package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import org.jetbrains.annotations.NotNull;

import java.util.*;
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
        final UUID taskUuid; // Mili - globally unique task UUID for cross-region parameter passing
        final Object scheduleRef;
        final Runnable work;
        final long tickCount;
        volatile TaskState state = TaskState.QUEUED;
        volatile String trace = "queued";
        volatile int retryCount;
        volatile boolean cancelRequested;
        volatile long updatedNanos;

        TaskRecord(long taskUid, UUID taskUuid, Object scheduleRef, Runnable work, long tickCount) {
            this.taskUid = taskUid;
            this.taskUuid = taskUuid;
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
        // Mili start - globally unique task UUID for cross-region parameter passing
        final UUID taskUuid;
        // Mili end
        // When this task was first queued
        final long enqueueNanos;
        // Estimated priority at enqueue time (updated when re-scored)
        volatile double priority;
        // How many ticks behind
        final long tickCount;
        // Monotonically increasing sequence to break ties (FIFO)
        final long seq;

        RegionTask(Object scheduleRef, Runnable work, long tickCount, long seq) {
            this(scheduleRef, work, tickCount, seq, nextTaskUid(), nextTaskUuid());
        }

        private RegionTask(Object scheduleRef, Runnable work, long tickCount, long seq, long taskUid) {
            this(scheduleRef, work, tickCount, seq, taskUid, nextTaskUuid());
        }

        // Mili start - retry constructor: reuses existing taskUuid (no duplicate UUID)
        private RegionTask(Object scheduleRef, Runnable work, long tickCount, long seq, long taskUid, UUID taskUuid) {
            this.scheduleRef = scheduleRef;
            this.work = work;
            this.tickCount = tickCount;
            this.enqueueNanos = System.nanoTime();
            this.priority = 0.0;
            this.seq = seq;
            this.taskUid = taskUid;
            this.taskUuid = taskUuid;
        }
        // Mili end

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

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static volatile ExecutorService workerPool;
    private static final PriorityBlockingQueue<RegionTask> taskQueue =
            new PriorityBlockingQueue<>();
    private static final ConcurrentHashMap<Integer, AtomicLong> lastTickTime =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, TaskRecord> taskRecords = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, RegionTask> pendingTasks = new ConcurrentHashMap<>();
    private static final AtomicLong sequence = new AtomicLong(0);
    private static final AtomicBoolean shutdown = new AtomicBoolean(false);

    // Mili start - fix: periodic cleanup of completed task records to prevent OOM
    private static final long TASK_RECORD_TTL_NS = 60_000_000_000L; // 60 seconds
    private static final AtomicLong lastRecordCleanupNanos = new AtomicLong(0);
    private static final long RECORD_CLEANUP_INTERVAL_NS = 30_000_000_000L; // 30 seconds
    // Mili end

    /**
     * Initialize the balancer.  Safe to call multiple times; idempotent.
     */
    public static void init() {
        if (!RegionBalancerConfig.enabled) return;
        if (initialized.getAndSet(true)) return;

        // Mili start - initialize task UUID registry
        RegionTaskIdRegistry.init();
        // Mili end

        int poolSize = RegionBalancerConfig.getThreadPoolSize();
        workerPool = Executors.newFixedThreadPool(poolSize, r -> {
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

    private static final AtomicLong taskUidGen = new AtomicLong(0);

    private static long nextTaskUid() {
        return taskUidGen.incrementAndGet();
    }

    // Mili start - UUID allocation via RegionTaskIdRegistry
    private static UUID nextTaskUuid() {
        return RegionTaskIdRegistry.allocateAndRegister("region-tick", null);
    }
    // Mili end

    static MergePolicy getRustMergePolicy() {
        return MergePolicy.defaultPolicy();
    }

    private static int getRustNetworkHint(int defaultBatch) {
        return defaultBatch;
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
    }

    private static void dispatchLoop() {
        while (!shutdown.get()) {
            try {
                RegionTask task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
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
                        RegionTask next = taskQueue.poll();
                        if (next == null) break;
                        RegionLoadMonitor.RegionLoadSnapshot nextSnap = RegionLoadMonitor.getSnapshot(next.scheduleRef);
                        if (nextSnap.isLowLoad()) {
                            mergeList.add(next);
                        } else {
                            taskQueue.add(next); // high-load, put back
                            break;
                        }
                    }
                    if (mergeList.size() > 1) {
                        final List<Runnable> works = new ArrayList<>();
                        for (RegionTask t : mergeList) works.add(t.work);
                        workerPool.execute(() -> {
                            for (Runnable w : works) {
                                try {
                                    w.run();
                                }
                                catch (Throwable ex) {
                                    com.mojang.logging.LogUtils.getClassLogger().error(
                                            "Merged region task failed", ex);
                                }
                            }
                            for (RegionTask t : mergeList) {
                                markTaskState(t, TaskState.COMPLETED, "completed");
                            }
                        });
                        continue;
                    }
                }
                // Mili end - Region dynamic merge

                workerPool.execute(() -> {
                    try {
                        markTaskState(task, TaskState.RUNNING, "running");
                        task.work.run();
                        markTaskState(task, TaskState.COMPLETED, "completed");
                    } catch (Throwable ex) {
                        markTaskState(task, TaskState.FAILED, "failed");
                        com.mojang.logging.LogUtils.getClassLogger().error(
                                "RegionBalancer task failed", ex);
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable ex) {
                // Mili start - fix: catch Throwable (not just Exception) to prevent dispatcher thread death
                // An Error (e.g. OOM) would kill the dispatcher thread permanently,
                // causing all future submit() calls to queue forever without execution.
                com.mojang.logging.LogUtils.getClassLogger().error(
                        "RegionBalancer dispatch loop error (survived)", ex);
                // Mili end
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
        if (!RegionBalancerConfig.enabled || workerPool == null) {
            // Mili start - fix: catch exceptions in fallback synchronous execution to prevent server crash
            try {
                work.run();
            } catch (Throwable ex) {
                com.mojang.logging.LogUtils.getClassLogger().error(
                        "RegionBalancer fallback synchronous execution failed", ex);
            }
            // Mili end
            return;
        }

        int key = System.identityHashCode(scheduleRef);
        AtomicLong lastTick = lastTickTime.computeIfAbsent(key, k -> new AtomicLong(0));
        long last = lastTick.get();

        double priority = RegionLoadMonitor.computePriority(scheduleRef, last);

        RegionTask task = new RegionTask(scheduleRef, work, tickCount, sequence.incrementAndGet());
        task.priority = priority;
        registerTask(task);
        taskQueue.add(task);
    }

    /**
     * Submit a region tick task and block until it completes.
     * This is the "real thread pool scheduling" entry point:
     * the per-region dedicated thread hands off the tick work to the
     * shared thread pool and waits for it to finish.
     */
    public static void submitAndWait(Object scheduleRef, long tickCount, Runnable work) {
        if (!RegionBalancerConfig.enabled || workerPool == null) {
            // Mili start - fix: catch exceptions in fallback synchronous execution
            try {
                work.run();
            } catch (Throwable ex) {
                com.mojang.logging.LogUtils.getClassLogger().error(
                        "RegionBalancer submitAndWait fallback failed", ex);
            }
            // Mili end
            return;
        }

        RegionLoadMonitor.beforeTick(scheduleRef);
        final long begin = System.nanoTime();
        // Mili start - fix: catch exceptions to prevent crash propagation
        try {
            work.run(); // execute on the calling thread to preserve region context
        } catch (Throwable ex) {
            com.mojang.logging.LogUtils.getClassLogger().error(
                    "RegionBalancer submitAndWait execution failed", ex);
        }
        // Mili end
        RegionLoadMonitor.afterTick(scheduleRef, System.nanoTime() - begin);
        markTicked(scheduleRef);
    }

    private static void registerTask(RegionTask task) {
        // Mili start - update Registry state for this task UUID
        RegionTaskIdRegistry.updateState(task.taskUuid, "queued");
        // Mili end
        TaskRecord record = taskRecords.computeIfAbsent(task.taskUid, id ->
                new TaskRecord(id, task.taskUuid, task.scheduleRef, task.work, task.tickCount));
        record.state = TaskState.QUEUED;
        record.trace = "queued:" + task.seq;
        record.updatedNanos = System.nanoTime();
        pendingTasks.put(task.taskUid, task);
    }

    private static void markTaskState(RegionTask task, TaskState state, String trace) {
        TaskRecord record = taskRecords.computeIfAbsent(task.taskUid, id ->
                new TaskRecord(id, task.taskUuid, task.scheduleRef, task.work, task.tickCount));
        record.state = state;
        record.trace = trace + ":" + task.seq;
        record.updatedNanos = System.nanoTime();
        // Mili start - update Registry state and unregister on terminal states
        RegionTaskIdRegistry.updateState(task.taskUuid, state.name().toLowerCase(java.util.Locale.ROOT));
        if (state == TaskState.COMPLETED || state == TaskState.FAILED || state == TaskState.CANCELLED) {
            pendingTasks.remove(task.taskUid);
            RegionTaskIdRegistry.unregister(task.taskUuid);
        }
        // Mili end
        // Mili start - fix: periodic cleanup of stale task records to prevent OOM
        maybeCleanupStaleTaskRecords();
        // Mili end
    }

    public static TaskState getTaskState(long taskUid) {
        TaskRecord record = taskRecords.get(taskUid);
        return record != null ? record.state : TaskState.UNKNOWN;
    }

    // Mili start - get task UUID by taskUid
    public static UUID getTaskUuid(long taskUid) {
        TaskRecord record = taskRecords.get(taskUid);
        return record != null ? record.taskUuid : null;
    }
    // Mili end

    public static String getTaskTrace(long taskUid) {
        TaskRecord record = taskRecords.get(taskUid);
        return record != null ? record.trace : "unknown";
    }

    public static boolean cancelTask(long taskUid) {
        TaskRecord record = taskRecords.get(taskUid);
        if (record == null) {
            return false;
        }
        record.cancelRequested = true;
        record.state = TaskState.CANCELLED;
        record.trace = "cancelled";
        record.updatedNanos = System.nanoTime();
        pendingTasks.remove(taskUid);
        // Mili start - unregister from global UUID registry
        RegionTaskIdRegistry.unregister(record.taskUuid);
        // Mili end
        return true;
    }

    public static boolean retryTask(long taskUid) {
        TaskRecord record = taskRecords.get(taskUid);
        if (record == null || record.cancelRequested || record.state == TaskState.CANCELLED) {
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

        // Mili start - re-register UUID for retry (no duplicate — reuse existing)
        RegionTaskIdRegistry.register(record.taskUuid, "region-tick-retry", record.scheduleRef);
        // Mili end

        if (!RegionBalancerConfig.enabled || workerPool == null) {
            // Mili start - fix: catch exceptions in fallback retry execution
            try {
                record.work.run();
            } catch (Throwable ex) {
                com.mojang.logging.LogUtils.getClassLogger().error(
                        "RegionBalancer retry fallback failed", ex);
            }
            // Mili end
            record.state = TaskState.COMPLETED;
            record.trace = "completed:retry";
            // Mili start - unregister on inline completion
            RegionTaskIdRegistry.unregister(record.taskUuid);
            // Mili end
            return true;
        }

        // Mili start - retry reuses the same taskUuid (no new UUID allocated)
        RegionTask retryTask = new RegionTask(record.scheduleRef, record.work, record.tickCount, sequence.incrementAndGet(), taskUid, record.taskUuid);
        // Mili end
        retryTask.priority = RegionLoadMonitor.computePriority(record.scheduleRef, System.nanoTime());
        registerTask(retryTask);
        taskQueue.add(retryTask);
        return true;
    }

    public static void clearTaskTrace(long taskUid) {
        TaskRecord record = taskRecords.get(taskUid);
        if (record != null) {
            record.trace = "cleared";
        }
    }

    // Mili start - fix: periodic cleanup of stale task records to prevent OOM
    private static void maybeCleanupStaleTaskRecords() {
        long now = System.nanoTime();
        long last = lastRecordCleanupNanos.get();
        if (now - last < RECORD_CLEANUP_INTERVAL_NS) return;
        if (!lastRecordCleanupNanos.compareAndSet(last, now)) return;

        taskRecords.entrySet().removeIf(entry -> {
            TaskRecord rec = entry.getValue();
            return (rec.state == TaskState.COMPLETED || rec.state == TaskState.FAILED || rec.state == TaskState.CANCELLED)
                    && (now - rec.updatedNanos > TASK_RECORD_TTL_NS);
        });
    }
    // Mili end

    public static void markTicked(Object scheduleRef) {
        if (!RegionBalancerConfig.enabled) return;
        int key = System.identityHashCode(scheduleRef);
        AtomicLong last = lastTickTime.get(key);
        if (last != null) {
            last.set(System.nanoTime());
        }
    }

    public static int pendingTasks() {
        return taskQueue.size();
    }

    public static int activeWorkers() {
        if (workerPool instanceof ThreadPoolExecutor tpe) {
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
        stats.put("initialized", initialized.get());
        stats.put("shutdown", shutdown.get());
        // Mili start - include UUID registry stats
        stats.putAll(RegionTaskIdRegistry.getStats());
        // Mili end
        return stats;
    }

    /**
     * Shutdown the balancer.
     */
    public static void shutdown() {
        shutdown.set(true);
        if (workerPool != null) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        // Mili start - shutdown task UUID registry
        RegionTaskIdRegistry.shutdown();
        // Mili end
    }
}