package fun.bm.mili.scheduler;

import java.util.concurrent.TimeUnit;

/**
 * The single scheduling entry point (fix.md §18).
 * <p>
 * The scheduler owns priority, deadline, budget, fairness and backpressure. It does
 * <b>not</b> own dependency ordering (that is the DAG layer's job) and it does <b>not</b>
 * own cancellation state (that is {@link TaskController}'s job).
 * <p>
 * Critical guarantee: no method here ever executes another region's work on the
 * caller's thread. When work cannot be scheduled the answer is a
 * {@link SubmissionResult}, never a silent synchronous fallback.
 */
public interface MiliScheduler {

    /**
     * Submit work for a region. Never runs the work on the calling thread.
     *
     * @return the handle, or {@code null} if the submission was rejected
     */
    TaskHandle submit(Object region, Runnable task);

    /**
     * Submit work and report exactly what happened.
     *
     * @return {@link SubmissionResult#ACCEPTED}, {@link SubmissionResult#MERGED},
     *         {@link SubmissionResult#DEFERRED} or {@link SubmissionResult#REJECTED}
     */
    SubmissionResult trySubmit(Object region, Runnable task);

    /**
     * Submit work that may be folded into an already-queued task with the same
     * {@code mergeKey} when the region queue is under pressure (fix.md §3).
     */
    SubmissionResult trySubmit(Object region, Runnable task, Object mergeKey);

    /**
     * Submit and block until the work finishes.
     * <p>
     * fix.md §2.1: if the caller already owns the region the work runs inline; otherwise
     * it goes through the region queue and the caller waits with a bounded timeout. On
     * timeout the task is <b>cancelled</b> — it is never run inline on the wrong thread.
     *
     * @return {@code true} if the work ran to completion
     */
    boolean submitAndWait(Object region, Runnable task, long timeout, TimeUnit unit);

    /** Cancel a task for real (token + interrupt + state). */
    boolean cancel(TaskHandle task);

    SchedulerState getState();

    /**
     * One scheduler round: sweep timed-out tasks, retry deferred submissions and refresh
     * budgets. Called by the runtime driver.
     */
    void tick();

    /**
     * Drain the region's queue on the thread that owns the region.
     *
     * @return number of tasks executed
     */
    int drainOwned(Object region, long budgetNanos);
}
