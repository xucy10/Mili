package fun.bm.mili.scheduler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-region scheduler metrics (fix.md §15/§17).
 * <p>
 * Replaces the previous arrangement where statistics lived in whatever map each
 * component happened to own, keyed inconsistently. Every counter here is
 * keyed by {@link RegionIdRegistry} id, is created with the runtime, and is
 * released in the same destroy step as the queue (fix.md §13 step 6), so a
 * region that dies cannot leave a growing statistics bucket behind.
 *
 * <p>All counters are {@link LongAdder} / {@link AtomicLong}: writes happen on
 * region threads every tick, reads happen on the balancer and command threads.
 */
public final class RegionMetrics {

    private final long regionId;
    private final long createdAtMillis = System.currentTimeMillis();

    private final LongAdder submitted = new LongAdder();
    private final LongAdder executed = new LongAdder();
    private final LongAdder merged = new LongAdder();
    private final LongAdder deferred = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder cancelled = new LongAdder();
    private final LongAdder timedOut = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder inlineRuns = new LongAdder();

    private final LongAdder totalTickNanos = new LongAdder();
    private final LongAdder tickCount = new LongAdder();
    private final AtomicLong peakTickNanos = new AtomicLong(0);
    private final AtomicLong budgetOvershoots = new AtomicLong(0);

    /** Tasks currently sitting in the queue. Maintained by {@link RegionWorkQueue}. */
    private final AtomicLong queuedNow = new AtomicLong(0);
    private final AtomicLong inFlightNow = new AtomicLong(0);

    public RegionMetrics(long regionId) {
        this.regionId = regionId;
    }

    public long regionId() {
        return regionId;
    }

    // ---------- Submission outcomes ----------

    public void onSubmitted() {
        submitted.increment();
    }

    public void onAccepted() {
        queuedNow.incrementAndGet();
    }

    public void onExecuted() {
        queuedNow.decrementAndGet();
        executed.increment();
    }

    public void onMerged() {
        merged.increment();
    }

    public void onDeferred() {
        deferred.increment();
    }

    public void onRejected() {
        rejected.increment();
    }

    public void onCancelled() {
        cancelled.increment();
    }

    public void onTimedOut() {
        timedOut.increment();
    }

    public void onFailed() {
        failed.increment();
    }

    /** Work ran inline because the caller already owned the region (fix.md §2.1). */
    public void onInlineRun() {
        inlineRuns.increment();
    }

    // ---------- Execution cost ----------

    public void onInFlightStart() {
        inFlightNow.incrementAndGet();
    }

    public void onInFlightEnd() {
        inFlightNow.decrementAndGet();
    }

    /** Record one region tick's wall time. */
    public void recordTick(long nanos) {
        long n = Math.max(0L, nanos);
        totalTickNanos.add(n);
        tickCount.increment();
        peakTickNanos.accumulateAndGet(n, Math::max);
    }

    public void onBudgetOvershoot() {
        budgetOvershoots.incrementAndGet();
    }

    // ---------- Reads ----------

    public long submittedCount() {
        return submitted.sum();
    }

    public long executedCount() {
        return executed.sum();
    }

    public long queuedNow() {
        return queuedNow.get();
    }

    public long inFlightNow() {
        return inFlightNow.get();
    }

    public long cancelledCount() {
        return cancelled.sum();
    }

    public long rejectedCount() {
        return rejected.sum();
    }

    public double averageTickMillis() {
        long count = tickCount.sum();
        return count == 0L ? 0.0 : (totalTickNanos.sum() / (double) count) / 1_000_000.0;
    }

    public double peakTickMillis() {
        return peakTickNanos.get() / 1_000_000.0;
    }

    public long budgetOvershoots() {
        return budgetOvershoots.get();
    }

    public long uptimeMillis() {
        return System.currentTimeMillis() - createdAtMillis;
    }

    /** Immutable snapshot for /mili perf and the bStats exporter. */
    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("region_id", regionId);
        out.put("label", RegionIdRegistry.labelOf(regionId));
        out.put("submitted", submitted.sum());
        out.put("executed", executed.sum());
        out.put("queued_now", queuedNow.get());
        out.put("in_flight_now", inFlightNow.get());
        out.put("merged", merged.sum());
        out.put("deferred", deferred.sum());
        out.put("rejected", rejected.sum());
        out.put("cancelled", cancelled.sum());
        out.put("timed_out", timedOut.sum());
        out.put("failed", failed.sum());
        out.put("inline_runs", inlineRuns.sum());
        out.put("avg_tick_ms", String.format("%.3f", averageTickMillis()));
        out.put("peak_tick_ms", String.format("%.3f", peakTickMillis()));
        out.put("budget_overshoots", budgetOvershoots.get());
        out.put("uptime_ms", uptimeMillis());
        return out;
    }

    @Override
    public String toString() {
        return "RegionMetrics[" + RegionIdRegistry.labelOf(regionId)
                + " sub=" + submitted.sum()
                + " exec=" + executed.sum()
                + " q=" + queuedNow.get()
                + " rej=" + rejected.sum()
                + " avgTick=" + String.format("%.2fms", averageTickMillis()) + "]";
    }
}
