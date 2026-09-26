package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import fun.bm.mili.scheduler.FoliaSchedulerAdapter;
import fun.bm.mili.scheduler.MiliSchedulerImpl;
import fun.bm.mili.scheduler.RegionOwnership;
import fun.bm.mili.scheduler.RegionRuntime;
import fun.bm.mili.scheduler.SchedulerLog;
import fun.bm.mili.scheduler.SubmissionResult;
import fun.bm.mili.scheduler.TaskHandle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adaptive Region Balancer.
 * <p>
 * <b>This class is now a facade.</b> It used to own a fixed thread pool, a
 * dispatcher thread, an unbounded priority queue, a task-record map and a
 * pending-task map — a second scheduler running beside Folia's, with its own
 * notion of what "running a region tick" meant. That duplication is what
 * fix.md §17 asks to collapse, so all of it now lives in
 * {@code fun.bm.mili.scheduler} and this class translates the old call shape
 * into it.
 *
 * <p>What changed for callers is one thing, and it is the important one:
 * <b>{@link #submitAndWait} no longer runs the work on the calling thread when
 * the caller does not own the region.</b> It used to, unconditionally, on its
 * primary path:
 *
 * <pre>
 *     RegionLoadMonitor.beforeTick(scheduleRef);
 *     work.run();   // "execute on the calling thread to preserve region context"
 *     RegionLoadMonitor.afterTick(scheduleRef, elapsed);
 * </pre>
 *
 * So a thread belonging to region A that called this with region B's schedule ran
 * region B's tick inline, and the comment claimed that preserved region context
 * while doing the opposite. The two paths where running inline is <em>correct</em>
 * are kept, and are now explicit rather than incidental:
 *
 * <ol>
 *   <li><b>the balancer is disabled</b> — there is no scheduler to defer to, and
 *       the call-site contract is "this is my own region's tick", so running it
 *       here is the passthrough callers depend on;</li>
 *   <li><b>the caller owns the region</b> — the fix.md §2.1 test, answered by
 *       {@link RegionOwnership}.</li>
 * </ol>
 *
 * <p>A third case is handled deliberately rather than by accident: if ownership is
 * <em>unknowable</em> because the Folia hooks have not been installed, this class
 * runs the tick inline and warns once. Deferring instead would route a tick into a
 * queue that only the calling thread could ever drain, stalling every region tick
 * on a server whose hooks are missing — a far worse failure than the behaviour it
 * would be replacing. The degraded path is therefore explicit and logged, not
 * silent.
 *
 * <p><b>Task introspection has moved.</b> {@code getTaskState} / {@code getTaskUuid}
 * / {@code cancelTask} used to read this class's own bookkeeping. Task identity now
 * lives in the scheduler, which is the only component that can answer
 * "is this task still running?" honestly. The methods remain so existing call
 * sites compile, and they say so in their contracts.
 */
public final class RegionBalancer {

    private RegionBalancer() {}

    /** Retained for API compatibility. See the scheduler's {@code TaskState}. */
    public enum TaskState {
        UNKNOWN,
        QUEUED,
        RUNNING,
        MERGED,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /** Bounded wait for a foreign-thread {@link #submitAndWait}. */
    private static final long SUBMIT_WAIT_MILLIS = 50L;

    // ---------- State ----------

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean shutdown = new AtomicBoolean(false);

    private static final AtomicLong TASK_SEQ = new AtomicLong(0);

    private static final AtomicLong accepted = new AtomicLong();
    private static final AtomicLong merged = new AtomicLong();
    private static final AtomicLong deferred = new AtomicLong();
    private static final AtomicLong rejected = new AtomicLong();
    private static final AtomicLong inlineRuns = new AtomicLong();
    private static final AtomicLong waitTimeouts = new AtomicLong();
    private static final AtomicLong droppedWhileInactive = new AtomicLong();

    private static volatile boolean warnedMissingOwnershipHooks;

    // ---------- Lifecycle ----------

    /**
     * Initialize the balancer. Safe to call multiple times; idempotent.
     * <p>
     * The worker pool and dispatcher thread that used to be created here are gone.
     * A pool only ever had one legitimate use in this design — pure computation
     * over snapshots, which is the scheduler's compute lane — and region-owned
     * work never legitimately ran on one, so there is nothing here to start.
     */
    public static void init() {
        if (!RegionBalancerConfig.enabled) return;
        if (initialized.getAndSet(true)) return;

        shutdown.set(false);

        RegionTaskIdRegistry.init();
        FoliaSchedulerAdapter.init();

        SchedulerLog.info("RegionBalancer initialized (delegating to the Mili scheduler)");
    }

    /** Shutdown the balancer and everything it owns. Idempotent. */
    public static void shutdown() {
        if (shutdown.getAndSet(true)) return;
        initialized.set(false);

        FoliaSchedulerAdapter.shutdown();
        RegionTaskIdRegistry.shutdown();
        RegionLoadMonitor.clear();

        SchedulerLog.info("RegionBalancer shutdown");
    }

    private static boolean isActive() {
        return RegionBalancerConfig.enabled
                && initialized.get()
                && !shutdown.get()
                && FoliaSchedulerAdapter.isRunning();
    }

    // ---------- Submission ----------

    /**
     * Queue a region tick task.
     *
     * @param scheduleRef the region schedule
     * @param tickCount   how many ticks to run (informational)
     * @param work        the actual tick work
     */
    public static void submit(Object scheduleRef, long tickCount, Runnable work) {
        if (work == null || scheduleRef == null) return;

        if (!isActive()) {
            // The balancer is off. Running the work here would be the fix.md §2.3
            // violation: a caller that may belong to another region would execute
            // this region's tick. Refuse and count it instead.
            droppedWhileInactive.incrementAndGet();
            return;
        }

        long taskUid = TASK_SEQ.incrementAndGet();
        UUID taskUuid = RegionTaskIdRegistry.allocateAndRegister("region-tick", scheduleRef);

        SubmissionResult result = FoliaSchedulerAdapter.scheduleOnOwningThread(
                scheduleRef,
                wrap(taskUuid, work),
                priorityFor(scheduleRef),
                mergeKeyFor(scheduleRef, tickCount),
                TaskHandle.NO_DEADLINE,
                "region-tick");

        switch (result) {
            case ACCEPTED -> accepted.incrementAndGet();
            case MERGED -> merged.incrementAndGet();
            case DEFERRED -> deferred.incrementAndGet();
            case REJECTED -> {
                rejected.incrementAndGet();
                releaseUuid(taskUuid);
            }
        }

        // taskUid exists only to keep the submission sequence monotonic for
        // diagnostics; task identity itself is owned by the scheduler.
        if (taskUid == Long.MIN_VALUE) {
            SchedulerLog.debug("RegionBalancer task sequence wrapped");
        }
    }

    /**
     * Submit a region tick task and block until it completes.
     *
     * @see RegionBalancer the class javadoc for exactly when this may run inline
     */
    public static void submitAndWait(Object scheduleRef, long tickCount, Runnable work) {
        if (work == null || scheduleRef == null) return;

        // Case 1 — disabled. Passthrough: the caller is the region's own tick and
        // there is no scheduler in the picture.
        if (!RegionBalancerConfig.enabled || !initialized.get() || shutdown.get()) {
            runPassthrough(scheduleRef, work, "disabled");
            return;
        }

        // Case 2 — the caller demonstrably owns the region (fix.md §2.1).
        if (RegionOwnership.isOwnedByCurrentThread(scheduleRef)) {
            runPassthrough(scheduleRef, work, "owned");
            return;
        }

        // Case 3 — ownership is unknowable because the hooks are missing.
        if (!RegionOwnership.canDetermineOwnership(scheduleRef)) {
            if (!warnedMissingOwnershipHooks) {
                warnedMissingOwnershipHooks = true;
                SchedulerLog.warn(
                        "RegionBalancer: ownership hooks are not installed, so region ticks are "
                                + "running inline as before. Install the ownership resolver and the "
                                + "region locator from the Folia hooks to enable real scheduling.");
            }
            runPassthrough(scheduleRef, work, "ownership-unknown");
            return;
        }

        // Case 4 — the caller does not own the region: queue it and wait, bounded.
        UUID taskUuid = RegionTaskIdRegistry.allocateAndRegister("region-tick", scheduleRef);

        boolean completed = FoliaSchedulerAdapter.executeOnOwningThread(
                scheduleRef, wrap(taskUuid, work), SUBMIT_WAIT_MILLIS);

        if (!completed) {
            waitTimeouts.incrementAndGet();
            releaseUuid(taskUuid);
            SchedulerLog.warn(
                    "RegionBalancer: tick work did not complete within %dms; it was cancelled rather "
                            + "than executed on the calling thread.",
                    SUBMIT_WAIT_MILLIS);
        }
    }

    /**
     * Run work directly on the calling thread, preserving the pre-scheduler
     * behaviour for the cases where that is correct.
     */
    private static void runPassthrough(Object scheduleRef, Runnable work, String reason) {
        RegionLoadMonitor.beforeTick(scheduleRef);
        long begin = System.nanoTime();
        try {
            work.run();
            inlineRuns.incrementAndGet();
        } catch (Throwable ex) {
            SchedulerLog.error("Region tick failed (passthrough: " + reason + ")", ex);
        } finally {
            RegionLoadMonitor.afterTick(scheduleRef, System.nanoTime() - begin);
            markTicked(scheduleRef);
        }
    }

    /** Wrap the tick so its task UUID is released exactly once, whatever happens. */
    private static Runnable wrap(UUID taskUuid, Runnable work) {
        return () -> {
            try {
                work.run();
            } finally {
                releaseUuid(taskUuid);
            }
        };
    }

    private static void releaseUuid(UUID taskUuid) {
        if (taskUuid != null) {
            RegionTaskIdRegistry.unregister(taskUuid);
        }
    }

    /**
     * Scheduling priority for a region, derived from its debt and wait time
     * (fix.md §21). A single map lookup — this runs on every tick submission, so
     * it must not iterate or allocate.
     */
    private static int priorityFor(Object scheduleRef) {
        RegionRuntime runtime = MiliSchedulerImpl.peekRuntimeFor(scheduleRef);
        if (runtime == null) return 0;
        double score = runtime.priorityScore();
        int priority = (int) (score * TaskHandle.HIGH_PRIORITY);
        // Never claim the high-priority slot from here: that overshoot is reserved
        // for work a caller is actively blocked on.
        return Math.max(0, Math.min(TaskHandle.HIGH_PRIORITY - 1, priority));
    }

    /**
     * Merge key for a region tick.
     * <p>
     * Consecutive ticks for one region are interchangeable — running the latest
     * one is what a tick means — so under queue pressure they may be folded
     * together instead of being deferred. The key includes the region so two
     * regions are never merged with each other.
     */
    private static Object mergeKeyFor(Object scheduleRef, long tickCount) {
        return "region-tick:" + System.identityHashCode(scheduleRef) + ":" + tickCount;
    }

    // ---------- Diagnostics ----------

    /**
     * @deprecated Task state is owned by the scheduler now. Returns
     *         {@link TaskState#UNKNOWN} for every id; use
     *         {@code FoliaSchedulerAdapter.snapshot()} or
     *         {@code MiliSchedulerImpl.regionSnapshots()} instead.
     */
    @Deprecated
    public static TaskState getTaskState(long taskUid) {
        return TaskState.UNKNOWN;
    }

    /**
     * @deprecated Task UUIDs are owned by {@link RegionTaskIdRegistry} now. Use
     *         {@code RegionTaskIdRegistry.findByScheduleRef(region)} instead.
     */
    @Deprecated
    public static UUID getTaskUuid(long taskUid) {
        return null;
    }

    /**
     * @deprecated See {@link #getTaskState(long)}.
     */
    @Deprecated
    public static String getTaskTrace(long taskUid) {
        return "unknown";
    }

    /**
     * @deprecated Task cancellation is owned by the scheduler now. Use
     *         {@code FoliaSchedulerAdapter.cancelTask(taskId, reason)}, or
     *         {@code MiliSchedulerImpl.instance().destroyRegion(region)} to cancel
     *         everything belonging to a region.
     */
    @Deprecated
    public static boolean cancelTask(long taskUid) {
        return false;
    }

    /**
     * @deprecated A task handle is single-use by design. Cancelling and re-queueing
     *         the same handle would make "the scheduler says it is gone" and "the
     *         code has stopped" ambiguous again — the property fix.md §4 exists to
     *         establish. Re-submit the work instead.
     */
    @Deprecated
    public static boolean retryTask(long taskUid) {
        return false;
    }

    /** @deprecated See {@link #getTaskState(long)}. */
    @Deprecated
    public static void clearTaskTrace(long taskUid) {
        // no-op
    }

    public static void markTicked(Object scheduleRef) {
        if (!RegionBalancerConfig.enabled || scheduleRef == null) return;
        RegionLoadMonitor.beforeTick(scheduleRef);
    }

    public static int pendingTasks() {
        int total = 0;
        for (RegionRuntime rt : MiliSchedulerImpl.allRuntimes()) {
            total += rt.queue().size();
        }
        return total;
    }

    /** Number of compute-lane workers. Region-owned work no longer runs on a pool. */
    public static int activeWorkers() {
        var lane = MiliSchedulerImpl.instance().computeLane();
        return lane == null ? 0 : lane.activeWorkers();
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("pending_tasks", pendingTasks());
        stats.put("active_workers", activeWorkers());
        stats.put("initialized", initialized.get());
        stats.put("shutdown", shutdown.get());
        stats.put("accepted", accepted.get());
        stats.put("merged", merged.get());
        stats.put("deferred", deferred.get());
        stats.put("rejected", rejected.get());
        stats.put("inline_owner_runs", inlineRuns.get());
        stats.put("wait_timeouts", waitTimeouts.get());
        stats.put("dropped_while_inactive", droppedWhileInactive.get());
        stats.putAll(RegionTaskIdRegistry.getStats());
        stats.putAll(FoliaSchedulerAdapter.snapshot());
        return stats;
    }
}
