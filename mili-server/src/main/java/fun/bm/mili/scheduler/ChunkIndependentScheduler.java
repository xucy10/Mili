package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.misc.ChunkIndependentConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public final class ChunkIndependentScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ServerLevel, ChunkIndependentScheduler> INSTANCES = new ConcurrentHashMap<>();

    private final ServerLevel level;
    private final CrossChunkBus crossChunkBus;
    private final int workerCount;
    private final ExecutorService workerPool;

    // Active workers indexed by ChunkPos.asLong
    private final Long2ObjectOpenHashMap<ChunkWorker> activeWorkers = new Long2ObjectOpenHashMap<>();
    private final Object workerLock = new Object();

    // Thread -> Worker mapping for TickThread.isTickThreadFor compatibility
    private final Map<Thread, ChunkWorker> workerThreadMap = new ConcurrentHashMap<>();

    // Scheduler state
    private final AtomicReference<Thread> schedulerThread = new AtomicReference<>(null);
    private volatile boolean running;

    // Config snapshot
    private volatile long timeoutMs;
    private volatile boolean mixedMode;
    private volatile boolean strictMode;

    public ChunkIndependentScheduler(ServerLevel level) {
        this.level = level;
        this.crossChunkBus = new CrossChunkBus(level);

        ChunkIndependentConfig cfg = ChunkIndependentConfig.getInstance();
        this.workerCount = cfg.workerThreads > 0 ? cfg.workerThreads : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.timeoutMs = cfg.timeoutMs;
        this.mixedMode = cfg.mixedMode;
        this.strictMode = cfg.strictMode;

        this.workerPool = Executors.newFixedThreadPool(workerCount, task -> {
            Thread t = new Thread(task, "mili-chunk-worker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });

        crossChunkBus.setTimeoutNanos(timeoutMs * 1_000_000L);
    }

    // ======================== Lifecycle ========================

    public void start() {
        if (running) return;
        running = true;
        crossChunkBus.startCoordinator();

        Thread st = new Thread(this::schedulerLoop, "mili-chunk-scheduler");
        st.setDaemon(true);
        st.start();
        schedulerThread.set(st);

        INSTANCES.put(level, this);
        LOGGER.info("ChunkIndependentScheduler started: {} workers on {}", workerCount, level.dimension().location());
    }

    public void stop() {
        if (!running) return;
        running = false;
        crossChunkBus.stopCoordinator();

        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        synchronized (workerLock) {
            activeWorkers.values().forEach(ChunkWorker::release);
            activeWorkers.clear();
            workerThreadMap.clear();
        }
        crossChunkBus.clear();
        schedulerThread.set(null);
        INSTANCES.remove(level);
        LOGGER.info("ChunkIndependentScheduler stopped");
    }

    // ======================== Scheduler Loop ========================

    private void schedulerLoop() {
        while (running) {
            try {
                tickScheduler();
                LockSupport.parkNanos("mili-scheduler", 1_000_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("Scheduler loop error", e);
            }
        }
    }

    /** Collect workers that are ready to tick this cycle */
    private void tickScheduler() {
        List<ChunkWorker> ready = new ArrayList<>();
        synchronized (workerLock) {
            for (ChunkWorker worker : activeWorkers.values()) {
                if (!worker.isReleased() && worker.getChunk() != null && worker.getChunk().isLoaded()) {
                    ready.add(worker);
                }
            }
        }
        if (ready.isEmpty()) return;

        // Classify: high-interaction chunks tick sequentially (region fallback)
        List<ChunkWorker> independent = new ArrayList<>();
        List<ChunkWorker> sequential = new ArrayList<>();

        for (ChunkWorker w : ready) {
            if (mixedMode && !strictMode && isHighInteraction(w)) {
                sequential.add(w);
            } else {
                independent.add(w);
            }
        }

        // Tick independent workers in parallel via thread pool
        if (!independent.isEmpty()) {
            CountDownLatch latch = new CountDownLatch(independent.size());
            for (ChunkWorker w : independent) {
                workerPool.submit(() -> {
                    try {
                        w.tick();
                    } catch (Throwable t) {
                        LOGGER.error("Worker tick failed: {}", t.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            try {
                if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                    LOGGER.warn("{} chunk workers timed out, falling back to region mode",
                        independent.stream().filter(w -> !w.waitForTickCompletion(0)).count());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Tick sequential workers on scheduler thread (simulates Folia region)
        for (ChunkWorker w : sequential) {
            w.tick();
        }

        // Reset for next tick
        for (ChunkWorker w : ready) {
            w.resetForNextTick();
        }
    }

    private boolean isHighInteraction(ChunkWorker worker) {
        ChunkBorderCache cache = worker.getBorderCache();
        if (cache == null) return true;
        // Force capture on first tick
        if (!cache.isReady()) {
            LevelChunk chunk = worker.getChunk();
            if (chunk != null) cache.captureBorderState(chunk);
        }
        return cache.isHighInteraction();
    }

    // ======================== Worker Registration ========================

    public void registerChunk(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        long key = pos.toLong();
        synchronized (workerLock) {
            if (!activeWorkers.containsKey(key)) {
                ChunkWorker worker = new ChunkWorker(level, pos.x, pos.z, this);
                worker.assignChunk(chunk);
                activeWorkers.put(key, worker);
            } else {
                ChunkWorker existing = activeWorkers.get(key);
                if (existing.getChunk() != chunk) {
                    existing.assignChunk(chunk);
                }
            }
        }
    }

    public void unregisterChunk(ChunkPos pos) {
        long key = pos.toLong();
        synchronized (workerLock) {
            ChunkWorker w = activeWorkers.remove(key);
            if (w != null) {
                workerThreadMap.values().remove(w);
                w.release();
            }
        }
    }

    /** Register worker thread for TickThread compatibility */
    public void registerWorkerThread(Thread thread, ChunkWorker worker) {
        workerThreadMap.put(thread, worker);
    }

    public void unregisterWorkerThread(Thread thread) {
        workerThreadMap.remove(thread);
    }

    /** Find worker for current thread (used by TickThread extension) */
    public ChunkWorker getWorkerForThread(Thread thread) {
        return workerThreadMap.get(thread);
    }

    // ======================== Lookup ========================

    public ChunkWorker getWorker(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        synchronized (workerLock) {
            return activeWorkers.get(key);
        }
    }

    public ChunkWorker getWorker(ChunkPos pos) {
        return getWorker(pos.x, pos.z);
    }

    public CrossChunkBus getCrossChunkBus() {
        return crossChunkBus;
    }

    public int getActiveWorkerCount() {
        synchronized (workerLock) {
            return activeWorkers.size();
        }
    }

    // ======================== Static Helpers ========================

    public static ChunkIndependentScheduler getInstance(ServerLevel level) {
        return INSTANCES.get(level);
    }

    public static void shutdownAll() {
        for (ChunkIndependentScheduler cis : INSTANCES.values()) {
            cis.stop();
        }
        INSTANCES.clear();
    }

    public boolean isRunning() { return running; }
}
