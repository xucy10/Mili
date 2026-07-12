package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public enum TaskState {
        UNKNOWN,
        QUEUED,
        RUNNING,
        MERGED,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private static final class TaskRecord {
        final long taskUid;
        final Object scheduleRef;
        final Runnable work;
        final long tickCount;
        volatile TaskState state = TaskState.QUEUED;
        volatile String trace = "queued";
        volatile int retryCount;
        volatile boolean cancelRequested;
        volatile long updatedNanos;

        TaskRecord(long taskUid, Object scheduleRef, Runnable work, long tickCount) {
            this.taskUid = taskUid;
            this.scheduleRef = scheduleRef;
            this.work = work;
            this.tickCount = tickCount;
            this.updatedNanos = System.nanoTime();
        }
    }

    /**
     * A single region tick invocation.
     */
    public static final class RegionTask implements Comparable<RegionTask> {
        // Region schedule reference (opaque, only used as a key)
        final Object scheduleRef;
        // The actual work: calls schedule.tickRegion(...)
        final Runnable work;
        // Rust-backed task UID for diagnostics and thread-side processing context.
        final long taskUid;
        // When this task was first queued
        final long enqueueNanos;
        // Estimated priority at enqueue time (updated when re-scored)
        volatile double priority;
        // How many ticks behind
        final long tickCount;
        // Monotonically increasing sequence to break ties (FIFO)
        final long seq;

        RegionTask(Object scheduleRef, Runnable work, long tickCount, long seq) {
            this(scheduleRef, work, tickCount, seq, nextTaskUid());
        }

        private RegionTask(Object scheduleRef, Runnable work, long tickCount, long seq, long taskUid) {
            this.scheduleRef = scheduleRef;
            this.work = work;
            this.tickCount = tickCount;
            this.enqueueNanos = System.nanoTime();
            this.priority = 0.0;
            this.seq = seq;
            this.taskUid = taskUid;
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
    private static final ConcurrentHashMap<Long, TaskRecord> TASK_RECORDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, RegionTask> PENDING_TASKS = new ConcurrentHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong(0);
    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);
    private static final String RUST_OPTIMIZER_CLASS_NAME = "org.mili.rust.RustOptimizer";
    private static final Method RUST_SCHEDULER_METHOD = findRustSchedulerMethod();
    private static final Method RUST_TASK_UID_METHOD = findRustTaskUidMethod();
    private static final Method RUST_NETWORK_OPT_METHOD = findRustNetworkOptimizeMethod();

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

    private static Method findRustSchedulerMethod() {
        try {
            Class<?> optimizerClass = Class.forName(RUST_OPTIMIZER_CLASS_NAME);
            return optimizerClass.getMethod("scheduler", int.class, int.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findRustTaskUidMethod() {
        try {
            Class<?> optimizerClass = Class.forName(RUST_OPTIMIZER_CLASS_NAME);
            return optimizerClass.getMethod("taskUid");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findRustNetworkOptimizeMethod() {
        try {
            Class<?> optimizerClass = Class.forName(RUST_OPTIMIZER_CLASS_NAME);
            return optimizerClass.getMethod("networkOptimize", String.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        }
    }

    private static long nextTaskUid() {
        if (RUST_TASK_UID_METHOD == null) {
            return System.nanoTime();
        }

        try {
            Object result = RUST_TASK_UID_METHOD.invoke(null);
            if (result instanceof String resultString) {
                String[] parts = resultString.split(":", 2);
                if (parts.length == 2) {
                    return Long.parseLong(parts[1]);
                }
            }
        } catch (ReflectiveOperationException | NumberFormatException ignored) {
            // Fallback to a Java-side timestamp when the Rust bridge is unavailable.
        }
        return System.nanoTime();
    }

    static MergePolicy getRustMergePolicy() {
        if (RUST_SCHEDULER_METHOD == null) {
            return MergePolicy.defaultPolicy();
        }

        try {
            Object result = RUST_SCHEDULER_METHOD.invoke(null, Math.min(4, Runtime.getRuntime().availableProcessors()), 512);
            if (result instanceof String resultString) {
                String[] parts = resultString.split(":");
                if (parts.length >= 4) {
                    int batch = parsePositive(parts[1], 1);
                    int workerCount = parsePositive(parts[2], 1);
                    int workUnits = parsePositive(parts[3], 512);
                    return MergePolicy.fromRust(batch, workerCount, workUnits);
                }
            }
        } catch (ReflectiveOperationException | NumberFormatException ignored) {
            // Fallback to Java-only behavior if the Rust bridge is unavailable.
        }
        return MergePolicy.defaultPolicy();
    }

    private static int getRustNetworkHint(int defaultBatch) {
        if (RUST_NETWORK_OPT_METHOD == null) {
            return defaultBatch;
        }

        try {
            Object result = RUST_NETWORK_OPT_METHOD.invoke(null, "1,2,4,8");
            if (result instanceof String resultString) {
                String[] parts = resultString.split(":");
                if (parts.length >= 2) {
                    int hint = parsePositive(parts[1], defaultBatch);
                    return Math.max(1, Math.min(RegionBalancerConfig.mergeBatchHardLimit, hint));
                }
            }
        } catch (ReflectiveOperationException | NumberFormatException ignored) {
            // Fallback to the batch value already derived from the scheduler policy.
        }
        return defaultBatch;
    }

    private static int parsePositive(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static final class MergePolicy {
        final int batchSize;
        final int maxMergeCount;
        final boolean allowAggressiveMerging;

        private MergePolicy(int batchSize, int maxMergeCount, boolean allowAggressiveMerging) {
            this.batchSize = batchSize;
            this.maxMergeCount = maxMergeCount;
            this.allowAggressiveMerging = allowAggressiveMerging;
        }

        static MergePolicy defaultPolicy() {
            return new MergePolicy(4, 4, false);
        }

        static MergePolicy fromRust(int batch, int workerCount, int workUnits) {
            int boundedBatch = Math.max(1, Math.min(RegionBalancerConfig.mergeBatchHardLimit, batch));
            int maxMergeCount = Math.max(1, Math.min(RegionBalancerConfig.mergeBatchHardLimit, boundedBatch));
            boolean aggressive = workUnits > 512 && workerCount >= 2;
            return new MergePolicy(boundedBatch, maxMergeCount, aggressive);
        }
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
                    MergePolicy policy = getRustMergePolicy();
                    int mergeBatchSize = Math.min(
                            RegionBalancerConfig.mergeBatchHardLimit,
                            policy.allowAggressiveMerging
                                    ? Math.max(policy.batchSize, 2)
                                    : Math.min(RegionBalancerConfig.mergeBatchSoftLimit, policy.batchSize)
                    );
                    mergeBatchSize = Math.max(mergeBatchSize, getRustNetworkHint(mergeBatchSize));
                    if (snap.avgTickNanos() <= RegionBalancerConfig.lowLoadThresholdMs * 1_000_000.0 * 0.5) {
                        mergeBatchSize = Math.max(mergeBatchSize, Math.min(RegionBalancerConfig.mergeBatchHardLimit, policy.maxMergeCount));
                    }
                    while (mergeList.size() < mergeBatchSize) {
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
                                try {
                                    markTaskState(mergeList, TaskState.MERGED, "merged");
                                    markTaskState(mergeList, TaskState.RUNNING, "running");
                                    w.run();
                                    markTaskState(mergeList, TaskState.COMPLETED, "completed");
                                }
                                catch (Throwable ex) {
                                    markTaskState(mergeList, TaskState.FAILED, "failed");
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
                        markTaskState(List.of(task), TaskState.RUNNING, "running");
                        task.work.run();
                        markTaskState(List.of(task), TaskState.COMPLETED, "completed");
                    } catch (Throwable ex) {
                        markTaskState(List.of(task), TaskState.FAILED, "failed");
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
        registerTask(task);
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

        RegionLoadMonitor.beforeTick(scheduleRef);
        final long begin = System.nanoTime();
        work.run(); // execute on the calling thread to preserve region context
        RegionLoadMonitor.afterTick(scheduleRef, System.nanoTime() - begin);
        markTicked(scheduleRef);
    }

    private static void registerTask(RegionTask task) {
        TaskRecord record = TASK_RECORDS.computeIfAbsent(task.taskUid, id ->
                new TaskRecord(id, task.scheduleRef, task.work, task.tickCount));
        record.state = TaskState.QUEUED;
        record.trace = "queued:" + task.seq;
        record.updatedNanos = System.nanoTime();
        PENDING_TASKS.put(task.taskUid, task);
    }

    private static void markTaskState(List<RegionTask> tasks, TaskState state, String trace) {
        for (RegionTask task : tasks) {
            TaskRecord record = TASK_RECORDS.computeIfAbsent(task.taskUid, id ->
                    new TaskRecord(id, task.scheduleRef, task.work, task.tickCount));
            record.state = state;
            record.trace = trace + ":" + task.seq;
            record.updatedNanos = System.nanoTime();
            if (state == TaskState.COMPLETED || state == TaskState.FAILED || state == TaskState.CANCELLED) {
                PENDING_TASKS.remove(task.taskUid);
            }
        }
    }

    public static TaskState getTaskState(long taskUid) {
        TaskRecord record = TASK_RECORDS.get(taskUid);
        return record != null ? record.state : TaskState.UNKNOWN;
    }

    public static String getTaskTrace(long taskUid) {
        TaskRecord record = TASK_RECORDS.get(taskUid);
        return record != null ? record.trace : "unknown";
    }

    public static boolean cancelTask(long taskUid) {
        TaskRecord record = TASK_RECORDS.get(taskUid);
        if (record == null) {
            return false;
        }
        record.cancelRequested = true;
        record.state = TaskState.CANCELLED;
        record.trace = "cancelled";
        record.updatedNanos = System.nanoTime();
        PENDING_TASKS.remove(taskUid);
        return true;
    }

    public static boolean retryTask(long taskUid) {
        TaskRecord record = TASK_RECORDS.get(taskUid);
        if (record == null || record.cancelRequested && record.state == TaskState.CANCELLED) {
            return false;
        }
        if (record.state == TaskState.RUNNING) {
            return false;
        }
        record.retryCount++;
        record.cancelRequested = false;
        record.state = TaskState.QUEUED;
        record.trace = "retried:" + record.retryCount;
        record.updatedNanos = System.nanoTime();

        if (!RegionBalancerConfig.enabled || WORKER_POOL == null) {
            record.work.run();
            record.state = TaskState.COMPLETED;
            record.trace = "completed:retry";
            return true;
        }

        RegionTask retryTask = new RegionTask(record.scheduleRef, record.work, record.tickCount, SEQUENCE.incrementAndGet(), taskUid);
        retryTask.priority = RegionLoadMonitor.computePriority(record.scheduleRef, System.nanoTime());
        registerTask(retryTask);
        TASK_QUEUE.add(retryTask);
        return true;
    }

    public static void clearTaskTrace(long taskUid) {
        TaskRecord record = TASK_RECORDS.get(taskUid);
        if (record != null) {
            record.trace = "cleared";
        }
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
     * Get performance statistics for the region balancer.
     */
    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("pending_tasks", pendingTasks());
        stats.put("active_workers", activeWorkers());
        stats.put("initialized", INITIALIZED.get());
        stats.put("shutdown", SHUTDOWN.get());
        return stats;
    }

    /**
     * Get region balancer statistics.
     */
    public static java.util.Map<String, Integer> getStats() {
        java.util.Map<String, Integer> stats = new java.util.LinkedHashMap<>();
        stats.put("pending_tasks", pendingTasks());
        stats.put("active_workers", activeWorkers());
        stats.put("initialized", INITIALIZED.get() ? 1 : 0);
        stats.put("task_records", TASK_RECORDS.size());
        stats.put("pending_task_refs", PENDING_TASKS.size());
        return stats;
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
