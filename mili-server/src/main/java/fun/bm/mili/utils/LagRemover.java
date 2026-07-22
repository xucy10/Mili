package fun.bm.mili.utils;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lag removal system based on LaggRemover features.
 * Provides automatic lag detection and mitigation.
 */
public final class LagRemover {
    private static final long MEMORY_MBYTE = 1024 * 1024;
    private static LagRemover instance;

    private final org.bukkit.plugin.Plugin plugin;
    private volatile boolean running = true;

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

    private LagRemover(org.bukkit.plugin.Plugin plugin) {
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

    public static synchronized void init(org.bukkit.plugin.Plugin plugin) {
        if (instance != null) return;
        if (plugin == null) {
            throw new IllegalArgumentException("Mili plugin instance is required for LagRemover");
        }
        instance = new LagRemover(plugin);
        instance.start();
    }

    private org.bukkit.plugin.Plugin getPlugin() {
        return plugin;
    }

    public static LagRemover getInstance() {
        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.running = false;
            instance = null;
        }
    }

    private void start() {
        org.bukkit.plugin.Plugin activePlugin = getPlugin();

        // TPS tracking
        TPSTracker.init(activePlugin);

        // Auto chunk unload
        if (autoChunkUnload) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!running) {
                        cancel();
                        return;
                    }
                    unloadEmptyChunks();
                }
            }.runTaskTimer(activePlugin, 200L, 200L);
        }

        // Auto lag removal
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!running) {
                    cancel();
                    return;
                }
                if (smartLagAI) {
                    runSmartLagDetection();
                }
            }
        }.runTaskTimer(activePlugin, 1200L, 1200L);

        activePlugin.getLogger().info("[Mili] LagRemover initialized");
    }

    private void unloadEmptyChunks() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    chunk.unload(true);
                }
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
        new BukkitRunnable() {
            @Override
            public void run() {
                cooldownPlayers.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, localLagRemovalCooldown * 20L);

        // Remove entities
        int toRemove = (int) (nearby.size() * localThinPercent);
        int removed = 0;
        for (Entity entity : nearby) {
            if (removed >= toRemove) break;
            if (entity instanceof Item) {
                entity.remove();
                removed++;
            } else if (isHostile(entity.getType())) {
                entity.remove();
                removed++;
            }
        }

        player.sendMessage("§e[Mili] 检测到您周围实体过多，已清理 " + removed + " 个实体以缓解卡顿。");
    }

    private void clearGroundItems() {
        LongAdder count = new LongAdder();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item) {
                    entity.remove();
                    count.increment();
                }
            }
        }
        long c = count.sum();
        if (c > 0) {
            Bukkit.broadcastMessage("§e[Mili] 内存不足，已清理 " + c + " 个地面物品。");
        }
    }

    private void clearHostileEntities() {
        LongAdder count = new LongAdder();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isHostile(entity.getType())) {
                    entity.remove();
                    count.increment();
                }
            }
        }
        long c = count.sum();
        if (c > 0) {
            Bukkit.broadcastMessage("§e[Mili] TPS过低，已清理 " + c + " 个敌对实体。");
        }
    }

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