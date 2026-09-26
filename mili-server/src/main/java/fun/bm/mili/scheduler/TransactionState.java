package fun.bm.mili.scheduler;

/**
 * States of a {@link CrossRegionTransaction} (fix.md §11).
 * <pre>
 *     PREPARE -> ENQUEUED -> EXECUTING -> COMMITTED
 *     retry:   PREPARE -> DEFERRED -> EXECUTING -> COMMITTED
 *     failure: FAILED / CANCELLED
 * </pre>
 */
public enum TransactionState {
    PREPARE,
    ENQUEUED,
    /**
     * Mili start - fix: the scheduler kept the work alive for a later round
     * ({@link SubmissionResult#DEFERRED}), so the transaction it belongs to must stay
     * live as well.  Marking it {@code FAILED} here used to be "harmless" only because
     * {@code execute()} forgot to check for terminal states — fixing that check without
     * adding this state would have silently thrown the work away.
     */
    DEFERRED,
    EXECUTING,
    COMMITTED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMMITTED || this == CANCELLED || this == FAILED;
    }

    /** Whether the transaction still has work to do. */
    public boolean isLive() {
        return !isTerminal();
    }
}
