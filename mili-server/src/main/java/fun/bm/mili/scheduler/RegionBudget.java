package fun.bm.mili.scheduler;

/**
 * Per-tick compute budget of a region (fix.md §20/§21).
 * <p>
 * fix.md §20 explicitly rejects the "the server is overloaded, so lower the tick
 * frequency" reflex: that degrades every player equally and turns a scheduling
 * problem into a gameplay problem. Instead the region keeps a fixed time budget
 * per tick and the scheduler decides <em>what fits inside it</em>:
 *
 * <pre>
 *     budget 50ms
 *       +-- region-owned game logic   (always, it is the server's actual job)
 *       +-- queued scheduler tasks    (while budget remains)
 *       +--- entity ticks             (what is left over, weighted by priority)
 * </pre>
 *
 * <p>The budget is per-tick and strictly non-carrying: whatever is not spent is
 * not hoarded, because overspending one tick cannot buy back the next one. Debt
 * from overspending is tracked separately by {@link SchedulerDebt}.
 *
 * <p>Not thread-safe by design: a budget belongs to exactly one region, and is
 * only ever touched by that region's owning thread.
 */
public final class RegionBudget {

    /** Fallback budget when nothing has been configured: 50ms per tick. */
    public static final long DEFAULT_BUDGET_NANOS = 50_000_000L;

    /** Region-owned game logic is never starved below this share of the budget. */
    private static final double GAME_LOGIC_FLOOR_RATIO = 0.5;

    private long budgetNanos;
    private long spentNanos;
    private long gameLogicNanos;

    public RegionBudget() {
        this(DEFAULT_BUDGET_NANOS);
    }

    public RegionBudget(long budgetNanos) {
        this.budgetNanos = Math.max(1L, budgetNanos);
    }

    /** Start a new tick: clear the spend counters. */
    public void beginTick() {
        this.spentNanos = 0L;
        this.gameLogicNanos = 0L;
    }

    /** Charge time spent by the region's own game logic (Folia's tick). */
    public void chargeGameLogic(long nanos) {
        long n = Math.max(0L, nanos);
        this.gameLogicNanos += n;
        this.spentNanos += n;
    }

    /** Charge time spent running scheduler tasks. */
    public void charge(long nanos) {
        this.spentNanos += Math.max(0L, nanos);
    }

    /** Nanoseconds still available for scheduler work this tick. */
    public long remainingNanos() {
        return Math.max(0L, budgetNanos - spentNanos);
    }

    /**
     * Budget still available for <em>optional</em> work (entity ticks, deferred
     * tasks). Stricter than {@link #remainingNanos()}: it first reserves
     * {@link #GAME_LOGIC_FLOOR_RATIO} of the budget so that a burst of optional
     * work can never crowd out the region's real job next tick.
     */
    public long optionalNanos() {
        long floor = (long) (budgetNanos * GAME_LOGIC_FLOOR_RATIO);
        return Math.max(0L, budgetNanos - Math.max(floor, spentNanos));
    }

    public boolean exhausted() {
        return spentNanos >= budgetNanos;
    }

    /** Whether there is room for a task estimated to cost {@code estimatedNanos}. */
    public boolean canAfford(long estimatedNanos) {
        return remainingNanos() >= Math.max(0L, estimatedNanos);
    }

    /** Milliseconds already spent this tick. */
    public double spentMillis() {
        return spentNanos / 1_000_000.0;
    }

    public double gameLogicMillis() {
        return gameLogicNanos / 1_000_000.0;
    }

    public long budgetNanos() {
        return budgetNanos;
    }

    /**
     * Adjust the per-tick budget (for example when {@link BudgetController}
     * observes sustained debt, or a region is rebalanced).
     */
    public void setBudgetNanos(long nanos) {
        this.budgetNanos = Math.max(1L, nanos);
    }

    /** Set the budget from the configured per-tick millisecond allowance. */
    public void setBudgetMillis(double millis) {
        setBudgetNanos((long) (Math.max(0.1, millis) * 1_000_000.0));
    }

    public double budgetMillis() {
        return budgetNanos / 1_000_000.0;
    }

    @Override
    public String toString() {
        return String.format("RegionBudget[budget=%.2fms spent=%.2fms remaining=%.2fms]",
                budgetMillis(), spentMillis(), remainingNanos() / 1_000_000.0);
    }
}
