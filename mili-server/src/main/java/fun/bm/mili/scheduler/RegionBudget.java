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
    /** Mili start - fix: monotonic tick counter, see {@link #beginTick(long)}. */
    private final AtomicLong tickEpoch = new AtomicLong(0L);

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

    /**
     * Open the tick with the given epoch, resetting the consumed counter.
     * <p>
     * Mili start - fix: the tick boundary used to be a no-argument {@code beginTick()} that
     * any thread could call, and the background {@code Mili-Scheduler-Driver} thread called
     * it for every region every 50&nbsp;ms.  A 50&nbsp;ms scheduler round is <b>not</b> a
     * region tick boundary: it could clear {@code usedNanos} while the region thread was
     * still inside its tick, so work done in that tick was never charged and
     * {@link #exhausted()} could never trip.
     * <p>
     * The boundary is now expressed as an epoch.  Only a strictly newer epoch resets the
     * counter, so the call is idempotent within a tick and a duplicate or stale call from
     * another thread becomes a harmless no-op instead of a cross-thread write.
     * <p>
     * Invariant: {@link #consume(long)} may only be called on the owning region thread and
     * only after a successful {@code beginTick(epoch)} for the current tick.
     *
     * @param epoch monotonically increasing tick number of the owning region
     * @return {@code true} if this call performed the reset
     */
    public boolean beginTick(long epoch) {
        for (;;) {
            long seen = tickEpoch.get();
            if (epoch <= seen) return false;
            if (tickEpoch.compareAndSet(seen, epoch)) {
                usedNanos.set(0L);
                return true;
            }
        }
    }

    /** Highest tick epoch accepted by {@link #beginTick(long)}. */
    public long tickEpoch() {
        return tickEpoch.get();
    }

    /**
     * Charge consumed time against the budget.
     * <p>
     * Must be called on the region's owning thread, within the tick opened by
     * {@link #beginTick(long)}.  Charging the same work twice (for example per task
     * <i>and</i> for the whole drain window) inflates {@link #utilisation()} and makes the
     * PI controller shrink the budget for no reason.
     */
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
