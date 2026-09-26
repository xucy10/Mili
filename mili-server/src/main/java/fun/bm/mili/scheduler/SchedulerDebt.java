package fun.bm.mili.scheduler;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Scheduler debt of a region (fix.md §21).
 * <p>
 * When a region overruns its budget the temptation is to add a "catch-up tick".
 * fix.md §21 rejects that as a patch rather than a policy: it turns one slow tick
 * into a burst of double work, which tends to cascade.
 *
 * <p>Instead the overrun is accumulated as debt, and the scheduler uses it as a
 * priority signal:
 *
 * <pre>
 *     target = 50ms
 *     tick 1: 60ms actual -> debt +10ms
 *     tick 2: 45ms actual -> debt  +5ms
 *     ...
 *     debt rising -> the region is scheduled earlier and gets a larger budget share
 * </pre>
 *
 * <p>Debt decays while a region keeps up, and is bounded so that a region that
 * was starved for a while cannot accumulate an unbounded claim on the pool and
 * then monopolise it.
 *
 * <p><b>Thread safety:</b> the counters are atomics because the region's own
 * thread records ticks while the balancer thread reads debt for prioritisation
 * decisions. Every mutation is a single atomic add, so lock-free reads are
 * accurate enough for scheduling.
 */
public final class SchedulerDebt {

    /** Debt is capped at this many ticks' worth of budget, to avoid runaway claims. */
    private static final double MAX_DEBT_TICKS = 8.0;

    /** Fraction of the debt repaid by one on-time tick. */
    private static final double REPAY_RATIO = 0.5;

    /** Debt above this (in ticks) counts as heavily behind. */
    private static final double HEAVY_DEBT_TICKS = 2.0;

    private final AtomicLong accumulatedDebtNanos = new AtomicLong(0);
    private final AtomicLong ticksObserved = new AtomicLong(0);
    private final AtomicLong overrunTicks = new AtomicLong(0);

    private volatile long targetNanos = RegionBudget.DEFAULT_BUDGET_NANOS;

    public void setTargetNanos(long nanos) {
        this.targetNanos = Math.max(1L, nanos);
    }

    public long targetNanos() {
        return targetNanos;
    }

    /**
     * Record one region tick.
     *
     * @param actualNanos wall time the tick took
     */
    public void recordTick(long actualNanos) {
        long actual = Math.max(0L, actualNanos);
        long target = targetNanos;
        ticksObserved.incrementAndGet();

        if (actual > target) {
            overrunTicks.incrementAndGet();
            long cap = (long) (target * MAX_DEBT_TICKS);
            accumulatedDebtNanos.updateAndGet(prev -> Math.min(cap, prev + (actual - target)));
        } else {
            long headroom = target - actual;
            accumulatedDebtNanos.updateAndGet(prev -> {
                long repay = (long) (headroom * REPAY_RATIO);
                return Math.max(0L, prev - repay);
            });
        }
    }

    /** Current debt in nanoseconds. */
    public long debtNanos() {
        return accumulatedDebtNanos.get();
    }

    /** Current debt expressed in ticks of the target budget. */
    public double debtTicks() {
        return debtNanos() / (double) targetNanos;
    }

    /** Whether this region is meaningfully behind. */
    public boolean isHeavilyBehind() {
        return debtTicks() >= HEAVY_DEBT_TICKS;
    }

    /**
     * Priority boost in the range {@code [0.0, 1.0]} derived from debt.
     * <p>
     * Consumed by the balancer's priority scoring: a debt-free region scores 0,
     * a region at the cap scores 1. This is intentionally linear — a step
     * function would make regions oscillate between starving and hogging.
     */
    public double priorityBoost() {
        double ticks = debtTicks();
        if (ticks <= 0.0) return 0.0;
        return Math.min(1.0, ticks / MAX_DEBT_TICKS);
    }

    /** Ticks observed since the last {@link #reset()}. */
    public long ticksObserved() {
        return ticksObserved.get();
    }

    /** Ticks that overran the target. */
    public long overrunTicks() {
        return overrunTicks.get();
    }

    /** Fraction of observed ticks that overran the target. */
    public double overrunRatio() {
        long total = ticksObserved.get();
        return total == 0L ? 0.0 : overrunTicks.get() / (double) total;
    }

    /** Clear debt and counters — used when a region is rebalanced or recreated. */
    public void reset() {
        accumulatedDebtNanos.set(0L);
        ticksObserved.set(0L);
        overrunTicks.set(0L);
    }

    @Override
    public String toString() {
        return String.format("SchedulerDebt[debt=%.2fticks overrun=%.1f%%]",
                debtTicks(), overrunRatio() * 100.0);
    }
}
