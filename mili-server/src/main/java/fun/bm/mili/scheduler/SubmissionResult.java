package fun.bm.mili.scheduler;

/**
 * Outcome of a scheduler submission.
 * <p>
 * fix.md §3: when a queue is full the scheduler must <b>never</b> fall back to
 * "caller executes the foreign region's work".  The scheduler decides instead:
 * <pre>
 *     Queue Full
 *        ├── deferrable   -> DEFERRED
 *        ├── mergeable    -> MERGED
 *        ├── high priority-> bounded wait (caller sees ACCEPTED)
 *        └── past deadline-> REJECTED
 * </pre>
 */
public enum SubmissionResult {
    /** Accepted and queued for execution. */
    ACCEPTED,
    /** Not queued separately: folded into an already-queued equivalent task. */
    MERGED,
    /** Queue was full but the task may be retried later; caller must not execute it inline. */
    DEFERRED,
    /** Dropped: past deadline or region is closing. Caller must not execute it inline. */
    REJECTED;

    public boolean isAccepted() {
        return this == ACCEPTED || this == MERGED;
    }

    /** Whether the caller is allowed to run the work itself. Always {@code false}. */
    public boolean allowsInlineExecution() {
        return false;
    }
}
