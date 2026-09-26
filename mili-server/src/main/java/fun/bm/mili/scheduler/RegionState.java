package fun.bm.mili.scheduler;

/**
 * Lifecycle state of a region inside the Mili runtime.
 * <p>
 * fix.md §9: every region-related component must obey the same destruction order,
 * summarised as "stop ingress first, then drain what is left, finally delete
 * lifecycle data".  The state machine makes that order enforceable:
 * <pre>
 *     ACTIVE -> DEACTIVATING -> DRAINING -> CLOSED
 * </pre>
 */
public enum RegionState {
    /** Accepting new work normally. */
    ACTIVE,
    /** Region is going away: new work is refused, already-queued work stays valid. */
    DEACTIVATING,
    /** No new work; pending queue is being drained and async tasks cancelled. */
    DRAINING,
    /** Fully released: queue drained, registrations/metrics/load-monitor entries removed. */
    CLOSED;

    public boolean acceptsNewWork() {
        return this == ACTIVE;
    }

    public boolean isTerminal() {
        return this == CLOSED;
    }

    /** Whether any work may still be executed in this state. */
    public boolean allowsExecution() {
        return this == ACTIVE || this == DEACTIVATING || this == DRAINING;
    }
}
