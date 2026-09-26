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
    MERGED,
    COMPLETED,
    FAILED,
    /** Scheduler stopped waiting AND the executing thread was interrupted AND the token was flipped. */
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == MERGED;
    }
}
