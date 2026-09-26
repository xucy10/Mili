package fun.bm.mili.scheduler;

import java.util.Map;

/**
 * Everything the scheduler knows about one region (fix.md §18).
 * <p>
 * Deliberately Minecraft-free: the region is an opaque {@code Object} handle and
 * all state visible here is plain Java. That keeps the scheduling model testable
 * on its own, and it forces every place that <em>does</em> need Minecraft types
 * to live in an explicit adapter rather than leaking into the core.
 *
 * <p>fix.md §18 asks for exactly this as a stepping stone: Mili does not tear out
 * the Folia scheduler in one move, it first puts an abstraction in front of it so
 * the eventual migration has somewhere to land.
 */
public interface RegionRuntime {

    /** Stable id from {@link RegionIdRegistry}. Never {@link RegionIdRegistry#UNKNOWN}. */
    long regionId();

    /** The opaque region handle this runtime was created for. */
    Object region();

    RegionState state();

    /** Per-tick compute budget. Owned by the region's thread; not thread-safe. */
    RegionBudget budget();

    /** Accumulated scheduling debt and the priority boost derived from it. */
    SchedulerDebt debt();

    /** Bounded work queue with the fix.md §3 backpressure policy. */
    RegionWorkQueue queue();

    /** Per-region counters. */
    RegionMetrics metrics();

    /**
     * Whether the region's owning thread is the caller.
     * <p>
     * Convenience delegate to {@link RegionOwnership#isOwnedByCurrentThread(Object)}
     * so call sites read as "does this region belong to me?" rather than
     * reaching into a static helper.
     */
    default boolean isOwnedByCurrentThread() {
        return RegionOwnership.isOwnedByCurrentThread(region());
    }

    /** Record that the region completed a tick. */
    void markTicked();

    /** Nanos since the last {@link #markTicked()}. */
    long nanosSinceLastTick();

    /** Whether the region may still accept new work. */
    default boolean acceptsWork() {
        return state().acceptsWork();
    }

    /** Whether work already queued may still be executed. */
    default boolean acceptsExecution() {
        return state().acceptsExecution();
    }

    /** Priority score in {@code [0, 1]} combining debt with how long the region has waited. */
    double priorityScore();

    /** Immutable snapshot for diagnostics. */
    Map<String, Object> snapshot();

    @Override
    String toString();
}
