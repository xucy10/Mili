package fun.bm.mili.scheduler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default {@link RegionRuntime} implementation.
 * <p>
 * Holds no Minecraft references beyond the opaque handle it was created with, so
 * a destroyed region can be collected as soon as every component drops its
 * runtime — which is why {@link RegionLifecycle} clears the scheduler's lookup
 * table before releasing the region (fix.md §13 step 9).
 */
public final class MiliRegionRuntime implements RegionRuntime {

    private final long regionId;
    private final Object region;

    private final AtomicReference<RegionState> state = new AtomicReference<>(RegionState.ACTIVE);

    private final RegionBudget budget = new RegionBudget();
    private final SchedulerDebt debt = new SchedulerDebt();
    private final RegionWorkQueue queue;
    private final RegionMetrics metrics;

    private volatile long lastTickNanos = System.nanoTime();
    private volatile long ticksObserved;

    public MiliRegionRuntime(long regionId, Object region) {
        this.regionId = regionId;
        this.region = region;
        this.queue = new RegionWorkQueue(regionId);
        this.metrics = new RegionMetrics(regionId);
    }

    public MiliRegionRuntime(long regionId, Object region, int softCapacity, int hardCapacity) {
        this.regionId = regionId;
        this.region = region;
        this.queue = new RegionWorkQueue(regionId, softCapacity, hardCapacity);
        this.metrics = new RegionMetrics(regionId);
    }

    @Override
    public long regionId() {
        return regionId;
    }

    @Override
    public Object region() {
        return region;
    }

    @Override
    public RegionState state() {
        return state.get();
    }

    @Override
    public RegionBudget budget() {
        return budget;
    }

    @Override
    public SchedulerDebt debt() {
        return debt;
    }

    @Override
    public RegionWorkQueue queue() {
        return queue;
    }

    @Override
    public RegionMetrics metrics() {
        return metrics;
    }

    /** Move to {@link RegionState#DRAINING}: no new work, existing backlog still runs. */
    public boolean beginDraining() {
        return state.compareAndSet(RegionState.ACTIVE, RegionState.DRAINING);
    }

    /** Move to {@link RegionState#CLOSING}: backlog has been drained. */
    public boolean beginClosing() {
        RegionState current = state.get();
        if (current == RegionState.CLOSED) return false;
        return state.compareAndSet(current, RegionState.CLOSING);
    }

    /** Move to {@link RegionState#CLOSED}: everything released. */
    public void markClosed() {
        state.set(RegionState.CLOSED);
    }

    @Override
    public void markTicked() {
        long now = System.nanoTime();
        long elapsed = now - lastTickNanos;
        lastTickNanos = now;
        ticksObserved++;
        metrics.recordTick(elapsed);
        debt.recordTick(elapsed);
    }

    @Override
    public long nanosSinceLastTick() {
        return System.nanoTime() - lastTickNanos;
    }

    public long ticksObserved() {
        return ticksObserved;
    }

    /**
     * Priority score combining two independent signals:
     * <ul>
     *   <li>debt — a region that keeps overrunning should be scheduled sooner (fix.md §21);</li>
     *   <li>wait time — a region that has not ticked for a while should not be starved.</li>
     * </ul>
     * Both are normalised to {@code [0, 1]} and averaged, so neither can dominate.
     */
    @Override
    public double priorityScore() {
        double debtBoost = debt.priorityBoost();

        long since = nanosSinceLastTick();
        // Saturate at one second: beyond that the region is equally "very late".
        double waitBoost = Math.min(1.0, since / 1_000_000_000.0);

        return (debtBoost + waitBoost) / 2.0;
    }

    @Override
    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("region_id", regionId);
        out.put("label", RegionIdRegistry.labelOf(regionId));
        out.put("state", state.get().name());
        out.put("priority_score", String.format("%.3f", priorityScore()));
        out.put("queue_size", queue.size());
        out.put("queue_soft_capacity", queue.softCapacity());
        out.put("ticks_observed", ticksObserved);
        out.put("millis_since_last_tick", nanosSinceLastTick() / 1_000_000L);
        out.put("budget_ms", String.format("%.2f", budget.budgetMillis()));
        out.put("debt_ticks", String.format("%.2f", debt.debtTicks()));
        out.put("queue_accepted_total", queue.acceptedTotal());
        out.put("queue_merged_total", queue.mergedTotal());
        out.put("queue_deferred_total", queue.deferredTotal());
        out.put("queue_rejected_total", queue.rejectedTotal());
        out.putAll(metrics.snapshot());
        return Collections.unmodifiableMap(out);
    }

    @Override
    public String toString() {
        return "MiliRegionRuntime[" + RegionIdRegistry.labelOf(regionId)
                + " state=" + state.get()
                + " q=" + queue.size()
                + " " + String.format("debt=%.2f", debt.debtTicks()) + "]";
    }
}
