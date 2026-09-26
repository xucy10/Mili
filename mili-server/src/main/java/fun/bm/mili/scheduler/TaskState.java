package fun.bm.mili.scheduler;

/**
 * Lifecycle state of a single submitted task.
 * <p>
 * fix.md §4: the previous implementation could mark a task {@code FAILED} or
 * {@code CANCELLED} while the code was still running and still mutating
 * Minecraft state.  Therefore {@link #CANCELLED} and {@link #TIMED_OUT} are only
 * ever published <em>after</em> the executing thread has observed the
 * cancellation and left {@link TaskHandle#runTask()}.
 */
public enum TaskState {

    /** Created but not yet queued. */
    CREATED,
    /** Waiting in a region queue. */
    QUEUED,
    /** Currently executing. */
    RUNNING,
    /** Finished normally. */
    COMPLETED,
    /** Threw a {@link Throwable}. */
    FAILED,
    /** Cancelled before or during execution; the task has stopped mutating state. */
    CANCELLED,
    /** Exceeded its deadline. Cancellation was attempted; see {@link #CANCELLED}. */
    TIMED_OUT,
    /** Merged into an equivalent already-queued task; this handle never ran on its own. */
    MERGED;

    /** Whether no further state transition is possible. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMED_OUT || this == MERGED;
    }

    /** Whether the task ran to completion and its effects are valid. */
    public boolean isSuccessful() {
        return this == COMPLETED;
    }
}
