package fun.bm.mili.scheduler;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The scheduling contract every Mili subsystem talks to (fix.md §18).
 * <p>
 * fix.md is explicit that this is an abstraction to grow into, not a rewrite:
 * Folia is not torn out in one move. What the interface buys immediately is that
 * callers stop caring which executor runs their work, and the ownership rules
 * stop being folklore that each call site has to remember.
 *
 * <p>Two rules are baked into the signatures:
 * <ol>
 *   <li>{@link #submit} never runs the work on the caller. There is no overload
 *       that "runs it inline if the queue is full", because that is the bug
 *       (fix.md §2.3).</li>
 *   <li>{@link #submitAndWait} may run the work inline <em>only</em> when the
 *       caller already owns the region, and it says so through its return value
 *       rather than silently.</li>
 * </ol>
 */
public interface MiliScheduler {

    SchedulerState getState();

    default boolean isRunning() {
        return getState() == SchedulerState.RUNNING;
    }

    /**
     * Queue work to run on {@code region}'s owning thread.
     *
     * @return what the scheduler did with the task; the caller must never execute
     *         the work itself on a {@code DEFERRED} or {@code REJECTED} result
     */
    SubmissionResult submit(Object region, Runnable work);

    /**
     * Queue work with scheduling hints.
     *
     * @param priority  higher runs first; {@link TaskHandle#HIGH_PRIORITY} and above
     *                  may use the queue's bounded overshoot
     * @param mergeKey  non-null marks the task as foldable into an equivalent queued
     *                  task; must not be used for work somebody is waiting on
     * @param deadlineNanos absolute {@link System#nanoTime()} deadline, or
     *                  {@link TaskHandle#NO_DEADLINE}
     */
    SubmissionResult submit(Object region, Runnable work, int priority, Object mergeKey,
                            long deadlineNanos, String description);

    /**
     * Queue work and wait for it, bounded.
     * <p>
     * Runs inline only if the calling thread already owns {@code region}. Otherwise
     * the task is queued for the owning thread and waited on; on timeout it is
     * cancelled rather than executed on the wrong thread.
     *
     * @return {@code true} only if the work ran to completion
     */
    boolean submitAndWait(Object region, Runnable work, long timeout, TimeUnit unit);

    /**
     * Execute queued work for {@code region}, up to {@code budgetNanos}.
     * <p>
     * Must be called on the region's owning thread — this is the region-owned
     * execution lane, and the only place region work may touch world state.
     *
     * @return how many tasks ran
     */
    int drainOwned(Object region, long budgetNanos);

    /** Cancel a task by id. Safe to call from any thread. */
    boolean cancel(long taskId, String reason);

    /** Current scheduling priority of a region, in {@code [0, 1]}. */
    double priorityOf(Object region);

    /** The runtime for a region, creating it if necessary. {@code null} if the region is unknown/closed. */
    RegionRuntime runtimeFor(Object region);

    /** The runtime for a region if one exists. Never creates one. */
    RegionRuntime peekRuntime(Object region);

    /** Every live region runtime. */
    Collection<? extends RegionRuntime> runtimes();

    /** Run the full destroy sequence for a region (fix.md §13). Idempotent. */
    RegionLifecycle.DestroyReport destroyRegion(Object region);

    /** One scheduler round: expire deadlines and stale transactions, rebalance budgets. */
    void tick();

    /** The async compute lane. Pure computation only — see {@link WorkerRuntime}. */
    WorkerRuntime computeLane();

    Map<String, Object> snapshot();
}
