package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import fun.bm.mili.scheduler.RegionOwnership;
import fun.bm.mili.scheduler.SubmissionResult;
import fun.bm.mili.scheduler.TaskController;
import fun.bm.mili.scheduler.TaskHandle;
import fun.bm.mili.scheduler.TaskState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adaptive Region Balancer.
 * <p>
 * Replaces the per-region dedicated-thread model with priority-based admission into the
 * Mili runtime.  Regions are prioritised by their real-time load: heavy regions get
 * scheduled first, idle regions are deferred or merged.
 * <p>
 * <b>Design invariant (fix.md §2, §3, §6):</b> region tick work mutates Minecraft state,
 * so it may only run on the thread that owns the region.  This class therefore never
 * hands region work to its own worker threads and never falls back to "the caller runs
 * it".  When work cannot be scheduled the answer is a {@link SubmissionResult}.
 * <pre>
 *     Caller -> Mili Scheduler -> Region Queue -> (region's owning thread) -> Execute
 * </pre>
 * Pure, snapshot-based computation is the only thing allowed on
 * {@code WorkerRuntime}; it never touches game state.
 */
public final class RegionBalancer {

    private RegionBalancer() {}

    /**
     * Hard bound on the pending task table. fix.md §3: capacity pressure must produce
     * DEFERRED / MERGED / REJECTED, never a synchronous fallback.
     */
    private static final int MAX_QUEUE_CAPACITY = 4096;

    /** Bounded wait used by {@link #submitAndWait}. */
    private static final long DEFAULT_WAIT_MILLIS = 50L;

    // ---------- State ----------

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean shutdown = new AtomicBoolean(false);

    private static final AtomicLong TASK_SEQ = new AtomicLong(0);

    private static final AtomicLong ACCEPTED = new AtomicLong();
    private static final AtomicLong MERGED = new AtomicLong();
    private static final AtomicLong DEFERRED = new AtomicLong();
    private static final AtomicLong REJECTED = new AtomicLong();
    private static final AtomicLong TIMED_OUT = new AtomicLong();

    /**
     * Initialize the balancer. Safe to call multiple times; idempotent.
     */
    public static void init() {
        if (!RegionBalancerConfig.enabled) return;
        if (initialized.getAndSet(true)) return;

        shutdown.set(false);

        // Task UUID registry (kept for cross-region parameter passing).
        RegionTaskIdRegistry.init();

        // The runtime that actually schedules and drains region work.
        fun.bm.mili.scheduler.MiliSchedulerImpl.init();

        // Adaptive TPS tracking.
        AdaptiveTPSManager.start();

        com.mojang.logging.LogUtils.getClassLogger().info(
                "RegionBalancer initialized (Mili runtime scheduler)");
    }

    // ---------- Public API ----------

    /**
     * Submit a region tick task.
     * <p>
     * fix.md §3: this never executes {@code work} on the calling thread.  A full queue
     * yields {@link SubmissionResult#DEFERRED} or {@link SubmissionResult#REJECTED}.
     *
     * @param scheduleRef the region schedule (used as a key)
     * @param tickCount   how many ticks to run
     * @param work        the actual tick work (must call the original tickRegion)
     * @return what the scheduler decided
     */
    public static SubmissionResult submit(Object scheduleRef, long tickCount, Runnable work) {
        if (work == null) return SubmissionResult.REJECTED;

        if (!isActive()) {
            // Feature disabled: there is no balancer at all, so the caller keeps its
            // original behaviour. This is the config-off path, NOT backpressure.
            runUnbalanced(work, "disabled");
            return SubmissionResult.ACCEPTED;
        }

        long taskUid = TASK_SEQ.incrementAndGet();
        UUID taskUuid = RegionTaskIdRegistry.allocateAndRegister("region-tick", scheduleRef);

        // Backpressure before admission (fix.md §3).
        if (TaskController.liveCount() >= MAX_QUEUE_CAPACITY) {
            SubmissionResult merged = tryMerge(scheduleRef, work);
            if (merged == SubmissionResult.MERGED) {
                MERGED.incrementAndGet();
                RegionTaskIdRegistry.unregister(taskUuid);
                return SubmissionResult.MERGED;
            }
            DEFERRED.incrementAndGet();
            RegionTaskIdRegistry.unregister(taskUuid);
            return SubmissionResult.DEFERRED;
        }

        SubmissionResult result = fun.bm.mili.scheduler.MiliSchedulerImpl.instance()
                .trySubmit(scheduleRef, wrap(taskUid, taskUuid, work), mergeKey(scheduleRef, tickCount));

        switch (result) {
            case ACCEPTED -> ACCEPTED.incrementAndGet();
            case MERGED -> {
                MERGED.incrementAndGet();
                RegionTaskIdRegistry.unregister(taskUuid);
            }
            case DEFERRED -> {
                DEFERRED.incrementAndGet();
                RegionTaskIdRegistry.unregister(taskUuid);
            }
            default -> {
                REJECTED.incrementAndGet();
                RegionTaskIdRegistry.unregister(taskUuid);
            }
        }
        return result;
    }

    /**
     * Submit a region tick task and block until it completes.
     * <p>
     * fix.md §2.1: the decision is made by asking whether the calling thread is the
     * region's current legal execution thread.
     * <pre>
     *     if (region.isOwnedByCurrentThread()) { work.run(); return; }
     *     scheduler.submit(region, work)  ...  bounded wait
     * </pre>
     * On timeout the task is <b>cancelled</b> (token + interrupt). It is never executed
     * inline on a thread that does not own the region.
     *
     * @return {@code true} if the work ran to completion
     */
    public static boolean submitAndWait(Object scheduleRef, long tickCount, Runnable work) {
        if (work == null) return false;

        if (!isActive()) {
            runUnbalanced(work, "disabled");
            return true;
        }

        // fix.md §2.1 — ownership first.
        if (RegionOwnership.isOwnedByCurrentThread(scheduleRef)) {
            RegionLoadMonitor.beforeTick(scheduleRef);
            long begin = System.nanoTime();
            try {
                work.run();
                return true;
            } finally {
                RegionLoadMonitor.afterTick(scheduleRef, System.nanoTime() - begin);
                markTicked(scheduleRef);
            }
        }

        long taskUid = TASK_SEQ.incrementAndGet();
        UUID taskUuid = RegionTaskIdRegistry.allocateAndRegister("region-tick", scheduleRef);
        Runnable wrapped = wrap(taskUid, taskUuid, work);

        boolean completed = fun.bm.mili.scheduler.MiliSchedulerImpl.instance()
                .submitAndWait(scheduleRef, wrapped, DEFAULT_WAIT_MILLIS, TimeUnit.MILLISECONDS);

        if (!completed) {
            // Bounded wait expired: the task was cancelled for real (fix.md §4).
            TIMED_OUT.incrementAndGet();
            RegionTaskIdRegistry.updateState(taskUuid, "cancelled");
        }
        RegionTaskIdRegistry.unregister(taskUuid);
        return completed;
    }

    private static boolean isActive() {
        return RegionBalancerConfig.enabled && initialized.get() && !shutdown.get();
    }

    /** Feature-off path: run inline because no balancer exists at all. */
    private static void runUnbalanced(Runnable work, String reason) {
        try {
            work.run();
        } catch (Throwable ex) {
            com.mojang.logging.LogUtils.getClassLogger().error(
                    "RegionBalancer inline execution failed (" + reason + ")", ex);
        }
    }

    private static Runnable wrap(long taskUid, UUID taskUuid, Runnable work) {
        return () -> {
            RegionTaskIdRegistry.updateState(taskUuid, "running");
            try {
                work.run();
                RegionTaskIdRegistry.updateState(taskUuid, "completed");
            } catch (Throwable t) {
                RegionTaskIdRegistry.updateState(taskUuid, "failed");
                throw t;
            }
        };
    }

    private static Object mergeKey(Object scheduleRef, long tickCount) {
        return "region-tick:" + fun.bm.mili.scheduler.RegionIdRegistry.idOf(scheduleRef) + ":" + tickCount;
    }

    /** Fold work into an equivalent pending task when the queue is saturated. */
    private static SubmissionResult tryMerge(Object scheduleRef, Runnable work) {
        long regionId = fun.bm.mili.scheduler.RegionIdRegistry.idOf(scheduleRef);
        for (TaskHandle handle : TaskController.liveTasksForRegion(regionId)) {
            if (handle.tryMerge(work)) {
                return SubmissionResult.MERGED;
            }
        }
        return SubmissionResult.REJECTED;
    }

    // ---------- Diagnostics ----------

    public static TaskState getTaskState(long taskUid) {
        TaskHandle handle = TaskController.get(taskUid);
        return handle != null ? handle.state() : TaskState.UNKNOWN;
    }

    public static UUID getTaskUuid(long taskUid) {
        TaskHandle handle = TaskController.get(taskUid);
        return handle != null ? handle.taskUuid() : null;
    }

    public static String getTaskTrace(long taskUid) {
        TaskHandle handle = TaskController.get(taskUid);
        if (handle == null) return "unknown";
        return handle.state().name().toLowerCase(java.util.Locale.ROOT) + ":" + handle.regionId();
    }

    /**
     * Cancel a task for real: token flipped, executing thread interrupted, state moved to
     * CANCELLED (fix.md §4 and §5).
     */
    public static boolean cancelTask(long taskUid) {
        TaskHandle handle = TaskController.get(taskUid);
        if (handle == null) return false;
        boolean cancelled = handle.cancel("cancelled-by-balancer");
        if (cancelled) TaskController.release(handle);
        return cancelled;
    }

    /**
     * Retry a task. Unlike the previous implementation this never runs the work inline —
     * a retry is just another submission and obeys the same backpressure rules.
     */
    public static boolean retryTask(long taskUid) {
        TaskHandle handle = TaskController.get(taskUid);
        if (handle == null) return false;
        if (handle.state() == TaskState.CANCELLED || handle.state() == TaskState.RUNNING) return false;

        if (!isActive()) {
            REJECTED.incrementAndGet();
            return false;
        }

        SubmissionResult result = fun.bm.mili.scheduler.MiliSchedulerImpl.instance()
                .trySubmit(handle.region(), handle.work(), mergeKey(handle.region(), 0L));
        if (result == SubmissionResult.ACCEPTED || result == SubmissionResult.MERGED) {
            ACCEPTED.incrementAndGet();
            return true;
        }
        REJECTED.incrementAndGet();
        return false;
    }

    public static void clearTaskTrace(long taskUid) {
        TaskController.release(TaskController.get(taskUid));
    }

    public static void markTicked(Object scheduleRef) {
        if (!RegionBalancerConfig.enabled) return;
        fun.bm.mili.scheduler.MiliRegionRuntime runtime =
                fun.bm.mili.scheduler.MiliSchedulerImpl.runtimeFor(scheduleRef);
        if (runtime != null) runtime.markTicked();
    }

    public static int pendingTasks() {
        return TaskController.liveCount();
    }

    public static int activeWorkers() {
        return fun.bm.mili.scheduler.WorkerRuntime.poolSize();
    }

    /** Run one scheduler round. Called by the runtime driver / perf commands. */
    public static void tick() {
        if (!isActive()) return;
        fun.bm.mili.scheduler.MiliSchedulerImpl.instance().tick();
    }

    /**
     * Drain everything queued for a region. Only legal on the region's owning thread.
     */
    public static int drainOwned(Object scheduleRef, long budgetNanos) {
        if (!isActive()) return 0;
        return fun.bm.mili.scheduler.MiliSchedulerImpl.instance()
                .drainOwned(scheduleRef, budgetNanos);
    }

    /** Get performance statistics for the region balancer. */
    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("pending_tasks", pendingTasks());
        stats.put("active_workers", activeWorkers());
        stats.put("initialized", initialized.get());
        stats.put("shutdown", shutdown.get());
        stats.put("accepted", ACCEPTED.get());
        stats.put("merged", MERGED.get());
        stats.put("deferred", DEFERRED.get());
        stats.put("rejected", REJECTED.get());
        stats.put("timed_out", TIMED_OUT.get());
        stats.putAll(RegionTaskIdRegistry.getStats());
        return stats;
    }

    /** Shutdown the balancer. */
    public static void shutdown() {
        shutdown.set(true);
        initialized.set(false);
        fun.bm.mili.scheduler.MiliSchedulerImpl.shutdown();
        RegionTaskIdRegistry.shutdown();
    }
}
