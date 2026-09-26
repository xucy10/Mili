package fun.bm.mili.scheduler;

import java.util.Collection;

/**
 * Converts server load into a per-region <b>computation budget</b>.
 * <p>
 * fix.md §15: the old governor mapped load onto {@code TIME_BETWEEN_TICKS}, which turns
 * high load into lost TPS:
 * <pre>
 *     load up -> tick interval up -> TPS down
 * </pre>
 * The budget controller instead keeps the tick cadence and shrinks how much work each
 * tick is allowed to do:
 * <pre>
 *     normal        -> 50ms
 *     pressure      -> 48ms
 *     high pressure -> 45ms
 * </pre>
 * A PI (proportional-integral) controller drives the value so it converges instead of
 * oscillating around a threshold.
 */
public final class BudgetController {

    private BudgetController() {}

    /** Tick budget bounds, in nanoseconds. */
    private static final long MIN_BUDGET_NANOS = RegionBudget.HIGH_PRESSURE_BUDGET_NANOS;
    private static final long MAX_BUDGET_NANOS = RegionBudget.NORMAL_BUDGET_NANOS;

    private static final double KP = 0.35;
    private static final double KI = 0.08;

    private static double integral = 0.0;
    private static volatile long currentBudgetNanos = RegionBudget.NORMAL_BUDGET_NANOS;
    private static volatile double lastLoadFactor = 0.0;

    /**
     * Feed the measured load and receive the budget every region should use.
     *
     * @param loadFactor 0.0 = idle, 1.0 = at or over the tick target
     */
    public static synchronized long update(double loadFactor) {
        lastLoadFactor = loadFactor;
        // Error > 0 means we are over budget and must shrink the allowance.
        double error = loadFactor - 0.8;

        integral = clamp(integral + error, -2.0, 2.0);
        double adjustment = (KP * error + KI * integral) * (double) MAX_BUDGET_NANOS;

        long next = (long) ((double) MAX_BUDGET_NANOS - adjustment);
        next = Math.max(MIN_BUDGET_NANOS, Math.min(MAX_BUDGET_NANOS, next));
        currentBudgetNanos = next;
        return next;
    }

    /**
     * Apply the current budget to every region runtime.
     * <p>
     * Mili start - fix: this used to call {@code runtime.budget().beginTick()} as well.
     * It is invoked from the background {@code Mili-Scheduler-Driver} thread every 50&nbsp;ms,
     * so it reset the consumed counter of every region from a thread that owns none of
     * them — and 50&nbsp;ms is not a region tick boundary.  The tick boundary now belongs to
     * {@link RegionBudget#beginTick(long)}, which only the region's own tick may open; here
     * we only publish the new allowance.
     */
    public static void apply(Collection<MiliRegionRuntime> runtimes) {
        long budget = currentBudgetNanos;
        for (MiliRegionRuntime runtime : runtimes) {
            runtime.budget().setBudgetNanos(budget);
        }
    }

    /**
     * Recompute the budget from what the regions actually consumed last tick and push it
     * out to them.
     */
    public static void updateFrom(Collection<MiliRegionRuntime> runtimes) {
        double worst = 0.0;
        for (MiliRegionRuntime runtime : runtimes) {
            double utilisation = runtime.budget().utilisation();
            if (utilisation > worst) worst = utilisation;
        }
        long budget = update(worst);
        for (MiliRegionRuntime runtime : runtimes) {
            runtime.budget().setBudgetNanos(budget);
        }
    }

    public static long currentBudgetNanos() {
        return currentBudgetNanos;
    }

    public static double lastLoadFactor() {
        return lastLoadFactor;
    }

    public static synchronized void reset() {
        integral = 0.0;
        currentBudgetNanos = MAX_BUDGET_NANOS;
        lastLoadFactor = 0.0;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
