package fun.bm.mili.chunk;

import fun.bm.mili.config.modules.optimizations.ChunkSystemConfig;
import com.mojang.logging.LogUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class MiliChunkSystem {

    private MiliChunkSystem() {}

    private static volatile boolean initialized = false;
    private static BukkitTask mainThreadTask;
    private static ScheduledExecutorService asyncExecutor;

    private static final ConcurrentHashMap<World, WorldChunkData> WORLD_DATA = new ConcurrentHashMap<>();
    private static final AsyncChunkProcessor ASYNC_PROCESSOR = new AsyncChunkProcessor();

    private static final AtomicLong TOTAL_CHUNK_LOADS = new AtomicLong(0);
    private static final AtomicLong TOTAL_CHUNK_UNLOADS = new AtomicLong(0);
    private static final AtomicLong CACHE_HITS = new AtomicLong(0);
    private static final AtomicLong CACHE_MISSES = new AtomicLong(0);

    public static void init(org.bukkit.plugin.Plugin plugin) {
        if (!ChunkSystemConfig.enabled || initialized) return;

        if (plugin == null) {
            throw new IllegalArgumentException("Mili plugin instance is required for MiliChunkSystem");
        }

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
                plugin,
                MiliChunkSystem::tick,
                1L,
                1L
        );

        asyncExecutor.scheduleAtFixedRate(
                ASYNC_PROCESSOR::processQueue,
                0,
                50,
                TimeUnit.MILLISECONDS
        );

        initialized = true;
        LogUtils.getLogger().info(
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
                Thread.currentThread().interrupt();
            }
        }

        WORLD_DATA.clear();
        ASYNC_PROCESSOR.clear();

        LogUtils.getLogger().info("[Mili] MiliChunkSystem shutdown complete");
    }

    private static void tick() {
        long startNanos = System.nanoTime();

        try {
            for (Map.Entry<World, WorldChunkData> entry : WORLD_DATA.entrySet()) {
                World world = entry.getKey();
                WorldChunkData data = entry.getValue();

                ChunkHotnessUpdater.update(world, data);
                ChunkLifecycleManager.manage(world, data, TOTAL_CHUNK_UNLOADS);
                ChunkViewDistanceOptimizer.optimize(world, data);
            }
        } catch (Exception e) {
            LogUtils.getLogger().error("[Mili] Chunk system tick error", e);
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        if (elapsedNanos > 5_000_000L) {
            LogUtils.getLogger().warn(
                    "[Mili] Chunk system tick took {}ms", elapsedNanos / 1_000_000L
            );
        }
    }

    public static void registerWorld(World world) {
        WORLD_DATA.computeIfAbsent(world, w -> new WorldChunkData(w));
    }

    public static void unregisterWorld(World world) {
        WORLD_DATA.remove(world);
    }

    public static void queueAsyncOperation(AsyncChunkProcessor.AsyncChunkOperation operation) {
        if (!ASYNC_PROCESSOR.enqueue(operation)) {
            operation.onRejected();
        }
    }

    public static ChunkHotness getChunkHotness(World world, int chunkX, int chunkZ) {
        WorldChunkData data = WORLD_DATA.get(world);
        if (data == null) return null;
        return data.getHotness(chunkX, chunkZ);
    }

    public static CompletableFuture<Void> preloadArea(World world, int centerX, int centerZ, int radius) {
        return ASYNC_PROCESSOR.preloadArea(world, centerX, centerZ, radius);
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_chunk_loads", TOTAL_CHUNK_LOADS.get());
        stats.put("total_chunk_unloads", TOTAL_CHUNK_UNLOADS.get());
        stats.put("total_async_ops", ASYNC_PROCESSOR.getTotalOps());
        stats.put("cache_hits", CACHE_HITS.get());
        stats.put("cache_misses", CACHE_MISSES.get());
        stats.put("async_queue_size", ASYNC_PROCESSOR.queueSize());
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
}