package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.misc.UnifiedSchedulerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * 区块独立调度器 / Chunk independent scheduler.
 *
 * <p>为每个区块分配独立的工作线程，实现区块 tick 的并行化 /
 * Assigns dedicated worker threads per chunk for parallelized chunk ticking.
 *
 * <p>性能优化 / Performance optimizations:
 * <ul>
 *   <li>使用 {@link ConcurrentHashMap} 替代 HashMap+synchronized 减少锁竞争 /
 *       Uses ConcurrentHashMap instead of HashMap+synchronized to reduce lock contention</li>
 *   <li>预分配 ready 列表容量避免扩容 / Pre-allocates ready list capacity to avoid resizing</li>
 *   <li>自适应 park 时间减少 CPU 空转 / Adaptive park time to reduce CPU spinning</li>
 * </ul>
 */
public final class ChunkIndependentScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ServerLevel, ChunkIndependentScheduler> INSTANCES = new ConcurrentHashMap<>();

    private final ServerLevel level;
    private final CrossChunkBus crossChunkBus;
    private final int workerCount;
    private final ExecutorService workerPool;

    // ConcurrentHashMap 替代 HashMap+synchronized / ConcurrentHashMap instead of HashMap+synchronized
    private final ConcurrentHashMap<Long, ChunkWorker> activeWorkers = new ConcurrentHashMap<>();

    private volatile boolean running;

    private final long timeoutMs;
    private final boolean mixedMode;
    private final boolean strictMode;

    // 自适应 park 时间 / Adaptive park times
    private static final long ACTIVE_PARK_NANOS = 1_000_000L;    // 1ms when busy
    private static final long IDLE_PARK_NANOS = 50_000_000L;     // 50ms when idle

    public ChunkIndependentScheduler(ServerLevel level) {
        this.level = level;
        this.crossChunkBus = new CrossChunkBus(level);
        this.workerCount = UnifiedSchedulerConfig.chunkWorkerThreads > 0
                ? UnifiedSchedulerConfig.chunkWorkerThreads
                : Math.min(Math.max(1, Runtime.getRuntime().availableProcessors() - 1), 4);
        this.timeoutMs = UnifiedSchedulerConfig.chunkTimeoutMs;
        this.mixedMode = UnifiedSchedulerConfig.mixedMode;
        this.strictMode = UnifiedSchedulerConfig.crossChunkStrictMode;

        this.workerPool = Executors.newFixedThreadPool(workerCount, task -> {
            Thread t = new Thread(task, "mili-chunk-worker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });

        crossChunkBus.setTimeoutNanos(timeoutMs * 1_000_000L);
    }

    public void start() {
        if (running) return;
        running = true;
        crossChunkBus.startCoordinator();

        Thread st = new Thread(this::schedulerLoop, "mili-chunk-scheduler");
        st.setDaemon(true);
        st.start();

        INSTANCES.put(level, this);
        LOGGER.info("ChunkIndependentScheduler started: {} workers on dim {}", workerCount, level.dimension().identifier());
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

        // ConcurrentHashMap 不需要 synchronized / ConcurrentHashMap doesn't need synchronized
        activeWorkers.values().forEach(ChunkWorker::release);
        activeWorkers.clear();
        crossChunkBus.clear();
        INSTANCES.remove(level);
        LOGGER.info("ChunkIndependentScheduler stopped");
    }

    private void schedulerLoop() {
        while (running) {
            try {
                boolean hadWork = tickScheduler();
                // 自适应 park: 有工作时短 park，空闲时长 park / Adaptive park: short when busy, long when idle
                LockSupport.parkNanos("mili-scheduler", hadWork ? ACTIVE_PARK_NANOS : IDLE_PARK_NANOS);
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                LOGGER.error("Scheduler loop error", e);
            }
        }
    }

    /**
     * 调度一轮区块 worker 任务 / Schedule one round of chunk worker tasks.
     *
     * <p>优化: 预分配列表容量避免扩容，使用 ConcurrentHashMap 无锁遍历 /
     * Optimized: pre-allocates list capacity, uses ConcurrentHashMap lock-free iteration.
     *
     * @return true 如果有工作被调度 / true if work was scheduled
     */
    private boolean tickScheduler() {
        int size = activeWorkers.size();
        if (size == 0) return false;

        // 预分配容量避免扩容 / Pre-allocate capacity to avoid resizing
        List<ChunkWorker> ready = new ArrayList<>(size);
        for (ChunkWorker worker : activeWorkers.values()) {
            if (!worker.isReleased() && worker.getChunk() != null) {
                ready.add(worker);
            }
        }
        if (ready.isEmpty()) return false;

        CountDownLatch latch = new CountDownLatch(ready.size());
        for (ChunkWorker w : ready) {
            workerPool.submit(() -> {
                try {
                    w.captureBorder();
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                long timedOut = ready.stream().filter(w -> !w.waitForCapture(0)).count();
                if (timedOut > 0) {
                    LOGGER.warn("{} workers timed out during border capture", timedOut);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int highCount = 0;
        for (ChunkWorker w : ready) {
            if (w.isHighInteraction()) highCount++;
            w.resetForNextTick();
        }
        if (mixedMode && !strictMode && highCount > 0) {
            LOGGER.debug("CIS: {} high-interaction chunks", highCount);
        }
        return true;
    }

    public void registerChunk(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        long key = pos.toLong();
        // ConcurrentHashMap.compute 替代 synchronized / ConcurrentHashMap.compute replaces synchronized
        activeWorkers.compute(key, (k, existing) -> {
            if (existing == null) {
                ChunkWorker worker = new ChunkWorker(level, pos.x, pos.z, this);
                worker.assignChunk(chunk);
                return worker;
            }
            if (existing.getChunk() != chunk) {
                existing.assignChunk(chunk);
            }
            return existing;
        });
    }

    public void unregisterChunk(ChunkPos pos) {
        long key = pos.toLong();
        ChunkWorker w = activeWorkers.remove(key);
        if (w != null) {
            w.release();
        }
    }

    public ChunkWorker getWorker(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        return activeWorkers.get(key);
    }

    public ChunkWorker getWorker(ChunkPos pos) {
        return getWorker(pos.x, pos.z);
    }

    public CrossChunkBus getCrossChunkBus() {
        return crossChunkBus;
    }

    public int getActiveWorkerCount() {
        return activeWorkers.size();
    }

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

    public String getStatsSummary() {
        return String.format("ChunkIndependentScheduler[workers=%d, active=%d, running=%s]",
                workerCount, activeWorkers.size(), running);
    }
}
package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.misc.UnifiedSchedulerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

public final class ChunkIndependentScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ServerLevel, ChunkIndependentScheduler> INSTANCES = new ConcurrentHashMap<>();

    private final ServerLevel level;
    private final CrossChunkBus crossChunkBus;
    private final int workerCount;
    private final ExecutorService workerPool;

    private final Map<Long, ChunkWorker> activeWorkers = new HashMap<>();
    private final Object workerLock = new Object();

    private volatile boolean running;

    private final long timeoutMs;
    private final boolean mixedMode;
    private final boolean strictMode;

    public ChunkIndependentScheduler(ServerLevel level) {
        this.level = level;
        this.crossChunkBus = new CrossChunkBus(level);
        this.workerCount = UnifiedSchedulerConfig.chunkWorkerThreads > 0
                ? UnifiedSchedulerConfig.chunkWorkerThreads
                : Math.min(Math.max(1, Runtime.getRuntime().availableProcessors() - 1), 4);
        this.timeoutMs = UnifiedSchedulerConfig.chunkTimeoutMs;
        this.mixedMode = UnifiedSchedulerConfig.mixedMode;
        this.strictMode = UnifiedSchedulerConfig.crossChunkStrictMode;

        this.workerPool = Executors.newFixedThreadPool(workerCount, task -> {
            Thread t = new Thread(task, "mili-chunk-worker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });

        crossChunkBus.setTimeoutNanos(timeoutMs * 1_000_000L);
    }

    public void start() {
        if (running) return;
        running = true;
        crossChunkBus.startCoordinator();

        Thread st = new Thread(this::schedulerLoop, "mili-chunk-scheduler");
        st.setDaemon(true);
        st.start();

        INSTANCES.put(level, this);
        LOGGER.info("ChunkIndependentScheduler started: {} workers on dim {}", workerCount, level.dimension().toString());
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
        }
        crossChunkBus.clear();
        INSTANCES.remove(level);
        LOGGER.info("ChunkIndependentScheduler stopped");
    }

    private void schedulerLoop() {
        while (running) {
            try {
                boolean hadWork = tickScheduler();
                LockSupport.parkNanos("mili-scheduler", hadWork ? 1_000_000L : 50_000_000L);
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                LOGGER.error("Scheduler loop error", e);
            }
        }
    }

    private boolean tickScheduler() {
        List<ChunkWorker> ready = new ArrayList<>();
        synchronized (workerLock) {
            for (ChunkWorker worker : activeWorkers.values()) {
                if (!worker.isReleased() && worker.getChunk() != null) {
                    ready.add(worker);
                }
            }
        }
        if (ready.isEmpty()) return false;

        CountDownLatch latch = new CountDownLatch(ready.size());
        for (ChunkWorker w : ready) {
            workerPool.submit(() -> {
                try {
                    w.captureBorder();
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                LOGGER.warn("{} workers timed out during border capture",
                    ready.stream().filter(w -> !w.waitForCapture(0)).count());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int highCount = 0;
        for (ChunkWorker w : ready) {
            if (w.isHighInteraction()) highCount++;
            w.resetForNextTick();
        }
        if (mixedMode && !strictMode && highCount > 0) {
            LOGGER.debug("CIS: {} high-interaction chunks", highCount);
        }
        return true;
    }

    public void registerChunk(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        long key = pos.toLong();
        synchronized (workerLock) {
            ChunkWorker existing = activeWorkers.get(key);
            if (existing == null) {
                ChunkWorker worker = new ChunkWorker(level, pos.x, pos.z, this);
                worker.assignChunk(chunk);
                activeWorkers.put(key, worker);
            } else if (existing.getChunk() != chunk) {
                existing.assignChunk(chunk);
            }
        }
    }

    public void unregisterChunk(ChunkPos pos) {
        long key = pos.toLong();
        synchronized (workerLock) {
            ChunkWorker w = activeWorkers.remove(key);
            if (w != null) {
                w.release();
            }
        }
    }

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
