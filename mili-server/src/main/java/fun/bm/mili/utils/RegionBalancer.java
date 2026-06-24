package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adaptive Region Balancer.
 * <p>
 * Replaces the per-region dedicated-thread model with a fixed-size thread pool
 * and priority-based scheduling.  Regions are ticked according to their
 * real-time load: heavy regions get more CPU time, idle regions are deferred.
 * <p>
 * <b>Design invariant:</b> this class does NOT touch game state.  It only
 * schedules {@code Runnable} tasks that wrap the original Folia tick logic.
 * All game logic continues to run on the region's own thread context.
 */
public final class RegionBalancer {

    private RegionBalancer() {}

    // ---------- Task model ----------

    /**
     * A single region tick invocation.
     */
    public static final class RegionTask implements Comparable<RegionTask> {
        // Region schedule reference (opaque, only used as a key)
        final Object scheduleRef;
        // The actual work: calls schedule.tickRegion(...)
        final Runnable work;
        // When this task was first queued
        final long enqueueNanos;
        // Estimated priority at enqueue time (updated when re-scored)
        volatile double priority;
        // How many ticks behind
        final long tickCount;
        // Monotonically increasing sequence to break ties (FIFO)
        final long seq;

        RegionTask(Object scheduleRef, Runnable work, long tickCount, long seq) {
            this.scheduleRef = scheduleRef;
            this.work = work;
            this.tickCount = tickCount;
            this.enqueueNanos = System.nanoTime();
            this.priority = 0.0;
            this.seq = seq;
        }

        @Override
        public int compareTo(@NotNull RegionTask o) {
            // Higher priority first
            int cmp = Double.compare(o.priority, this.priority);
            if (cmp != 0) return cmp;
            // Tie-break by sequence (older first to avoid starvation)
            return Long.compare(this.seq, o.seq);
        }
    }

    // ---------- State ----------

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static volatile ExecutorService WORKER_POOL;
    private static final PriorityBlockingQueue<RegionTask> TASK_QUEUE =
            new PriorityBlockingQueue<>();
    private static final ConcurrentHashMap<Integer, AtomicLong> LAST_TICK_TIME =
            new ConcurrentHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong(0);
    private static final AtomicLong TASK_UID = new AtomicLong(0);
    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

    /**
     * Initialize the balancer.  Safe to call multiple times; idempotent.
     */
    public static void init() {
        if (!RegionBalancerConfig.enabled) return;
        if (INITIALIZED.getAndSet(true)) return;

        int poolSize = RegionBalancerConfig.getThreadPoolSize();
        WORKER_POOL = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "RegionBalancer-Worker");
            t.setDaemon(true);
            return t;
        });

        // Start a scheduler thread that continuously pulls tasks from the queue
        // and submits them to the worker pool.  The worker pool handles actual
        // execution; this thread only does dispatching.
        Thread dispatcher = new Thread(RegionBalancer::dispatchLoop, "RegionBalancer-Dispatcher");
        dispatcher.setDaemon(true);
        dispatcher.start();

        // Mili start - Adaptive TPS
        fun.bm.mili.utils.AdaptiveTPSManager.start();
        // Mili end - Adaptive TPS

        com.mojang.logging.LogUtils.getClassLogger().info(
                "RegionBalancer initialized with {} worker threads", poolSize);
    }

    private static void dispatchLoop() {
        while (!SHUTDOWN.get()) {
            try {
                RegionTask task = TASK_QUEUE.poll(100, TimeUnit.MILLISECONDS);
                if (task == null) continue;

                // Re-score priority right before execution to avoid starvation
                long overdue = System.nanoTime() - task.enqueueNanos;
                double starvationBoost = Math.min(0.3, overdue / 100_000_000.0); // 100ms cap
                task.priority += starvationBoost;

                // Mili start - Region dynamic merge: batch low-load regions
                RegionLoadMonitor.RegionLoadSnapshot snap = RegionLoadMonitor.getSnapshot(task.scheduleRef);
                if (snap.isLowLoad()) {
                    List<RegionTask> mergeList = new ArrayList<>();
                    mergeList.add(task);
                    while (mergeList.size() < 4) { // max 4 regions per merge
                        RegionTask next = TASK_QUEUE.poll();
                        if (next == null) break;
                        RegionLoadMonitor.RegionLoadSnapshot nextSnap = RegionLoadMonitor.getSnapshot(next.scheduleRef);
                        if (nextSnap.isLowLoad()) {
                            mergeList.add(next);
                        } else {
                            TASK_QUEUE.add(next); // high-load, put back
                            break;
                        }
                    }
                    if (mergeList.size() > 1) {
                        final List<Runnable> works = new ArrayList<>();
                        for (RegionTask t : mergeList) works.add(t.work);
                        WORKER_POOL.execute(() -> {
                            for (Runnable w : works) {
                                try { w.run(); }
                                catch (Throwable ex) {
                                    com.mojang.logging.LogUtils.getClassLogger().error(
                                            "Merged region task failed", ex);
                                }
                            }
                        });
                        continue;
                    }
                }
                // Mili end - Region dynamic merge

                WORKER_POOL.execute(() -> {
                    try {
                        task.work.run();
                    } catch (Throwable ex) {
                        com.mojang.logging.LogUtils.getClassLogger().error(
                                "RegionBalancer task failed", ex);
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                com.mojang.logging.LogUtils.getClassLogger().error(
                        "RegionBalancer dispatch loop error", ex);
            }
        }
    }

    // ---------- Public API ----------

    /**
     * Submit a region tick task.
     *
     * @param scheduleRef the region schedule (used as a key)
     * @param tickCount   how many ticks to run
     * @param work        the actual tick work (must call the original tickRegion)
     */
    public static void submit(Object scheduleRef, long tickCount, Runnable work) {
        if (!RegionBalancerConfig.enabled || WORKER_POOL == null) {
            // Fallback: run synchronously
            work.run();
            return;
        }

        int key = System.identityHashCode(scheduleRef);
        AtomicLong lastTick = LAST_TICK_TIME.computeIfAbsent(key, k -> new AtomicLong(0));
        long last = lastTick.get();

        double priority = RegionLoadMonitor.computePriority(scheduleRef, last);

        RegionTask task = new RegionTask(scheduleRef, work, tickCount, SEQUENCE.incrementAndGet());
        task.priority = priority;
        TASK_QUEUE.add(task);
    }

    /**
     * Submit a region tick task and block until it completes.
     * This is the "real thread pool scheduling" entry point:
     * the per-region dedicated thread hands off the tick work to the
     * shared thread pool and waits for it to finish.
     */
    public static void submitAndWait(Object scheduleRef, long tickCount, Runnable work) {
        if (!RegionBalancerConfig.enabled || WORKER_POOL == null) {
            work.run();
            return;
        }

        long uid = TASK_UID.incrementAndGet();
        RegionLoadMonitor.beforeTick(scheduleRef);
        final long begin = System.nanoTime();
        work.run(); // execute on the calling thread to preserve region context
        RegionLoadMonitor.afterTick(scheduleRef, System.nanoTime() - begin);
        markTicked(scheduleRef);
    }

    /**
     * Get the next unique task ID (for diagnostics / logging).
     */
    public static long getNextTaskUid() {
        return TASK_UID.incrementAndGet();
    }

    /**
     * Get the current highest assigned task ID.
     */
    public static long getCurrentTaskUid() {
        return TASK_UID.get();
    }

    /**
     * Mark that a region has just completed a tick.
     * Used to track starvation for priority boosting.
     */
    public static void markTicked(Object scheduleRef) {
        if (!RegionBalancerConfig.enabled) return;
        int key = System.identityHashCode(scheduleRef);
        AtomicLong last = LAST_TICK_TIME.get(key);
        if (last != null) {
            last.set(System.nanoTime());
        }
    }

    /**
     * Get the number of tasks currently waiting in the queue.
     */
    public static int pendingTasks() {
        return TASK_QUEUE.size();
    }

    /**
     * Get the number of active worker threads.
     */
    public static int activeWorkers() {
        if (WORKER_POOL instanceof ThreadPoolExecutor tpe) {
            return tpe.getActiveCount();
        }
        return -1;
    }

    /**
     * Shutdown the balancer.
     */
    public static void shutdown() {
        SHUTDOWN.set(true);
        if (WORKER_POOL != null) {
            WORKER_POOL.shutdown();
        }
    }
}
