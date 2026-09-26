package fun.bm.mili.scheduler;

/**
 * Lifecycle state of a scheduled task.
 * <p>
 * fix.md §4: {@code FAILED} may only mean "the scheduler stopped waiting".
 * A task that reached {@link #CANCELLED} must additionally guarantee that the
 * task body is no longer mutating protected state — see
 * {@link TaskHandle#cancel()} which interrupts the executing thread and
 * flips the {@link CancellationToken}.
 */
public enum TaskState {
    UNKNOWN,
    QUEUED,
    RUNNING,
    /**
     * Cancellation has been requested for a task that is already running and the waiters
     * have been released, but the task body has not returned yet.
     * <p>
     * Mili start - fix: this state exists because {@code cancel()} used to flip a running
     * task straight to {@link #CANCELLED} while {@code complete()} could overwrite it with
     * {@link #COMPLETED} — both were plain field writes, so the winner was undefined.
     * {@code CANCELLING} is the "cancel requested, body still exiting" window; only the
     * body's own {@code complete()} may move it to {@link #CANCELLED}, which is the first
     * moment Mili can prove the task stopped mutating protected state.
     */
    CANCELLING,
    /** Not queued separately: the work was folded into an already-queued equivalent task. */
    MERGED,
    COMPLETED,
    FAILED,
    /** Scheduler stopped waiting AND the executing thread was interrupted AND the token was flipped. */
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == MERGED;
    }

    /** Whether the task is still occupying the scheduler (queued, running or exiting). */
    public boolean isLive() {
        return !isTerminal() && this != UNKNOWN;
    }
}
