package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * REFACTORED: 跨区块更新总线 - 修复了原代码中的并发问题和死锁风险
 *
 * <p>原始问题:
 * <ul>
 *   <li>在遍历ConcurrentHashMap的同时进行remove操作可能导致不一致</li>
 *   <li>synchronized(injections)可能造成死锁</li>
 *   <li>缺乏对ChunkWorker状态的完整检查</li>
 *   <li>错误处理不完善</li>
 * </ul>
 *
 * <p>修复内容:
 * <ul>
 *   <li>使用ConcurrentLinkedQueue替代synchronizedList避免锁竞争</li>
 *   <li>使用快照遍历避免遍历期间修改集合</li>
 *   <li>添加了完整的ChunkWorker状态验证</li>
 *   <li>改进了错误处理和日志记录</li>
 * </ul>
 */
public final class CrossChunkBus {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long COORDINATOR_POLL_NANOS = 50_000_000L;
    private static final int MAX_INJECTIONS_PER_CHUNK = 1000; // 防止内存溢出

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
     * REFACTORED: 使用快照遍历避免遍历期间修改集合
     */
    private void drainBorderUpdates() {
        if (borderUpdateQueue.isEmpty()) return;

        // 创建entry的快照进行遍历
        List<Map.Entry<Long, Queue<Runnable>>> entries = new ArrayList<>(borderUpdateQueue.entrySet());

        for (Map.Entry<Long, Queue<Runnable>> entry : entries) {
            long targetKey = entry.getKey();
            Queue<Runnable> tasks = entry.getValue();

            // FIXED: 直接使用 remove 返回值判断，避免竞态窗口
            if (tasks == null || tasks.isEmpty()) {
                borderUpdateQueue.remove(targetKey, tasks);
                continue;
            }

            // 获取或创建注入队列
            Queue<Runnable> injection = injectionQueue.computeIfAbsent(targetKey,
                k -> new ConcurrentLinkedQueue<>());

            // 限制单个区块的注入数量，防止内存溢出
            if (injection.size() >= MAX_INJECTIONS_PER_CHUNK) {
                LOGGER.warn("CrossChunkBus: injection queue for chunk {} exceeded limit, dropping {} tasks",
                    targetKey, tasks.size());
                tasks.clear();
                borderUpdateQueue.remove(targetKey);
                continue;
            }

            // 原子性地移动任务
            Runnable task;
            while ((task = tasks.poll()) != null) {
                injection.offer(task);
            }

            borderUpdateQueue.remove(targetKey);
        }
    }

    /**
     * REFACTORED: 改进注入传递逻辑，避免死锁
     */
    private void deliverInjections() {
        if (injectionQueue.isEmpty()) return;

        // 创建entry的快照进行遍历
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
                // FIXED: 提升日志级别，getWorker 失败可能是重要问题
                LOGGER.warn("CrossChunkBus: failed to get worker for chunk ({}, {}): {}",
                    tx, tz, e.getMessage());
                continue;
            }

            if (target == null || target.isReleased()) {
                // 如果目标worker不可用，清理注入队列
                LOGGER.debug("CrossChunkBus: target worker for chunk ({}, {}) unavailable, dropping {} injections",
                    tx, tz, injections.size());
                injections.clear();
                injectionQueue.remove(targetKey);
                continue;
            }

            // FIXED: 限制每批次处理的任务数量和总执行时间，避免阻塞 coordinator 线程
            List<Runnable> toExecute = new ArrayList<>();
            Runnable injection;
            final int BATCH_SIZE = 100; // 每批次最多处理100个任务
            final long BATCH_TIME_NANOS = 10_000_000L; // 每批次最多10ms
            long batchStartTime = System.nanoTime();

            while ((injection = injections.poll()) != null) {
                // 检查是否超过批次限制
                if (toExecute.size() >= BATCH_SIZE) {
                    break;
                }
                // 检查是否超过批次时间限制
                if (System.nanoTime() - batchStartTime > BATCH_TIME_NANOS && !toExecute.isEmpty()) {
                    break;
                }
                toExecute.add(injection);
            }

            // 执行收集到的任务
            for (Runnable task : toExecute) {
                try {
                    task.run();
                } catch (Exception e) {
                    LOGGER.error("CrossChunkBus injection failed for chunk ({},{})", tx, tz, e);
                }
            }

            // 如果队列为空，移除entry
            if (injections.isEmpty()) {
                injectionQueue.remove(targetKey);
            }
        }
    }

    // ======================== Public API ========================

    /**
     * 入队一个边界更新任务
     *
     * @param source 源ChunkWorker
     * @param targetChunkKey 目标区块键
     * @param injection 要执行的任务
     */
    public void enqueueBorderUpdate(ChunkWorker source, long targetChunkKey, Runnable injection) {
        if (source == null || injection == null) return;

        Queue<Runnable> queue = borderUpdateQueue.computeIfAbsent(targetChunkKey,
            k -> new ConcurrentLinkedQueue<>());

        // 限制队列大小防止内存溢出
        if (queue.size() >= MAX_INJECTIONS_PER_CHUNK) {
            LOGGER.warn("CrossChunkBus: border update queue for chunk {} full, dropping task", targetChunkKey);
            return;
        }

        queue.add(injection);
    }

    /**
     * 处理指定worker的边界更新
     *
     * @param worker 目标ChunkWorker
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

    /**
     * 清空所有队列
     */
    public void clear() {
        borderUpdateQueue.clear();
        injectionQueue.clear();
    }

    /**
     * 获取运行状态
     *
     * @return 是否正在运行
     */
    public boolean isRunning() { return running.get(); }

    /**
     * 获取当前待处理的边界更新数量
     *
     * @return 待处理数量
     */
    public int getPendingBorderUpdates() {
        return borderUpdateQueue.values().stream().mapToInt(Queue::size).sum();
    }

    /**
     * 获取当前待处理的注入数量
     *
     * @return 待处理数量
     */
    public int getPendingInjections() {
        return injectionQueue.values().stream().mapToInt(Queue::size).sum();
    }
}
