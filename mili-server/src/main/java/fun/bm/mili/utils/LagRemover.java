package fun.bm.mili.utils;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lag removal system based on LaggRemover features.
 * Provides automatic lag detection and mitigation.
 *
 * <p>Mili start - fix: Folia migration.
 * <p>The original implementation drove everything from Paper's legacy scheduler
 * ({@code BukkitRunnable} / {@code Bukkit.getScheduler()}), which Folia disables at runtime
 * (calling it throws {@code UnsupportedOperationException}). It also mutated region-owned
 * state — chunks and entities — from the global thread, which is a silent correctness
 * violation under Folia rather than an exception.
 * <p>The rules now are:
 * <ul>
 *   <li>periodic scanning runs on the {@code GlobalRegionScheduler};</li>
 *   <li>every chunk mutation is dispatched to the scheduler of the region owning the chunk;</li>
 *   <li>every entity mutation is dispatched to the entity's own scheduler.</li>
 * </ul>
 * Reading {@code World#getEntities()} / {@code World#getLoadedChunks()} off-region is tolerated
 * here (it is a plain structure scan with no Folia assertion) but the result is only used to
 * decide <i>where</i> to dispatch work, never to mutate anything directly.
 * Mili end
 */
public final class LagRemover {
    private static final long MEMORY_MBYTE = 1024 * 1024;
    // Mili start - fix: declare instance as volatile for safe publication across threads
    private static volatile LagRemover instance;
    // Mili end

    // Mili start - fix: Folia migration — a per-entity task per removal is heavier than the old
    // inline loop, so each pass is bounded to keep one detection round from flooding the region
    // schedulers. The follow-up pass picks up whatever was left over.
    private static final int MAX_ENTITY_REMOVALS_PER_PASS = 4096;
    // Mili end

    private final Plugin plugin;
    private volatile boolean running = true;

    // Mili start - fix: Folia migration — track the scheduled tasks so shutdown can release them
    private volatile ScheduledTask chunkUnloadTask;
    private volatile ScheduledTask lagAiTask;
    // Mili end

    // Configuration
    private final boolean autoChunkUnload;
    private final boolean thinMobs;
    private final int thinAt;
    private final double tpsThreshold;
    private final long ramThreshold;
    private final boolean smartLagAI;
    private final long smartAICooldown;
    private final int autoLagRemovalInterval;
    private final boolean doRelativeAction;
    private final int localLagRadius;
    private final int localLagTriggered;
    private final float localThinPercent;
    private final int localLagRemovalCooldown;

    private long lastSmartAIRun = 0;
    private final java.util.Set<java.util.UUID> cooldownPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private LagRemover(Plugin plugin) {
        this.plugin = plugin;

        this.autoChunkUnload = true;
        this.thinMobs = true;
        this.thinAt = 300;
        this.tpsThreshold = 16.0;
        this.ramThreshold = 100;
        this.smartLagAI = true;
        this.smartAICooldown = 3;
        this.autoLagRemovalInterval = 10;
        this.doRelativeAction = true;
        this.localLagRadius = 10;
        this.localLagTriggered = 100;
        this.localThinPercent = 0.8f;
        this.localLagRemovalCooldown = 60;
    }

    public static synchronized void init(Plugin plugin) {
        if (instance != null) return;
        if (plugin == null) {
            throw new IllegalArgumentException("Mili plugin instance is required for LagRemover");
        }
        instance = new LagRemover(plugin);
        instance.start();
    }

    private Plugin getPlugin() {
        return plugin;
    }

    public static LagRemover getInstance() {
        return instance;
    }

    public static synchronized void shutdown() {
        LagRemover active = instance;
        instance = null;
        if (active != null) {
            active.running = false;
            // Mili start - fix: Folia migration — release the repeating region tasks and the
            // TPS sampler; the old code leaked all of them on stop.
            ScheduledTask chunkTask = active.chunkUnloadTask;
            active.chunkUnloadTask = null;
            if (chunkTask != null) {
                chunkTask.cancel();
            }
            ScheduledTask aiTask = active.lagAiTask;
            active.lagAiTask = null;
            if (aiTask != null) {
                aiTask.cancel();
            }
            TPSTracker.shutdown();
            // Mili end
        }
    }

    private void start() {
        Plugin activePlugin = getPlugin();

        // TPS tracking
        TPSTracker.init(activePlugin);

        // Mili start - fix: Folia migration. Paper's legacy scheduler is unusable under Folia,
        // so both repeating timers move to the global region scheduler. The bodies only scan
        // and dispatch — they never mutate region-owned state on this thread.
        // Auto chunk unload
        if (autoChunkUnload) {
            chunkUnloadTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(activePlugin, task -> {
                if (!running) {
                    task.cancel();
                    return;
                }
                unloadEmptyChunks();
            }, 200L, 200L);
        }

        // Auto lag removal
        lagAiTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(activePlugin, task -> {
            if (!running) {
                task.cancel();
                return;
            }
            if (smartLagAI) {
                runSmartLagDetection();
            }
        }, 1200L, 1200L);
        // Mili end

        activePlugin.getLogger().info("[Mili] LagRemover initialized");
    }

    private void unloadEmptyChunks() {
        Plugin activePlugin = getPlugin();
        for (World world : Bukkit.getWorlds()) {
            if (!world.getPlayers().isEmpty()) continue;
            for (Chunk chunk : world.getLoadedChunks()) {
                if (!running) return;
                // Mili start - fix: Chunk#unload() asserts region ownership under Folia, so the
                // actual unload is dispatched to the region that owns the chunk.
                final World targetWorld = world;
                final Chunk targetChunk = chunk;
                try {
                    Bukkit.getRegionScheduler().run(activePlugin, targetWorld,
                            targetChunk.getX(), targetChunk.getZ(), task -> {
                                if (!running) return;
                                try {
                                    if (targetChunk.isLoaded()) {
                                        targetChunk.unload(true);
                                    }
                                } catch (Throwable ignored) {
                                    // region torn down or chunk already gone
                                }
                            });
                } catch (Throwable ignored) {
                    // world unloaded between the scan and the dispatch
                }
                // Mili end
            }
        }
    }

    private void runSmartLagDetection() {
        long now = System.currentTimeMillis();
        if (now - lastSmartAIRun < smartAICooldown * 60 * 1000) {
            return;
        }

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / MEMORY_MBYTE;
        long maxMemory = runtime.maxMemory() / MEMORY_MBYTE;
        long freeMemory = maxMemory - usedMemory;

        if (freeMemory < ramThreshold) {
            // Low RAM - clear items
            clearGroundItems();
            lastSmartAIRun = now;
        } else if (TPSTracker.getTPS() < tpsThreshold) {
            // Low TPS - clear entities
            clearHostileEntities();
            lastSmartAIRun = now;
        }
    }

    public void handlePlayerLagCommand(Player player) {
        if (!doRelativeAction) return;
        if (cooldownPlayers.contains(player.getUniqueId())) return;

        List<Entity> nearby = new ArrayList<>(player.getNearbyEntities(localLagRadius, localLagRadius, localLagRadius));
        if (nearby.size() < localLagTriggered) return;

        // Add cooldown
        cooldownPlayers.add(player.getUniqueId());
        // Mili start - fix: Folia migration. The cooldown set is concurrent and removing an entry
        // is a pure data-structure op, so it no longer needs a main-thread timer.
        try {
            Bukkit.getGlobalRegionScheduler().runDelayed(getPlugin(),
                    task -> cooldownPlayers.remove(player.getUniqueId()),
                    localLagRemovalCooldown * 20L);
        } catch (Throwable ignored) {
            // plugin went away mid-flight; the entry just stays until the next server restart
        }
        // Mili end

        // Remove entities
        int toRemove = (int) (nearby.size() * localThinPercent);
        int removed = 0;
        for (Entity entity : nearby) {
            if (removed >= toRemove) break;
            if (entity instanceof Item || isHostile(entity.getType())) {
                // Mili start - fix: dispatch the removal to the entity's owning region thread
                if (removeOnOwningThread(entity)) {
                    removed++;
                }
                // Mili end
            }
        }

        player.sendMessage("§e[Mili] 检测到您周围实体过多，已清理 " + removed + " 个实体以缓解卡顿。");
    }

    private void clearGroundItems() {
        LongAdder scheduled = new LongAdder();
        // Mili start - fix: the cap must stop the WHOLE pass, not just the inner per-world
        // loop.  A bare `break` only left the inner loop, so the outer world loop carried on
        // removing entities and MAX_ENTITY_REMOVALS_PER_PASS was never actually enforced.
        pass:
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (scheduled.sum() >= MAX_ENTITY_REMOVALS_PER_PASS) break pass;
                if (entity instanceof Item && removeOnOwningThread(entity)) {
                    scheduled.increment();
                }
            }
        }
        // Mili end
        long c = scheduled.sum();
        if (c > 0) {
            Bukkit.broadcastMessage("§e[Mili] 内存不足，已排队清理 " + c + " 个地面物品。");
        }
    }

    private void clearHostileEntities() {
        LongAdder scheduled = new LongAdder();
        // Mili start - fix: same as clearGroundItems — stop the whole pass on cap.
        pass:
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (scheduled.sum() >= MAX_ENTITY_REMOVALS_PER_PASS) break pass;
                if (isHostile(entity.getType()) && removeOnOwningThread(entity)) {
                    scheduled.increment();
                }
            }
        }
        // Mili end
        long c = scheduled.sum();
        if (c > 0) {
            Bukkit.broadcastMessage("§e[Mili] TPS过低，已排队清理 " + c + " 个敌对实体。");
        }
    }

    // Mili start - fix: Folia migration helper — schedule Entity#remove() on the entity's own
    // scheduler so the mutation happens on the region thread that owns the entity.
    // Returns false when the entity is already retired or has no scheduler.
    private boolean removeOnOwningThread(Entity entity) {
        if (entity == null || !entity.isValid()) return false;
        try {
            // Fully qualified: Mili has its own fun.bm.mili.scheduler.EntityScheduler, and
            // lenient imports would make this ambiguous.
            io.papermc.paper.threadedregions.scheduler.EntityScheduler scheduler = entity.getScheduler();
            if (scheduler == null) return false;
            return scheduler.run(getPlugin(), task -> entity.remove(), null) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
    // Mili end

    private boolean isHostile(EntityType type) {
        return switch (type) {
            case ZOMBIE, SKELETON, CREEPER, SPIDER, CAVE_SPIDER, ENDERMAN, WITCH,
                 SLIME, MAGMA_CUBE, BLAZE, GHAST, WITHER_SKELETON, ZOMBIE_VILLAGER,
                 HUSK, STRAY, DROWNED, PHANTOM, PILLAGER, VINDICATOR, EVOKER,
                 VEX, RAVAGER, HOGLIN, PIGLIN, PIGLIN_BRUTE, ZOGLIN -> true;
            default -> false;
        };
    }

    public boolean shouldThinMobs(Chunk chunk) {
        return thinMobs && chunk.getEntities().length > thinAt;
    }
}
