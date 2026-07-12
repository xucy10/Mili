package fun.bm.mili.chunk;

import fun.bm.mili.config.modules.optimizations.ChunkSystemConfig;
import org.mojang.logging.LogUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class MiliChunkSystem {

    private MiliChunkSystem() {}

    private static volatile boolean initialized = false;
    private static BukkitTask mainThreadTask;
    private static ScheduledExecutorService asyncExecutor;

    private static final ConcurrentHashMap<World, WorldChunkData> WORLD_DATA = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<AsyncChunkOperation> ASYNC_QUEUE = new ConcurrentLinkedQueue<>();

    private static final AtomicLong TOTAL_CHUNK_LOADS = new AtomicLong(0);
    private static final AtomicLong TOTAL_CHUNK_UNLOADS = new AtomicLong(0);
    private static final AtomicLong TOTAL_ASYNC_OPS = new AtomicLong(0);
    private static final AtomicLong CACHE_HITS = new AtomicLong(0);
    private static final AtomicLong CACHE_MISSES = new AtomicLong(0);

    public static void init() {
        if (!ChunkSystemConfig.enabled || initialized) return;

        asyncExecutor = Executors.newScheduledThreadPool(
                ChunkSystemConfig.asyncThreads,
                r -> {
                    Thread t = new Thread(r, "Mili-ChunkWorker");
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY + 1);
                    return t;
                }
        );

        for (World world : Bukkit.getWorlds()) {
            registerWorld(world);
        }

        mainThreadTask = Bukkit.getScheduler().runTaskTimer(
                Bukkit.getPluginManager().getPlugins()[0],
                MiliChunkSystem::tick,
                1L,
                1L
        );

        asyncExecutor.scheduleAtFixedRate(
                MiliChunkSystem::processAsyncQueue,
                0,
                50,
                TimeUnit.MILLISECONDS
        );

        initialized = true;
        org.mojang.logging.LogUtils.getLogger().info(
                "[Mili] MiliChunkSystem v3.0 initialized with {} async threads",
                ChunkSystemConfig.asyncThreads
        );
    }

    public static void shutdown() {
        if (!initialized) return;
        initialized = false;

        if (mainThreadTask != null) {
            mainThreadTask.cancel();
        }

        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
            }
        }

        WORLD_DATA.clear();
        ASYNC_QUEUE.clear();

        org.mojang.logging.LogUtils.getLogger().info("[Mili] MiliChunkSystem shutdown complete");
    }

    private static void tick() {
        long startNanos = System.nanoTime();

        try {
            for (Map.Entry<World, WorldChunkData> entry : WORLD_DATA.entrySet()) {
                World world = entry.getKey();
                WorldChunkData data = entry.getValue();

                updateChunkHotness(world, data);
                manageChunkLifecycle(world, data);
                optimizeViewDistance(world, data);
            }
        } catch (Exception e) {
            org.mojang.logging.LogUtils.getLogger().error("[Mili] Chunk system tick error", e);
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        if (elapsedNanos > 5_000_000L) {
            org.mojang.logging.LogUtils.getLogger().warn(
                    "[Mili] Chunk system tick took {}ms", elapsedNanos / 1_000_000L
            );
        }
    }

    private static void updateChunkHotness(World world, WorldChunkData data) {
        Collection<? extends Player> players = world.getPlayers();
        Set<Player> playerSet = players instanceof Set ? (Set<Player>) players : new HashSet<>(players);

        for (Chunk chunk : world.getLoadedChunks()) {
            int x = chunk.getX();
            int z = chunk.getZ();

            ChunkHotness hotness = data.getOrCreateHotness(x, z);

            boolean nearPlayer = false;
            double minDistSq = Double.MAX_VALUE;

            for (Player player : playerSet) {
                if (!player.isOnline() || player.getWorld() != world) continue;

                Location pLoc = player.getLocation();
                double dx = (pLoc.getBlockX() >> 4) - x;
                double dz = (pLoc.getBlockZ() >> 4) - z;
                double distSq = dx * dx + dz * dz;

                if (distSq < minDistSq) {
                    minDistSq = distSq;
                }

                if (distSq <= ChunkSystemConfig.hotChunkRadius * ChunkSystemConfig.hotChunkRadius) {
                    nearPlayer = true;
                    break;
                }
            }

            hotness.update(nearPlayer, minDistSq);
        }
    }

    private static void manageChunkLifecycle(World world, WorldChunkData data) {
        int loadedCount = world.getLoadedChunks().length;
        int maxLoaded = ChunkSystemConfig.maxLoadedChunks;

        if (loadedCount > maxLoaded) {
            List<CandidateChunk> candidates = new ArrayList<>();

            for (Chunk chunk : world.getLoadedChunks()) {
                ChunkHotness hotness = data.getHotness(chunk.getX(), chunk.getZ());
                if (hotness == null) continue;

                if (!isChunkKeepAlive(chunk)) {
                    candidates.add(new CandidateChunk(chunk, hotness));
                }
            }

            candidates.sort(Comparator.comparingDouble(c -> c.hotness.getScore()));

            int toUnload = Math.min(
                    candidates.size(),
                    loadedCount - (int)(maxLoaded * ChunkSystemConfig.unloadSafetyMargin)
            );

            for (int i = 0; i < toUnload; i++) {
                CandidateChunk candidate = candidates.get(i);
                unloadChunkSafely(candidate.chunk);
                TOTAL_CHUNK_UNLOADS.incrementAndGet();
            }
        }
    }

    private static void optimizeViewDistance(World world, WorldChunkData data) {
        if (!ChunkSystemConfig.dynamicViewDistance) return;

        Collection<? extends Player> players = world.getPlayers();
        if (players.isEmpty()) return;

        double avgLoad = 0;
        int sampleCount = 0;

        for (Chunk chunk : world.getLoadedChunks()) {
            ChunkHotness hotness = data.getHotness(chunk.getX(), chunk.getZ());
            if (hotness != null && hotness.isActive()) {
                avgLoad += hotness.getAccessCount();
                sampleCount++;
            }
        }

        if (sampleCount == 0) return;

        avgLoad /= sampleCount;

        int currentVD = world.getViewDistance();
        int targetVD = currentVD;

        if (avgLoad > ChunkSystemConfig.vdDecreaseThreshold && currentVD > ChunkSystemConfig.minViewDistance) {
            targetVD = currentVD - 1;
        } else if (avgLoad < ChunkSystemConfig.vdIncreaseThreshold && currentVD < ChunkSystemConfig.maxViewDistance) {
            targetVD = currentVD + 1;
        }

        if (targetVD != currentVD) {
            if (data.canAdjustViewDistance()) {
                world.setViewDistance(targetVD);
                data.recordViewDistanceAdjustment();
            }
        }
    }

    private static boolean isChunkKeepAlive(Chunk chunk) {
        if (chunk.getEntities().length > 0) return true;

        for (Player player : chunk.getWorld().getPlayers()) {
            Location eyeLoc = player.getEyeLocation();
            int pdx = (eyeLoc.getBlockX() >> 4) - chunk.getX();
            int pdz = (eyeLoc.getBlockZ() >> 4) - chunk.getZ();

            if (pdx * pdx + pdz * pdz <= 256) {
                return true;
            }
        }

        return false;
    }

    private static void unloadChunkSafely(Chunk chunk) {
        try {
            if (chunk.isForceLoaded() || chunk.isLoaded()) {
                chunk.unload(true);
            }
        } catch (Exception e) {
            org.mojang.logging.LogUtils.getLogger().debug(
                    "[Mili] Failed to unload chunk ({}, {})",
                    chunk.getX(), chunk.getZ()
            );
        }
    }

    private static void processAsyncQueue() {
        int processed = 0;
        long deadline = System.nanoTime() + ChunkSystemConfig.asyncTimeBudgetNs;

        while (processed < ChunkSystemConfig.maxAsyncOpsPerCycle && System.nanoTime() < deadline) {
            AsyncChunkOperation op = ASYNC_QUEUE.poll();
            if (op == null) break;

            try {
                op.execute();
                TOTAL_ASYNC_OPS.incrementAndGet();
                processed++;
            } catch (Exception e) {
                org.mojang.logging.LogUtils.getLogger().warn(
                        "[Mili] Async chunk operation failed", e
                );
            }
        }
    }

    public static void registerWorld(World world) {
        WORLD_DATA.computeIfAbsent(world, w -> new WorldChunkData(w));
    }

    public static void unregisterWorld(World world) {
        WORLD_DATA.remove(world);
    }

    public static void queueAsyncOperation(AsyncChunkOperation operation) {
        if (ASYNC_QUEUE.size() < ChunkSystemConfig.maxAsyncQueueSize) {
            ASYNC_QUEUE.add(operation);
        } else {
            operation.onRejected();
        }
    }

    public static ChunkHotness getChunkHotness(World world, int chunkX, int chunkZ) {
        WorldChunkData data = WORLD_DATA.get(world);
        if (data == null) return null;
        return data.getHotness(chunkX, chunkZ);
    }

    public static CompletableFuture<Void> preloadArea(World world, int centerX, int centerZ, int radius) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        queueAsyncOperation(new AsyncChunkOperation() {
            @Override
            public void execute() {
                try {
                    int chunksLoaded = 0;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            int cx = centerX + dx;
                            int cz = centerZ + dz;

                            Chunk chunk = world.getChunkAt(cx, cz);
                            if (chunk != null && chunk.isLoaded()) {
                                chunksLoaded++;
                            }
                        }
                    }

                    future.complete(null);
                    org.mojang.logging.LogUtils.getLogger().debug(
                            "[Mili] Preloaded {} chunks around ({}, {})",
                            chunksLoaded, centerX, centerZ
                    );
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onRejected() {
                future.completeExceptionally(new RuntimeException("Async queue full"));
            }
        });

        return future;
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_chunk_loads", TOTAL_CHUNK_LOADS.get());
        stats.put("total_chunk_unloads", TOTAL_CHUNK_UNLOADS.get());
        stats.put("total_async_ops", TOTAL_ASYNC_OPS.get());
        stats.put("cache_hits", CACHE_HITS.get());
        stats.put("cache_misses", CACHE_MISSES.get());
        stats.put("async_queue_size", ASYNC_QUEUE.size());
        stats.put("registered_worlds", WORLD_DATA.size());

        long totalHotChunks = 0;
        long activeChunks = 0;
        for (WorldChunkData data : WORLD_DATA.values()) {
            totalHotChunks += data.getTotalHotChunks();
            activeChunks += data.getActiveChunks();
        }
        stats.put("hot_chunks", totalHotChunks);
        stats.put("active_chunks", activeChunks);

        return stats;
    }

    public interface AsyncChunkOperation {
        void execute();
        default void onRejected() {}
    }

    private static class CandidateChunk {
        final Chunk chunk;
        final ChunkHotness hotness;

        CandidateChunk(Chunk chunk, ChunkHotness hotness) {
            this.chunk = chunk;
            this.hotness = hotness;
        }
    }
}