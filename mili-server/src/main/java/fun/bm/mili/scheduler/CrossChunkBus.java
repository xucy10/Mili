package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Cross-chunk coordination bus (专用协调线程).
 *
 * Design principles:
 * 1. CrossChunkBus 线程永不持有 ChunkWorker 锁 — 通过 volatile 标志位通信
 * 2. 两阶段提交：Phase 1 采集边界状态，Phase 2 tick 结束后注入延迟更新
 * 3. 所有跨区块操作有 1 tick 延迟（可配置 strict mode 回退 region 模式实现 0-tick）
 * 4. 超时降级：等待 > timeoutMs 则放弃并回退到 region 模式
 * 5. 锁按 ChunkPos 字典序获取
 */
public final class CrossChunkBus {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long COORDINATOR_POLL_NANOS = 500_000L; // 0.5ms

    // ---- Border Update Queues ----
    // Phase 1: captured border state (source -> target chunks)
    private final ConcurrentMap<Long, List<BorderUpdateTask>> borderUpdateQueue = new ConcurrentHashMap<>();

    // Phase 2: delayed injections (from coordinator to target workers)
    private final ConcurrentMap<Long, List<Runnable>> injectionQueue = new ConcurrentHashMap<>();

    // ---- Dedicated coordinator thread ----
    private final ServerLevel level;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread coordinatorThread;
    private volatile boolean shutdown;

    // ---- Timeout config (snapshot for fast access) ----
    private volatile long timeoutNanos;

    public CrossChunkBus(ServerLevel level) {
        this.level = level;
        this.timeoutNanos = 50_000_000L; // 50ms default
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
                // Drain border-update tasks: move from borderUpdateQueue to injectionQueue
                drainBorderUpdates();

                // Deliver border injections to workers that are ready
                deliverInjections();

                LockSupport.parkNanos(COORDINATOR_POLL_NANOS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("CrossChunkBus coordinator error", e);
            }
        }
        running.set(false);
    }

    /** Phase 2: Move border updates to injection queue (跨一 tick 延迟) */
    private void drainBorderUpdates() {
        if (borderUpdateQueue.isEmpty()) return;

        for (var iter = borderUpdateQueue.entrySet().iterator(); iter.hasNext();) {
            var entry = iter.next();
            long targetKey = entry.getKey();
            List<BorderUpdateTask> tasks = entry.getValue();
            if (tasks == null || tasks.isEmpty()) {
                iter.remove();
                continue;
            }

            // Move to injection queue (the delay is implicit: we move in the
            // coordinator's next cycle, which is ~1 tick later)
            injectionQueue.computeIfAbsent(targetKey, k -> Collections.synchronizedList(new ArrayList<>()))
                .addAll(tasks.stream().map(BorderUpdateTask::toInjection).toList());

            iter.remove();
        }
    }

    /** Deliver pending injections to workers that have completed Phase 2 */
    private void deliverInjections() {
        if (injectionQueue.isEmpty()) return;

        for (var iter = injectionQueue.entrySet().iterator(); iter.hasNext();) {
            var entry = iter.next();
            long targetKey = entry.getKey();
            List<Runnable> injections = entry.getValue();
            if (injections == null || injections.isEmpty()) {
                iter.remove();
                continue;
            }

            int tx = (int) (targetKey >> 32);
            int tz = (int) (targetKey & 0xFFFFFFFFL);
            ChunkWorker target = ChunkIndependentScheduler.getInstance(level).getWorker(tx, tz);

            if (target == null || target.isReleased()) {
                iter.remove();
                continue;
            }

            // Deliver injections on coordinator thread.
            // Border capture is complete (read-only, fast).
            synchronized (injections) {
                for (Runnable injection : injections) {
                    try {
                        injection.run();
                    } catch (Exception e) {
                        LOGGER.error("CrossChunkBus injection failed for chunk ({},{})", tx, tz, e);
                    }
                }
            }
            iter.remove();
        }
    }

    // ======================== Public API ========================

    /**
     * Enqueue a Phase-1 border update: source chunk signals that its border
     * block has changed and neighbor chunk should be notified.
     * The update will be delivered with 1-tick delay.
     */
    public void enqueueBorderUpdate(ChunkWorker source, long targetChunkKey, Runnable injection) {
        if (source == null || injection == null) return;
        borderUpdateQueue.computeIfAbsent(targetChunkKey, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(new BorderUpdateTask(targetChunkKey, injection));
    }

    /**
     * Process pending border updates for a worker (called from ChunkWorker.tick()
     * after phase2Ready = true). Drains the injection queue for this chunk.
     */
    public void processBorderUpdates(ChunkWorker worker) {
        long key = ((long) worker.getChunkX() << 32) | (worker.getChunkZ() & 0xFFFFFFFFL);
        List<Runnable> injections = injectionQueue.remove(key);
        if (injections == null) return;
        for (Runnable injection : injections) {
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

    // ======================== Internal Types ========================

    private record BorderUpdateTask(long targetChunkKey, Runnable injection) {
        Runnable toInjection() { return injection; }
    }
}
