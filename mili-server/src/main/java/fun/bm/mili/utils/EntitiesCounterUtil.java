package fun.bm.mili.utils;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.PositionCountingAreaMap;
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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static net.minecraft.world.level.NaturalSpawner.getRoughBiome;

public class EntitiesCounterUtil {
    private static final ConcurrentHashMap<ServerLevel, ConcurrentHashMap<Integer, Object2IntOpenHashMap<MobCategory>>> regionMobCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerLevel, ConcurrentHashMap<Integer, AtomicInteger>> regionChunkCounts = new ConcurrentHashMap<>();
    private static final Set<Integer> uniqueIds = ConcurrentHashMap.newKeySet();

    private static final AtomicInteger lastUsedId = new AtomicInteger(0);
    private static final Object idLock = new Object();
    private static final int CLEANUP_INTERVAL = 200;

    public static int generateUniqueId() {
        synchronized (idLock) {
            if (lastUsedId.get() % CLEANUP_INTERVAL == 0) runCleanUp();
            int id = lastUsedId.getAndIncrement();
            while (uniqueIds.contains(id)) {
                id = lastUsedId.getAndIncrement();
            }
            uniqueIds.add(id);
            return id;
        }
    }

    public static void onWorldDataUnload(ServerLevel level, int uniqueId) {
        uniqueIds.remove(uniqueId);
        ConcurrentHashMap<Integer, Object2IntOpenHashMap<MobCategory>> mobCounts = regionMobCounts.get(level);
        if (mobCounts != null) {
            mobCounts.remove(uniqueId);
        }
        ConcurrentHashMap<Integer, AtomicInteger> chunkCounts = regionChunkCounts.get(level);
        if (chunkCounts != null) {
            chunkCounts.remove(uniqueId);
        }
    }

    private static void runCleanUp() {
        Set<Integer> logged = new HashSet<>();
        for (ConcurrentHashMap<Integer, Object2IntOpenHashMap<MobCategory>> counts : regionMobCounts.values()) {
            logged.addAll(counts.keySet());
        }
        uniqueIds.removeIf(num -> !logged.contains(num));
    }

    // 每个区域只统计自己的实体，写入自己唯一的 key；不会访问其他区域的数据
    public static void tick(ServerLevel level, int uniqueId, ReferenceList<Entity> entities, PositionCountingAreaMap<ServerPlayer> areaMap) {
        Object2IntOpenHashMap<MobCategory> map = new Object2IntOpenHashMap<>();
        for (Entity entity : entities) {
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
        regionMobCounts.computeIfAbsent(level, k -> new ConcurrentHashMap<>()).put(uniqueId, map);

        int chunkCount = areaMap != null ? areaMap.getTotalPositions() : 0;
        regionChunkCounts.computeIfAbsent(level, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(uniqueId, k -> new AtomicInteger(chunkCount)).set(chunkCount);
    }

    // 读取时汇总所有区域的计数，O(区域数) 而非 O(实体数)
    public static NaturalSpawner.SpawnState runRemainingTasks(
            ServerLevel level, Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter, LocalMobCapCalculator calculator, final boolean countMobs
    ) {
        ConcurrentHashMap<Integer, Object2IntOpenHashMap<MobCategory>> counts = regionMobCounts.get(level);
        if (counts == null) return null; // skip if no data

        Object2IntOpenHashMap<MobCategory> globalMap = new Object2IntOpenHashMap<>();
        for (Object2IntOpenHashMap<MobCategory> map : counts.values()) {
            if (map == null) continue;
            for (Object2IntOpenHashMap.Entry<MobCategory> entry : map.object2IntEntrySet()) {
                globalMap.addTo(entry.getKey(), entry.getIntValue());
            }
        }

        int totalChunks = 0;
        ConcurrentHashMap<Integer, AtomicInteger> chunkCounts = regionChunkCounts.get(level);
        if (chunkCounts != null) {
            for (AtomicInteger c : chunkCounts.values()) {
                if (c != null) totalChunks += c.get();
            }
        }

        // Mili start - Copy from net/minecraft/world/level/NaturalSpawner
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
        return new NaturalSpawner.SpawnState(totalChunks, globalMap, potentialCalculator, calculator);
        // Mili end - Copy from net/minecraft/world/level/NaturalSpawner
    }
}