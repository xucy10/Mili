package fun.bm.mili.scheduler;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Default {@link RegionRuntime} implementation.
 * <p>
 * Everything here is keyed by the stable id from {@link RegionIdRegistry}, never by
 * {@code System.identityHashCode} (fix.md §8).
 */
public final class MiliRegionRuntime implements RegionRuntime {

    private final long regionId;
    private final Object region;
    private final RegionBudget budget;
    private final RegionWorkQueue queue;
    private final RegionMetrics metrics;
    private final SchedulerDebt debt;
    private final AtomicLong lastTickNanos = new AtomicLong(System.nanoTime());

    private volatile RegionState state = RegionState.ACTIVE;

    public MiliRegionRuntime(long regionId, Object region, int queueCapacity, long budgetNanos) {
        this.regionId = regionId;
        this.region = region;
        this.budget = new RegionBudget(budgetNanos);
        this.queue = new RegionWorkQueue(regionId, queueCapacity);
        this.metrics = new RegionMetrics(regionId);
        this.debt = new SchedulerDebt();
    }

    @Override public long regionId() { return regionId; }
    @Override public Object region() { return region; }
    @Override public RegionState state() { return state; }
    @Override public RegionBudget budget() { return budget; }
    @Override public RegionWorkQueue queue() { return queue; }
    @Override public RegionMetrics metrics() { return metrics; }
    @Override public SchedulerDebt debt() { return debt; }

    public long lastTickNanos() { return lastTickNanos.get(); }

    public void markTicked() { lastTickNanos.set(System.nanoTime()); }

    /**
     * Move {@code ACTIVE -> DEACTIVATING}. Idempotent.
     *
     * @return {@code true} if this call performed the transition
     */
    public boolean tryDeactivate() {
        if (state != RegionState.ACTIVE) return false;
        state = RegionState.DEACTIVATING;
        return true;
    }

    public boolean tryEnterDraining() {
        if (state == RegionState.CLOSED) return false;
        state = RegionState.DRAINING;
        return true;
    }

    public boolean tryClose() {
        if (state == RegionState.CLOSED) return false;
        state = RegionState.CLOSED;
        return true;
    }

    @Override
    public String toString() {
        return "RegionRuntime[id=" + regionId + ", state=" + state + ", depth=" + queue.depth() + "]";
    }
}
