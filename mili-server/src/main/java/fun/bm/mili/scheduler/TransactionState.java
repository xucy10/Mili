package fun.bm.mili.scheduler;

/**
 * Lifecycle of a cross-region operation.
 * <p>
 * fix.md §12/§21: a cross-region mutation is not "fire an event and forget it".
 * It is a transaction with an explicit prepare / commit boundary, so that a
 * region being destroyed can cancel everything still pointing at it
 * ({@link CrossRegionTransaction#cancelTargeting(long)}).
 */
public enum TransactionState {

    /** Source side is capturing an immutable payload. Nothing has been enqueued yet. */
    PREPARE,

    /** Payload queued into the target region's work queue. */
    ENQUEUED,

    /** Target region is applying the payload on its owning thread. */
    EXECUTING,

    /** Applied successfully. */
    COMMITTED,

    /** Threw while applying, or the target region disappeared. */
    FAILED,

    /** Target region was destroyed, or the transaction timed out before applying. */
    CANCELLED;

    public boolean isTerminal() {
        return this == COMMITTED || this == FAILED || this == CANCELLED;
    }

    /** Whether the transaction still holds a reference to a live target region. */
    public boolean holdsTarget() {
        return this == PREPARE || this == ENQUEUED || this == EXECUTING;
    }
}
