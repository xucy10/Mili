package fun.bm.mili.utils;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.PositionCountingAreaMap;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.experiment.GlobalEntitiesCounter;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static net.minecraft.world.level.NaturalSpawner.getRoughBiome;

public class EntitiesCounterUtil {
    private static final Map<ServerLevel, Cache<Integer, ReferenceList<Entity>>> globalLoadedEntities = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, Object2IntOpenHashMap<MobCategory>> mobsMap = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerLevel, Cache<Integer, PositionCountingAreaMap<ServerPlayer>>> mobsAreaMap = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, Integer> spawnableChunkCount = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, CompletableFuture<Void>> tasks = new ConcurrentHashMap<>();
    private static final Set<Integer> UniqueIds = ConcurrentHashMap.newKeySet();

    // mili - perf: track last successful tick time per level to enable throttling
    private static final Map<ServerLevel, AtomicLong> lastTickTime = new ConcurrentHashMap<>();
    // mili - perf: minimum interval between async ticks per level (ms)
    private static final long MIN_TICK_INTERVAL_MS = 50L;
    // mili - perf: cache version based on global loaded entities' reference identity hash
    private static final Map<ServerLevel, Integer> cacheVersion = new ConcurrentHashMap<>();

    // mili - 已迁移到 lastUsedIdAtomic / migrated to lastUsedIdAtomic

    // mili - perf: dedicated single-thread executor to avoid competing with ForkJoinPool common pool
    private static final ExecutorService COUNTER_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mili-EntitiesCounter");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    // mili - 使用 AtomicInteger 实现溢出安全的 ID 生成器
    // mili - Overflow-safe ID generator using AtomicInteger
    private static final java.util.concurrent.atomic.AtomicInteger lastUsedIdAtomic = new java.util.concurrent.atomic.AtomicInteger(1);
    private static final int CLEANUP_INTERVAL = 200; // 每 200 次 ID 生成触发一次清理 / cleanup every 200 IDs

    /**
     * 生成唯一 ID，溢出安全 / Generate unique ID, overflow-safe.
     * 使用 CAS 循环确保唯一性，溢出后回绕到 1 / Uses CAS loop for uniqueness, wraps to 1 on overflow.
     */
    public static int generateUniqueId() {
        synchronized (UniqueIds) {
            int id;
            do {
                id = lastUsedIdAtomic.incrementAndGet();
                // 溢出到负数或零时回绕 / Wrap around on overflow to negative or zero
                if (id <= 0) {
                    lastUsedIdAtomic.compareAndSet(id, 1);
                }
            } while (id <= 0 || UniqueIds.contains(id));

            // 周期性清理不再使用的 ID / Periodic cleanup of unused IDs
            if (id % CLEANUP_INTERVAL == 0) runCleanUp();

            UniqueIds.add(id);
            return id;
        }
    }

    public static void onWorldDataUnload(ServerLevel level, int uniqueId) {
        UniqueIds.remove(uniqueId);
        Cache<Integer, ReferenceList<Entity>> entitiesCache = globalLoadedEntities.get(level);
        if (entitiesCache != null) {
            entitiesCache.invalidate(uniqueId);
        }

        Cache<Integer, PositionCountingAreaMap<ServerPlayer>> areaCache = mobsAreaMap.get(level);
        if (areaCache != null) {
            areaCache.invalidate(uniqueId);
        }
    }

    private static void runCleanUp() {
        Set<Integer> logged = new HashSet<>();
        for (Cache<Integer, ReferenceList<Entity>> collection : globalLoadedEntities.values()) {
            logged.addAll(collection.asMap().keySet());
        }

        for (int num : UniqueIds) {
            if (logged.contains(num)) continue;
            UniqueIds.remove(num);
        }
    }

    public static void addDataToLoaded(ServerLevel level, ReferenceList<Entity> data, int uniqueId) {
        Cache<Integer, ReferenceList<Entity>> data0 = globalLoadedEntities.computeIfAbsent(level, k -> CacheBuilder.newBuilder().concurrencyLevel(16).weakValues().build());
        if (data0.asMap().containsKey(uniqueId)) return;
        data0.put(uniqueId, data);
    }

    public static void reportAreaMap(ServerLevel level, PositionCountingAreaMap<ServerPlayer> areaMap, int uniqueId) {
        Cache<Integer, PositionCountingAreaMap<ServerPlayer>> areaMap0 = mobsAreaMap.computeIfAbsent(level, k -> CacheBuilder.newBuilder().concurrencyLevel(16).weakValues().build());
        if (areaMap0.asMap().containsKey(uniqueId)) return;
        areaMap0.put(uniqueId, areaMap);
    }

    public static int getTotalChunkCount(ServerLevel level) {
        return spawnableChunkCount.getOrDefault(level, 0);
    }

    public static @Nullable Object2IntOpenHashMap<MobCategory> getMobsMap(ServerLevel level) {
        return mobsMap.get(level);
    }

    public static boolean canRunNewTask(ServerLevel level) {
        CompletableFuture<Void> task = tasks.get(level);
        if (task != null && !task.isDone()) {
            return false;
        }
        // mili - perf: throttle to avoid overwhelming scheduler when many regions tick
        AtomicLong last = lastTickTime.get(level);
        if (last != null) {
            long elapsed = System.currentTimeMillis() - last.get();
            if (elapsed < MIN_TICK_INTERVAL_MS) {
                return false;
            }
        }
        return true;
    }

    public static void tick(ServerLevel level) {
        Runnable task = () -> {
            try {
                Cache<Integer, ReferenceList<Entity>> data0 = globalLoadedEntities.get(level);
                if (data0 == null) return;

                Object2IntOpenHashMap<MobCategory> map = new Object2IntOpenHashMap<>();
                Collection<ReferenceList<Entity>> snapshot = data0.asMap().values();

                for (ReferenceList<Entity> data : snapshot) {
                    if (data == null) continue;
                    for (Entity entity : GlobalEntitiesCounter.async ? data.copy() : data) {
                        if (entity == null || entity.isRemoved() || !entity.isAlive()) continue;
                        // mili start - Copy from net/minecraft/world/level/NaturalSpawner
                        MobCategory category = entity.getType().getCategory();
                        if (category != MobCategory.MISC) {
                            // Paper start - Only count natural spawns
                            if (!entity.level().paperConfig().entities.spawning.countAllMobsForSpawning &&
                                    !(entity.spawnReason == CreatureSpawnEvent.SpawnReason.NATURAL ||
                                            entity.spawnReason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN)) {
                                continue;
                            }
                            // Paper end - Only count natural spawns
                            map.addTo(category, 1);
                        }
                    }
                    // mili end - Copy from net/minecraft/world/level/NaturalSpawner
                }
                mobsMap.put(level, map);

                Cache<Integer, PositionCountingAreaMap<ServerPlayer>> collection = mobsAreaMap.get(level);
                if (collection != null) {
                    int count = 0;
                    for (PositionCountingAreaMap<ServerPlayer> areaMap : collection.asMap().values()) {
                        if (areaMap != null) {
                            count += areaMap.getTotalPositions();
                        }
                    }
                    spawnableChunkCount.put(level, count);
                }
            } catch (Exception e) {
                LogUtils.getClassLogger().error("Failed to run task", e);
            }
        };
        if (GlobalEntitiesCounter.async) {
            tasks.put(level, CompletableFuture.runAsync(task, COUNTER_EXECUTOR).exceptionally(ex -> {
                LogUtils.getClassLogger().error("Failed to run task", ex);
                return null;
            }));
        } else {
            task.run();
        }
        // mili - perf: update last tick timestamp for throttling
        lastTickTime.computeIfAbsent(level, k -> new AtomicLong(0L)).set(System.currentTimeMillis());
    }

    public static NaturalSpawner.SpawnState runRemainingTasks(
            ServerLevel level, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter, LocalMobCapCalculator calculator, final boolean countMobs
    ) {
        Object2IntOpenHashMap<MobCategory> map = getMobsMap(level);
        if (map == null) return null; // skip if no data
        // mili start - Copy from net/minecraft/world/level/NaturalSpawner
        PotentialCalculator potentialCalculator = new PotentialCalculator();
        for (Entity entity : entities) {
            if (entity == null || entity.isRemoved() || !entity.isAlive()) continue;
            // Paper start - Only count natural spawns
            if (!entity.level().paperConfig().entities.spawning.countAllMobsForSpawning &&
                    !(entity.spawnReason == CreatureSpawnEvent.SpawnReason.NATURAL ||
                            entity.spawnReason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN)) {
                continue;
            }
            // Paper end - Only count natural spawns
            BlockPos blockPos = entity.blockPosition();
            chunkGetter.query(ChunkPos.asLong(blockPos), chunk -> {
                MobSpawnSettings.MobSpawnCost mobSpawnCost = getRoughBiome(blockPos, chunk).getMobSettings().getMobSpawnCost(entity.getType());
                if (mobSpawnCost != null) {
                    potentialCalculator.addCharge(entity.blockPosition(), mobSpawnCost.charge());
                }

                if (calculator != null && entity instanceof Mob) { // Paper - Optional per player mob spawns
                    calculator.addMob(chunk.getPos(), entity.getType().getCategory());
                }

                // Paper start - Optional per player mob spawns
                if (countMobs) {
                    chunk.level.getChunkSource().chunkMap.updatePlayerMobTypeMap(entity);
                }
                // Paper end - Optional per player mob spawns
            });
        }
        return new NaturalSpawner.SpawnState(getTotalChunkCount(level), map, potentialCalculator, calculator);
        // mili end - Copy from net/minecraft/world/level/NaturalSpawner
    }
}
