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
import java.util.concurrent.atomic.AtomicLong;

import static net.minecraft.world.level.NaturalSpawner.getRoughBiome;

public final class EntitiesCounterUtil {

    private static final Map<ServerLevel, Cache<Integer, ReferenceList<Entity>>> globalLoadedEntities = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, Object2IntOpenHashMap<MobCategory>> mobsMap = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerLevel, Cache<Integer, PositionCountingAreaMap<ServerPlayer>>> mobsAreaMap = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, Integer> spawnableChunkCount = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, CompletableFuture<Void>> tasks = new ConcurrentHashMap<>();
    private static final Set<Integer> UniqueIds = ConcurrentHashMap.newKeySet();
    private static final Map<ServerLevel, AtomicLong> lastTickTime = new ConcurrentHashMap<>();
    private static final long MIN_TICK_INTERVAL_MS = 50L;

    private static final java.util.concurrent.atomic.AtomicInteger lastUsedIdAtomic = new java.util.concurrent.atomic.AtomicInteger(1);
    private static final int CLEANUP_INTERVAL = 200;

    public static int generateUniqueId() {
        synchronized (UniqueIds) {
            int id;
            do {
                id = lastUsedIdAtomic.incrementAndGet();
                if (id <= 0) {
                    lastUsedIdAtomic.compareAndSet(id, 1);
                }
            } while (id <= 0 || UniqueIds.contains(id));

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
        Cache<Integer, ReferenceList<Entity>> data0 = globalLoadedEntities.computeIfAbsent(level,
            k -> CacheBuilder.newBuilder().concurrencyLevel(16).weakValues().build());
        if (data0.asMap().containsKey(uniqueId)) return;
        data0.put(uniqueId, data);
    }

    public static void reportAreaMap(ServerLevel level, PositionCountingAreaMap<ServerPlayer> areaMap, int uniqueId) {
        Cache<Integer, PositionCountingAreaMap<ServerPlayer>> areaMap0 = mobsAreaMap.computeIfAbsent(level,
            k -> CacheBuilder.newBuilder().concurrencyLevel(16).weakValues().build());
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
        if (task != null && !task.isDone()) return false;

        AtomicLong last = lastTickTime.get(level);
        if (last != null && System.currentTimeMillis() - last.get() < MIN_TICK_INTERVAL_MS) return false;
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
                    for (Entity entity : data) {
                        if (entity == null || entity.isRemoved() || !entity.isAlive()) continue;
                        MobCategory category = entity.getType().getCategory();
                        if (category != MobCategory.MISC) {
                            if (!entity.level().paperConfig().entities.spawning.countAllMobsForSpawning &&
                                    !(entity.spawnReason == CreatureSpawnEvent.SpawnReason.NATURAL ||
                                            entity.spawnReason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN)) {
                                continue;
                            }
                            map.addTo(category, 1);
                        }
                    }
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
        task.run();
        lastTickTime.computeIfAbsent(level, k -> new AtomicLong(0L)).set(System.currentTimeMillis());
    }

    public static NaturalSpawner.SpawnState runRemainingTasks(
            ServerLevel level, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter,
            LocalMobCapCalculator calculator, final boolean countMobs
    ) {
        Object2IntOpenHashMap<MobCategory> map = getMobsMap(level);
        if (map == null) return null;

        PotentialCalculator potentialCalculator = new PotentialCalculator();
        for (Entity entity : entities) {
            if (entity == null || entity.isRemoved() || !entity.isAlive()) continue;
            if (!entity.level().paperConfig().entities.spawning.countAllMobsForSpawning &&
                    !(entity.spawnReason == CreatureSpawnEvent.SpawnReason.NATURAL ||
                            entity.spawnReason == CreatureSpawnEvent.SpawnReason.CHUNK_GEN)) {
                continue;
            }
            BlockPos blockPos = entity.blockPosition();
            chunkGetter.query(ChunkPos.asLong(blockPos), chunk -> {
                MobSpawnSettings.MobSpawnCost mobSpawnCost = getRoughBiome(blockPos, chunk).getMobSettings().getMobSpawnCost(entity.getType());
                if (mobSpawnCost != null) {
                    potentialCalculator.addCharge(entity.blockPosition(), mobSpawnCost.charge());
                }
                if (calculator != null && entity instanceof Mob) {
                    calculator.addMob(chunk.getPos(), entity.getType().getCategory());
                }
                if (countMobs) {
                    chunk.level.getChunkSource().chunkMap.updatePlayerMobTypeMap(entity);
                }
            });
        }
        return new NaturalSpawner.SpawnState(getTotalChunkCount(level), map, potentialCalculator, calculator);
    }

    private EntitiesCounterUtil() {}
}
