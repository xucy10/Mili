package fun.bm.mili.scheduler;

/**
 * A region as a <i>stateful scheduling unit</i> rather than a thread.
 * <p>
 * fix.md §18 and §22.1: "Region &ne; Thread".  A region is a workload with an identity,
 * a lifecycle state, a budget, a queue, metrics and a debt account.  Workers are a
 * separate resource owned by the scheduler.
 */
public interface RegionRuntime {

    /** Stable identifier shared by metrics / scheduler / registry / cross-region / entity budget. */
    long regionId();

    /** The underlying region object (opaque to the scheduler). */
    Object region();

    /** Lifecycle state (fix.md §9). */
    RegionState state();

    /** Computation budget for the current tick (fix.md §15). */
    RegionBudget budget();

    /** Pending work for this region (fix.md §17). */
    RegionWorkQueue queue();

    /** Counters keyed by the stable region id (fix.md §8). */
    RegionMetrics metrics();

    /** Accumulated scheduling debt (fix.md §16). */
    SchedulerDebt debt();

    /** Priority of this region right now: load factor + debt boost. */
    default double priority() {
        double load = budget().utilisation();
        return load + debt().priorityBoost();
    }
}
