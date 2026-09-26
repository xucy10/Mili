package fun.bm.mili.scheduler;

/**
 * Lifecycle state of a region as seen by the Mili scheduler.
 * <p>
 * fix.md §13: the destroy sequence is "stop accepting, drain the backlog, then
 * delete lifecycle data". This enum makes each of those phases observable so a
 * region that is closing can no longer receive cross-region work.
 */
public enum RegionState {

    /** Normal operation. */
    ACTIVE,
    /** No new work accepted; the backlog is still being executed. */
    DRAINING,
    /** Backlog drained, async work cancelled, lifecycle data being removed. */
    CLOSING,
    /** Fully released. Any further reference is a bug. */
    CLOSED;

    /** Whether new tasks may be queued for this region. */
    public boolean acceptsWork() {
        return this == ACTIVE;
    }

    /** Whether work already queued may still be executed. */
    public boolean acceptsExecution() {
        return this == ACTIVE || this == DRAINING;
    }
}
