package fun.bm.mili.chunk;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.optimizations.ChunkSystemConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class MiliChunkSystem {

    private MiliChunkSystem() {}

    // Mili start - fix: use AtomicBoolean for thread-safe init/shutdown
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    // Mili end
    private static BukkitTask mainThreadTask;
    private static ScheduledExecutorService asyncExecutor;

    private static final ConcurrentHashMap<World, WorldChunkData> worldData = new ConcurrentHashMap<>();
    private static final AsyncChunkProcessor asyncProcessor = new AsyncChunkProcessor();

    private static final AtomicLong totalChunkLoads = new AtomicLong(0);
    private static final AtomicLong totalChunkUnloads = new AtomicLong(0);
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);
    private static final AtomicLong tickCounter = new AtomicLong(0);

    public static void init(org.bukkit.plugin.Plugin plugin) {
        // Mili start - fix: CAS-based init to prevent double initialization race
        if (!ChunkSystemConfig.enabled) return;
        if (!initialized.compareAndSet(false, true)) return;
        // Mili end

        if (plugin == null) {
            initialized.set(false);
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
                asyncProcessor::processQueue,
                0,
                50,
                TimeUnit.MILLISECONDS
        );

        LogUtils.getLogger().info(
                "[Mili] MiliChunkSystem v3.0 initialized with {} async threads",
                ChunkSystemConfig.asyncThreads
        );
    }

    public static void shutdown() {
        // Mili start - fix: CAS-based shutdown to prevent double shutdown race
        if (!initialized.compareAndSet(true, false)) return;
        // Mili end

        if (mainThreadTask != null) {
            mainThreadTask.cancel();
            mainThreadTask = null;
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
            asyncExecutor = null;
        }

        worldData.clear();
        asyncProcessor.clear();
        tickCounter.set(0);

        LogUtils.getLogger().info("[Mili] MiliChunkSystem shutdown complete");
    }

    private static void tick() {
        long startNanos = System.nanoTime();
        boolean cleanupCycle = tickCounter.incrementAndGet() % 600L == 0L;

        try {
            for (Map.Entry<World, WorldChunkData> entry : worldData.entrySet()) {
                World world = entry.getKey();
                WorldChunkData data = entry.getValue();

                ChunkHotnessUpdater.update(world, data);
                ChunkLifecycleManager.manage(world, data, totalChunkUnloads);
                ChunkViewDistanceOptimizer.optimize(world, data);
                if (cleanupCycle) {
                    data.cleanupStaleEntries();
                }
            }
        } catch (Throwable e) {
            // Mili start - fix: catch Throwable (not just Exception) to prevent main thread task cancellation on Error
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
        worldData.computeIfAbsent(world, w -> new WorldChunkData(w));
    }

    public static void unregisterWorld(World world) {
        worldData.remove(world);
    }

    public static void queueAsyncOperation(AsyncChunkProcessor.AsyncChunkOperation operation) {
        if (!asyncProcessor.enqueue(operation)) {
            operation.onRejected();
        }
    }

    public static ChunkHotness getChunkHotness(World world, int chunkX, int chunkZ) {
        WorldChunkData data = worldData.get(world);
        if (data == null) return null;
        return data.getHotness(chunkX, chunkZ);
    }

    public static CompletableFuture<Void> preloadArea(World world, int centerX, int centerZ, int radius) {
        return asyncProcessor.preloadArea(world, centerX, centerZ, radius);
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_chunk_loads", totalChunkLoads.get());
        stats.put("total_chunk_unloads", totalChunkUnloads.get());
        stats.put("total_async_ops", asyncProcessor.getTotalOps());
        stats.put("cache_hits", cacheHits.get());
        stats.put("cache_misses", cacheMisses.get());
        stats.put("async_queue_size", asyncProcessor.queueSize());
        stats.put("registered_worlds", worldData.size());

        long totalHotChunks = 0;
        long activeChunks = 0;
        for (WorldChunkData data : worldData.values()) {
            totalHotChunks += data.getTotalHotChunks();
            activeChunks += data.getActiveChunks();
        }
        stats.put("hot_chunks", totalHotChunks);
        stats.put("active_chunks", activeChunks);

        return stats;
    }
}