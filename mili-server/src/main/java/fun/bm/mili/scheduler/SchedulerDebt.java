package fun.bm.mili.scheduler;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-region scheduling debt.
 * <p>
 * fix.md §16: catch-up must stop being "run a few extra ticks" and become
 * "prioritise the regions that owe time".  Each region tracks how far its real tick
 * duration has drifted from its target:
 * <pre>
 *     Target = 50ms
 *     Tick 1: actual 60ms -> debt += 10ms
 *     Tick 2: actual 45ms -> debt += -5ms
 * </pre>
 * The scheduler converts debt into a priority boost:
 * <pre>
 *     debt up   -> priority up
 *     debt down -> priority down
 * </pre>
 * <p>
 * Debt is clamped so a single catastrophic tick cannot permanently starve every
 * other region.
 */
public final class SchedulerDebt {

    /** Default tick target: 50ms (20 TPS). */
    public static final long DEFAULT_TARGET_NANOS = 50_000_000L;
    /** Hard clamp on accumulated debt: 10 ticks worth of target time. */
    public static final long MAX_DEBT_NANOS = 500_000_000L;

    private final AtomicLong targetTickNanos;
    private final AtomicLong deadlineNanos = new AtomicLong(0L);
    private final AtomicLong accumulatedDebtNanos = new AtomicLong(0L);
    private final AtomicLong overdrawnTicks = new AtomicLong(0L);

    public SchedulerDebt() {
        this(DEFAULT_TARGET_NANOS);
    }

    public SchedulerDebt(long targetTickNanos) {
        this.targetTickNanos = new AtomicLong(Math.max(1L, targetTickNanos));
    }

    public long targetTickNanos() {
        return targetTickNanos.get();
    }

    public void setTargetTickNanos(long nanos) {
        targetTickNanos.set(Math.max(1L, nanos));
    }

    /** Absolute deadline of the current tick window; 0 when no tick is scheduled. */
    public long deadlineNanos() {
        return deadlineNanos.get();
    }

    public void setDeadlineNanos(long nanos) {
        deadlineNanos.set(nanos);
    }

    public long accumulatedDebtNanos() {
        return accumulatedDebtNanos.get();
    }

    /** Consecutive ticks that exceeded their budget. */
    public long overdrawnTicks() {
        return overdrawnTicks.get();
    }

    /**
     * Record a completed tick.
     *
     * @param actualNanos measured tick duration
     * @return the debt after this tick
     */
    public long recordTick(long actualNanos) {
        long delta = actualNanos - targetTickNanos.get();
        if (delta > 0) {
            overdrawnTicks.incrementAndGet();
        } else {
            overdrawnTicks.set(0L);
        }
        return addDebt(delta);
    }

    /** Consume (pay back) debt when a region gets extra execution time. */
    public long payDown(long nanos) {
        return addDebt(-nanos);
    }

    private long addDebt(long deltaNanos) {
        long now = System.nanoTime();
        while (true) {
            long current = accumulatedDebtNanos.get();
            long next = current + deltaNanos;
            if (next > MAX_DEBT_NANOS) next = MAX_DEBT_NANOS;
            if (next < -MAX_DEBT_NANOS) next = -MAX_DEBT_NANOS;
            if (accumulatedDebtNanos.compareAndSet(current, next)) {
                deadlineNanos.set(now + targetTickNanos.get());
                return next;
            }
        }
    }

    /** Debt expressed as a 0.0 ~ 1.0 factor (0 = paid up, 1 = fully overdrawn). */
    public double debtFactor() {
        long debt = accumulatedDebtNanos.get();
        if (debt <= 0) return 0.0;
        return Math.min(1.0, (double) debt / (double) MAX_DEBT_NANOS);
    }

    /** Priority boost derived from debt, in the same unit as {@link RegionLoadMonitor}-style priorities. */
    public double priorityBoost() {
        return debtFactor() * 0.5;
    }

    public void reset() {
        accumulatedDebtNanos.set(0L);
        overdrawnTicks.set(0L);
        deadlineNanos.set(0L);
    }
}
