package fun.bm.mili.scheduler;

/**
 * States of a {@link CrossRegionTransaction} (fix.md §11).
 * <pre>
 *     PREPARE -> ENQUEUED -> EXECUTING -> COMMITTED
 *     failure: FAILED -> CANCELLED
 * </pre>
 */
public enum TransactionState {
    PREPARE,
    ENQUEUED,
    EXECUTING,
    COMMITTED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMMITTED || this == CANCELLED || this == FAILED;
    }
}
