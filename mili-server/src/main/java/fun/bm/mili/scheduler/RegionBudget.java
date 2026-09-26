package fun.bm.mili.scheduler;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Execution budget for a region in one tick.
 * <p>
 * fix.md §15: the governor's job is to control the <b>computation budget</b>, not to
 * lower the tick rate.  Under load we shrink {@code 50ms -> 48ms -> 45ms} of allowed
 * work per tick; we do not stretch {@code TIME_BETWEEN_TICKS} to 60ms, which would
 * simply convert load into lost TPS.
 */
public final class RegionBudget {

    public static final long NORMAL_BUDGET_NANOS = 50_000_000L;
    public static final long PRESSURE_BUDGET_NANOS = 48_000_000L;
    public static final long HIGH_PRESSURE_BUDGET_NANOS = 45_000_000L;

    private final AtomicLong budgetNanos = new AtomicLong(NORMAL_BUDGET_NANOS);
    private final AtomicLong usedNanos = new AtomicLong(0L);

    public RegionBudget() {
        this(NORMAL_BUDGET_NANOS);
    }

    public RegionBudget(long budgetNanos) {
        this.budgetNanos.set(Math.max(1L, budgetNanos));
    }

    public long budgetNanos() {
        return budgetNanos.get();
    }

    public void setBudgetNanos(long nanos) {
        budgetNanos.set(Math.max(1L, nanos));
    }

    public long usedNanos() {
        return usedNanos.get();
    }

    /** Reset the consumed counter at the start of every tick. */
    public void beginTick() {
        usedNanos.set(0L);
    }

    /** Charge consumed time against the budget. */
    public void consume(long nanos) {
        usedNanos.addAndGet(Math.max(0L, nanos));
    }

    public long remainingNanos() {
        return Math.max(0L, budgetNanos.get() - usedNanos.get());
    }

    public boolean exhausted() {
        return usedNanos.get() >= budgetNanos.get();
    }

    /** 0.0 ~ 1.0 (or more when overdrawn). */
    public double utilisation() {
        return (double) usedNanos.get() / (double) budgetNanos.get();
    }
}
