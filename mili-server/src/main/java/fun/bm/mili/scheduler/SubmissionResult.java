package fun.bm.mili.scheduler;

/**
 * Outcome of a scheduler submission.
 * <p>
 * fix.md §3: when a queue is full the scheduler must <b>never</b> fall back to
 * "the caller executes a foreign region's work".  That fallback silently breaks
 * Region Ownership and produces failures that are almost impossible to
 * reproduce.  Instead the scheduler decides what happens to the task:
 *
 * <pre>
 *     Queue Full
 *        |
 *        +-- mergeable          -> MERGED   (folded into an equivalent task)
 *        +-- high priority      -> ACCEPTED (bounded overshoot)
 *        +-- still within SLA   -> DEFERRED (caller retries, nothing runs)
 *        +-- past deadline      -> REJECTED (dropped, nothing runs)
 * </pre>
 *
 * The caller is never allowed to run the work itself.
 */
public enum SubmissionResult {

    /** Queued for execution on the region's owning thread. */
    ACCEPTED,

    /** Not queued separately: folded into an already-queued equivalent task. */
    MERGED,

    /**
     * Queue was full and the task is still within its deadline. Nothing was
     * queued; the caller may retry later. The caller must <b>not</b> run the
     * work inline.
     */
    DEFERRED,

    /**
     * Dropped: past deadline, or the region/scheduler is closing. Nothing was
     * queued, and the caller must <b>not</b> run the work inline.
     */
    REJECTED;

    /** Whether the task is now owned by the scheduler. */
    public boolean isAccepted() {
        return this == ACCEPTED || this == MERGED;
    }

    /**
     * Whether the submitting caller is allowed to execute the work on its own
     * thread. Always {@code false} — kept as an explicit, testable statement of
     * the invariant in fix.md §2.3.
     */
    public boolean allowsInlineExecution() {
        return false;
    }
}
