package fun.bm.mili.scheduler;

import java.util.Collection;
import java.util.Map;

/**
 * Rebalances per-region tick budgets from observed debt (fix.md §20/§21).
 * <p>
 * The naive reaction to load is to slow the server down — raise
 * {@code TIME_BETWEEN_TICKS}, tick fewer entities. fix.md §20 rejects that
 * because it taxes every player for a problem caused by a few regions.
 *
 * <p>This controller instead moves the budget around:
 *
 * <pre>
 *     total budget available
 *           |
 *           +-- regions with low debt: give back some budget
 *           +-- regions with high debt: receive the surplus
 *           |
 *     nobody's tick frequency changes; the split does
 * </pre>
 *
 * <p>Budgets are clamped to {@code [min, max]} so a pathological region cannot
 * claim the whole allowance, and every region always keeps at least
 * {@code min} — a starved region that cannot finish a tick would only
 * accumulate more debt.
 *
 * <p>Reads are lock-free: each runtime owns its own budget and writes are single
 * volatile stores. The controller runs on the scheduler tick and is intentionally
 * cheap.
 */
public final class BudgetController {

    /** Floor: no region is ever given less than this, regardless of debt. */
    private static final long MIN_BUDGET_NANOS = 20_000_000L;   // 20ms

    /** Ceiling: half a tick. Even a desperate region cannot ask for more. */
    private static final long MAX_BUDGET_NANOS = 100_000_000L;  // 100ms

    /** Baseline handed to every region before rebalancing. */
    private static final long BASE_BUDGET_NANOS = 50_000_000L;  // 50ms

    /** How aggressively surplus is moved towards indebted regions, in [0, 1]. */
    private static final double TRANSFER_RATIO = 0.5;

    private static volatile long totalBudgetNanos = BASE_BUDGET_NANOS;
    private static volatile long lastRebalanceNanos;
    private static volatile int lastRebalancedRegions;
    private static volatile int lastDebtors;

    private BudgetController() {}

    /** Set the total allowance shared by all regions. Called from configuration. */
    public static void setTotalBudgetNanos(long nanos) {
        totalBudgetNanos = Math.max(MIN_BUDGET_NANOS, nanos);
    }

    public static void setBaseBudgetMillis(double millis) {
        setTotalBudgetNanos((long) (Math.max(0.1, millis) * 1_000_000.0));
    }

    public static long totalBudgetNanos() {
        return totalBudgetNanos;
    }

    public static long baseBudgetNanos() {
        return BASE_BUDGET_NANOS;
    }

    /**
     * Recompute every region's budget from its debt.
     *
     * @param runtimes the current runtimes; safe to call with an empty collection
     */
    public static void updateFrom(Collection<? extends RegionRuntime> runtimes) {
        if (runtimes == null || runtimes.isEmpty()) {
            lastRebalancedRegions = 0;
            lastDebtors = 0;
            return;
        }

        int count = runtimes.size();
        long base = Math.max(MIN_BUDGET_NANOS, totalBudgetNanos / count);

        // Pass 1: measure. Regions below the debt threshold keep the base budget
        // and donate the difference to the pool of surplus.
        double totalDebt = 0.0;
        int debtors = 0;
        for (RegionRuntime rt : runtimes) {
            double debt = rt.debt().debtTicks();
            if (debt > 0.25) {
                totalDebt += debt;
                debtors++;
            }
        }

        long donated = 0L;
        for (RegionRuntime rt : runtimes) {
            if (rt.debt().debtTicks() > 0.25) continue;
            donated += Math.max(0L, base - BASE_BUDGET_NANOS);
        }

        // Pass 2: assign. Debtors split the surplus in proportion to their debt;
        // everyone else gets the base.
        long transferable = (long) (donated * TRANSFER_RATIO);
        for (RegionRuntime rt : runtimes) {
            double debt = rt.debt().debtTicks();
            long assigned;
            if (debt > 0.25 && totalDebt > 0.0 && transferable > 0L) {
                assigned = base + (long) (transferable * (debt / totalDebt));
            } else {
                assigned = base;
            }
            assigned = Math.max(MIN_BUDGET_NANOS, Math.min(MAX_BUDGET_NANOS, assigned));

            RegionBudget budget = rt.budget();
            if (budget.budgetNanos() != assigned) {
                budget.setBudgetNanos(assigned);
                rt.debt().setTargetNanos(assigned);
            }
        }

        lastRebalanceNanos = System.nanoTime();
        lastRebalancedRegions = count;
        lastDebtors = debtors;
    }

    /** Nanos since the last rebalance, or {@code -1} if it never ran. */
    public static long nanosSinceLastRebalance() {
        long last = lastRebalanceNanos;
        return last == 0L ? -1L : System.nanoTime() - last;
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("total_budget_ms", String.format("%.2f", totalBudgetNanos / 1_000_000.0));
        out.put("base_budget_ms", String.format("%.2f", BASE_BUDGET_NANOS / 1_000_000.0));
        out.put("min_budget_ms", String.format("%.2f", MIN_BUDGET_NANOS / 1_000_000.0));
        out.put("max_budget_ms", String.format("%.2f", MAX_BUDGET_NANOS / 1_000_000.0));
        out.put("last_rebalanced_regions", lastRebalancedRegions);
        out.put("last_debtors", lastDebtors);
        long since = nanosSinceLastRebalance();
        out.put("millis_since_rebalance", since < 0 ? -1L : since / 1_000_000L);
        return out;
    }

    /** Reset for tests / shutdown. */
    public static void reset() {
        totalBudgetNanos = BASE_BUDGET_NANOS;
        lastRebalanceNanos = 0L;
        lastRebalancedRegions = 0;
        lastDebtors = 0;
    }
}
