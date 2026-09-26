package fun.bm.mili.scheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Budget-driven entity scheduling (fix.md §13).
 * <p>
 * Entities are not merely "ticked less"; they are scheduled against a per-region budget
 * that is itself derived from the region's tick budget:
 * <pre>
 *     Region Budget -> Entity Budget -> Entity Tasks (by priority)
 * </pre>
 * Classification is caller supplied so this class stays free of Minecraft types.
 */
public final class EntityScheduler {

    private EntityScheduler() {}

    /** Share of a region's tick budget handed to entity work. */
    private static volatile double entityBudgetShare = 0.6;

    private static final Map<Long, RegionEntityBudget> BUDGETS = new ConcurrentHashMap<>();

    /** Per-region entity budget, keyed by the stable region id (fix.md §8). */
    public static final class RegionEntityBudget {
        private final long regionId;
        private final AtomicLong totalBudgetNanos = new AtomicLong(0L);
        private final AtomicLongArray consumed = new AtomicLongArray(EntityPriority.values().length);
        private final AtomicLong ticked = new AtomicLong();
        private final AtomicLong skipped = new AtomicLong();
        private final AtomicLong removed = new AtomicLong();

        RegionEntityBudget(long regionId) {
            this.regionId = regionId;
        }

        public long regionId() { return regionId; }
        public long totalBudgetNanos() { return totalBudgetNanos.get(); }
        public long ticked() { return ticked.get(); }
        public long skipped() { return skipped.get(); }
        public long removed() { return removed.get(); }

        public long consumed(EntityPriority priority) {
            return consumed.get(priority.rank());
        }

        public long consumedTotal() {
            long total = 0L;
            for (int i = 0; i < consumed.length(); i++) total += consumed.get(i);
            return total;
        }

        public long remaining() {
            return Math.max(0L, totalBudgetNanos.get() - consumedTotal());
        }

        /** Begin a tick: reset counters and derive the budget from the region's tick budget. */
        public void beginTick(long regionTickBudgetNanos) {
            long budget = (long) (regionTickBudgetNanos * entityBudgetShare);
            totalBudgetNanos.set(Math.max(0L, budget));
            for (int i = 0; i < consumed.length(); i++) consumed.set(i, 0L);
            ticked.set(0L);
            skipped.set(0L);
            removed.set(0L);
        }

        /**
         * Try to reserve {@code estimatedNanos} for an entity of the given priority.
         * Each priority class is additionally capped at its own share so a flood of
         * LOW-priority far-away AI can never starve HIGH-priority work.
         */
        public boolean tryConsume(EntityPriority priority, long estimatedNanos) {
            long perClassCap = (long) (totalBudgetNanos.get() * priority.budgetShare());
            if (consumed.get(priority.rank()) + estimatedNanos > perClassCap) return false;
            if (consumedTotal() + estimatedNanos > totalBudgetNanos.get()) return false;
            consumed.addAndGet(priority.rank(), estimatedNanos);
            return true;
        }

        public void recordTicked() { ticked.incrementAndGet(); }
        public void recordSkipped() { skipped.incrementAndGet(); }
        public void recordRemoved() { removed.incrementAndGet(); }
    }

    public static RegionEntityBudget budgetFor(long regionId) {
        return BUDGETS.computeIfAbsent(regionId, RegionEntityBudget::new);
    }

    public static void beginTick(long regionId, long regionTickBudgetNanos) {
        budgetFor(regionId).beginTick(regionTickBudgetNanos);
    }

    /**
     * Decide what to do with an entity.
     * <p>
     * Returns one of {@link EntityTickDecision#TICK}, {@link EntityTickDecision#SKIP} or
     * {@link EntityTickDecision#REMOVE} with zero allocation (fix.md §12).
     *
     * @param regionId        stable region id
     * @param priority        classification of this entity
     * @param throttlerVerdict verdict from the Kaiiju throttler (0 = tick, 1 = skip, 2 = remove)
     */
    public static int decide(long regionId, EntityPriority priority, int throttlerVerdict) {
        if (throttlerVerdict == EntityTickDecision.REMOVE) {
            budgetFor(regionId).recordRemoved();
            return EntityTickDecision.REMOVE;
        }
        if (throttlerVerdict == EntityTickDecision.SKIP) {
            budgetFor(regionId).recordSkipped();
            return EntityTickDecision.SKIP;
        }
        RegionEntityBudget budget = budgetFor(regionId);
        if (!budget.tryConsume(priority, averageEntityNanos())) {
            budget.recordSkipped();
            return EntityTickDecision.SKIP;
        }
        budget.recordTicked();
        return EntityTickDecision.TICK;
    }

    /** Classify an entity from facts the caller already has. */
    public static EntityPriority classify(boolean nearPlayer, boolean redstoneRelated, boolean farAway) {
        if (nearPlayer || redstoneRelated) return EntityPriority.HIGH;
        if (farAway) return EntityPriority.LOW;
        return EntityPriority.NORMAL;
    }

    public static void setEntityBudgetShare(double share) {
        entityBudgetShare = Math.max(0.05, Math.min(1.0, share));
    }

    public static double entityBudgetShare() {
        return entityBudgetShare;
    }

    public static void release(long regionId) {
        BUDGETS.remove(regionId);
    }

    public static void clear() {
        BUDGETS.clear();
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("tracked_regions", BUDGETS.size());
        stats.put("budget_share", entityBudgetShare);
        long ticked = 0, skipped = 0, removed = 0;
        for (RegionEntityBudget budget : BUDGETS.values()) {
            ticked += budget.ticked();
            skipped += budget.skipped();
            removed += budget.removed();
        }
        stats.put("ticked", ticked);
        stats.put("skipped", skipped);
        stats.put("removed", removed);
        return stats;
    }

    /** Rough per-entity cost estimate used to conserve the budget. */
    private static long averageEntityNanos() {
        return 20_000L; // 20us; refined at runtime by callers that measure
    }
}
