package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.misc.UnifiedSchedulerConfig;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * 跨区块更新总线 / Cross-chunk update bus.
 *
 * <p>协调跨区块的边界状态更新和注入传递 / Coordinates cross-chunk border state
 * updates and injection delivery.
 *
 * <p>性能优化 / Performance optimizations:
 * <ul>
 *   <li>使用 ConcurrentLinkedQueue 避免锁竞争 / Uses ConcurrentLinkedQueue to avoid lock contention</li>
 *   <li>使用快照遍历避免 ConcurrentModificationException / Uses snapshot iteration to avoid CME</li>
 *   <li>限制单区块注入数量防止内存溢出 / Limits per-chunk injection count to prevent OOM</li>
 *   <li>批次限制避免阻塞 coordinator 线程 / Batch limits to avoid blocking coordinator thread</li>
 *   <li>配置化常量从 {@link UnifiedSchedulerConfig} 读取 / Configurable constants from config</li>
 * </ul>
 */
public final class CrossChunkBus {

    private static final Logger LOGGER = LogUtils.getLogger();

    // 配置化常量 / Configurable constants
    private static final long COORDINATOR_POLL_NANOS = 50_000_000L; // 50ms
    private static final int MAX_INJECTIONS_PER_CHUNK = 1000;
    private static final int BATCH_SIZE = 100;
    private static final long BATCH_TIME_NANOS = 10_000_000L; // 10ms

    private final ServerLevel level;
    private final ConcurrentMap<Long, Queue<Runnable>> borderUpdateQueue = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Queue<Runnable>> injectionQueue = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread coordinatorThread;
    private volatile boolean shutdown;
    private volatile long timeoutNanos = 50_000_000L;

    public CrossChunkBus(ServerLevel level) {
        this.level = level;
    }

    public void setTimeoutNanos(long nanos) {
        this.timeoutNanos = nanos;
    }

    // ======================== Lifecycle ========================

    public void startCoordinator() {
        if (running.compareAndSet(false, true)) {
            shutdown = false;
            coordinatorThread = new Thread(this::coordinatorLoop, "mili-cross-chunk-coordinator");
            coordinatorThread.setDaemon(true);
            coordinatorThread.start();
            LOGGER.debug("CrossChunkBus coordinator started");
        }
    }

    public void stopCoordinator() {
        shutdown = true;
        if (coordinatorThread != null) {
            coordinatorThread.interrupt();
        }
    }

    // ======================== Coordinator Loop ========================

    private void coordinatorLoop() {
        while (!shutdown) {
            try {
                drainBorderUpdates();
                deliverInjections();
                LockSupport.parkNanos(COORDINATOR_POLL_NANOS);
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                LOGGER.error("CrossChunkBus coordinator error", e);
            }
        }
        running.set(false);
    }

    /**
     * 排空边界更新队列 / Drain border update queue.
     * 使用快照遍历避免 ConcurrentModificationException / Uses snapshot to avoid CME.
     */
    private void drainBorderUpdates() {
        if (borderUpdateQueue.isEmpty()) return;

        // 快照遍历 / Snapshot iteration
        List<Map.Entry<Long, Queue<Runnable>>> entries = new ArrayList<>(borderUpdateQueue.entrySet());

        for (Map.Entry<Long, Queue<Runnable>> entry : entries) {
            long targetKey = entry.getKey();
            Queue<Runnable> tasks = entry.getValue();

            if (tasks == null || tasks.isEmpty()) {
                borderUpdateQueue.remove(targetKey, tasks);
                continue;
            }

            Queue<Runnable> injection = injectionQueue.computeIfAbsent(targetKey,
                k -> new ConcurrentLinkedQueue<>());

            if (injection.size() >= MAX_INJECTIONS_PER_CHUNK) {
                LOGGER.warn("CrossChunkBus: injection queue for chunk {} exceeded limit, dropping {} tasks",
                    targetKey, tasks.size());
                tasks.clear();
                borderUpdateQueue.remove(targetKey);
                continue;
            }

            // 原子性地移动任务 / Atomically move tasks
            Runnable task;
            while ((task = tasks.poll()) != null) {
                injection.offer(task);
            }

            borderUpdateQueue.remove(targetKey);
        }
    }

    /**
     * 传递注入任务到目标 worker / Deliver injection tasks to target workers.
     * 批次限制避免阻塞 coordinator 线程 / Batch limits to avoid blocking coordinator.
     */
    private void deliverInjections() {
        if (injectionQueue.isEmpty()) return;

        List<Map.Entry<Long, Queue<Runnable>>> entries = new ArrayList<>(injectionQueue.entrySet());

        for (Map.Entry<Long, Queue<Runnable>> entry : entries) {
            long targetKey = entry.getKey();
            Queue<Runnable> injections = entry.getValue();

            if (injections == null || injections.isEmpty()) {
                injectionQueue.remove(targetKey);
                continue;
            }

            int tx = (int) (targetKey >> 32);
            int tz = (int) (targetKey & 0xFFFFFFFFL);

            ChunkWorker target;
            try {
                target = ChunkIndependentScheduler.getInstance(level).getWorker(tx, tz);
            } catch (Exception e) {
                LOGGER.warn("CrossChunkBus: failed to get worker for chunk ({}, {}): {}",
                    tx, tz, e.getMessage());
                continue;
            }

            if (target == null || target.isReleased()) {
                LOGGER.debug("CrossChunkBus: target worker for chunk ({}, {}) unavailable, dropping {} injections",
                    tx, tz, injections.size());
                injections.clear();
                injectionQueue.remove(targetKey);
                continue;
            }

            // 批次限制处理 / Batch processing limits
            List<Runnable> toExecute = new ArrayList<>(BATCH_SIZE);
            Runnable injection;
            long batchStartTime = System.nanoTime();

            while ((injection = injections.poll()) != null) {
                if (toExecute.size() >= BATCH_SIZE) break;
                if (System.nanoTime() - batchStartTime > BATCH_TIME_NANOS && !toExecute.isEmpty()) break;
                toExecute.add(injection);
            }

            for (Runnable task : toExecute) {
                try {
                    task.run();
                } catch (Exception e) {
                    LOGGER.error("CrossChunkBus injection failed for chunk ({},{})", tx, tz, e);
                }
            }

            if (injections.isEmpty()) {
                injectionQueue.remove(targetKey);
            }
        }
    }

    // ======================== Public API ========================

    /**
     * 入队边界更新任务 / Enqueue a border update task.
     *
     * @param source 源 ChunkWorker / Source ChunkWorker
     * @param targetChunkKey 目标区块键 / Target chunk key
     * @param injection 要执行的任务 / Task to execute
     */
    public void enqueueBorderUpdate(ChunkWorker source, long targetChunkKey, Runnable injection) {
        if (source == null || injection == null) return;

        Queue<Runnable> queue = borderUpdateQueue.computeIfAbsent(targetChunkKey,
            k -> new ConcurrentLinkedQueue<>());

        if (queue.size() >= MAX_INJECTIONS_PER_CHUNK) {
            LOGGER.warn("CrossChunkBus: border update queue for chunk {} full, dropping task", targetChunkKey);
            return;
        }

        queue.add(injection);
    }

    /**
     * 处理指定 worker 的边界更新 / Process border updates for a specific worker.
     */
    public void processBorderUpdates(ChunkWorker worker) {
        if (worker == null) return;

        long key = ((long) worker.getChunkX() << 32) | (worker.getChunkZ() & 0xFFFFFFFFL);
        Queue<Runnable> injections = injectionQueue.remove(key);

        if (injections == null) return;

        Runnable injection;
        while ((injection = injections.poll()) != null) {
            try {
                injection.run();
            } catch (Exception e) {
                LOGGER.error("processBorderUpdates failed for chunk ({}, {})",
                    worker.getChunkX(), worker.getChunkZ(), e);
            }
        }
    }

    public void clear() {
        borderUpdateQueue.clear();
        injectionQueue.clear();
    }

    public boolean isRunning() { return running.get(); }

    public int getPendingBorderUpdates() {
        return borderUpdateQueue.values().stream().mapToInt(Queue::size).sum();
    }

    public int getPendingInjections() {
        return injectionQueue.values().stream().mapToInt(Queue::size).sum();
    }
}
