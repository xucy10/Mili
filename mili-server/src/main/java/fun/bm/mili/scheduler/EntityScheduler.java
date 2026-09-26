package fun.bm.mili.scheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-region entity compute scheduler (fix.md §11).
 * <p>
 * The old model was a throttle: an entity limit per class, and entities beyond
 * it simply did not run. That answers "how do we do less work" but not "which
 * work matters", so a distant cow and a redstone-adjacent item entity were
 * equally likely to be skipped.
 *
 * <p>Here the region gets an entity budget and each entity draws from it:
 *
 * <pre>
 *     Entity -> EntityPriority -> RegionBudget -> EntityScheduler -> decision
 * </pre>
 *
 * <ul>
 *   <li>{@code HIGH} (near player, redstone-relevant) is never skipped while any
 *       budget remains, and may dip into a small overdraft because losing one of
 *       these ticks is visible to a player;</li>
 *   <li>{@code NORMAL} is skipped only once the budget is spent;</li>
 *   <li>{@code LOW} is the first to be skipped;</li>
 *   <li>{@code REMOVE} is only ever emitted when removal is explicitly enabled —
 *       see {@link #setRemovalEnabled(boolean)}. Default is off, because
 *       scheduling pressure must not silently change the world.</li>
 * </ul>
 *
 * <p>Instances live one per region and are released with the region
 * (fix.md §14): {@link #release(long)} is wired into the destroy sequence, so a
 * long-running server does not accumulate one scheduler per region that ever
 * existed.
 */
public final class EntityScheduler {

    private static final ConcurrentHashMap<Long, EntityScheduler> BY_REGION = new ConcurrentHashMap<>();

    /** Overdraft granted to HIGH priority work, as a fraction of the budget. */
    private static final double HIGH_PRIORITY_OVERDRAFT = 0.25;

    /** Removal only becomes eligible this far past the budget. */
    private static final double REMOVAL_THRESHOLD = 2.0;

    private final long regionId;

    private final LongAdder ticksGranted = new LongAdder();
    private final LongAdder ticksSkipped = new LongAdder();
    private final LongAdder removalsSuggested = new LongAdder();
    private final LongAdder overdraftsTaken = new LongAdder();

    private volatile boolean removalEnabled;
    private volatile long budgetNanosPerTick = RegionBudget.DEFAULT_BUDGET_NANOS;
    private volatile long spentNanos;

    private EntityScheduler(long regionId) {
        this.regionId = regionId;
    }

    /** Look up (or create) the scheduler for a region. */
    public static EntityScheduler forRegion(long regionId) {
        return BY_REGION.computeIfAbsent(regionId, EntityScheduler::new);
    }

    /** Look up without creating. */
    public static EntityScheduler peek(long regionId) {
        return BY_REGION.get(regionId);
    }

    /** Drop the scheduler for a region. Part of the region destroy sequence. */
    public static void release(long regionId) {
        BY_REGION.remove(regionId);
    }

    public static int trackedRegions() {
        return BY_REGION.size();
    }

    public static void clear() {
        BY_REGION.clear();
    }

    /**
     * Whether budget pressure may ever recommend removing an entity.
     * <p>
     * Off by default: removing entities to hit a time budget is a gameplay
     * decision, not a scheduling one, so it must be turned on deliberately.
     */
    public void setRemovalEnabled(boolean enabled) {
        this.removalEnabled = enabled;
    }

    public boolean isRemovalEnabled() {
        return removalEnabled;
    }

    /** Start a new region tick with the given entity budget. */
    public void beginTick(long budgetNanos) {
        this.budgetNanosPerTick = Math.max(1L, budgetNanos);
        this.spentNanos = 0L;
    }

    /** Charge time spent ticking an entity. */
    public void recordSpent(long nanos) {
        if (nanos > 0L) {
            spentNanos += nanos;
        }
    }

    /**
     * Decide what to do with one entity.
     *
     * @param priority       scheduling priority of the entity
     * @param estimatedNanos expected cost of ticking it
     * @return one of {@link EntityTickDecision#TICK}, {@link EntityTickDecision#SKIP},
     *         {@link EntityTickDecision#REMOVE} — an {@code int}, never an object
     */
    public int decide(EntityPriority priority, long estimatedNanos) {
        if (priority == null) priority = EntityPriority.NORMAL;

        long budget = budgetNanosPerTick;
        long cost = Math.max(0L, estimatedNanos);
        long overdraftLimit = (long) (budget * (1.0 + HIGH_PRIORITY_OVERDRAFT));

        // HIGH priority work is protected: it may run into a bounded overdraft,
        // because dropping a tick for an entity a player is watching is exactly
        // the kind of "optimisation" that reads as a bug.
        if (priority == EntityPriority.HIGH) {
            if (spentNanos + cost <= overdraftLimit) {
                if (spentNanos >= budget) overdraftsTaken.increment();
                ticksGranted.increment();
                return EntityTickDecision.TICK;
            }
            ticksSkipped.increment();
            return EntityTickDecision.SKIP;
        }

        if (spentNanos + cost <= budget) {
            ticksGranted.increment();
            return EntityTickDecision.TICK;
        }

        // Over budget. Is this entity allowed to be removed instead of ticked?
        if (removalEnabled && priority.isRemovable()
                && spentNanos > (long) (budget * REMOVAL_THRESHOLD)) {
            removalsSuggested.increment();
            return EntityTickDecision.REMOVE;
        }

        ticksSkipped.increment();
        return EntityTickDecision.SKIP;
    }

    /**
     * Convenience overload that charges the cost of the previous entity
     * immediately, so the call site does not have to.
     */
    public int decideAndCharge(EntityPriority priority, long previousCostNanos) {
        recordSpent(previousCostNanos);
        return decide(priority, 0L);
    }

    public long ticksGranted() {
        return ticksGranted.sum();
    }

    public long ticksSkipped() {
        return ticksSkipped.sum();
    }

    public long removalsSuggested() {
        return removalsSuggested.sum();
    }

    public long overdraftsTaken() {
        return overdraftsTaken.sum();
    }

    /** Fraction of decisions that skipped the entity. */
    public double skipRatio() {
        long total = ticksGranted.sum() + ticksSkipped.sum();
        return total == 0L ? 0.0 : ticksSkipped.sum() / (double) total;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("region_id", regionId);
        out.put("entity_budget_ms", String.format("%.2f", budgetNanosPerTick / 1_000_000.0));
        out.put("entity_spent_ms", String.format("%.2f", spentNanos / 1_000_000.0));
        out.put("ticks_granted", ticksGranted.sum());
        out.put("ticks_skipped", ticksSkipped.sum());
        out.put("skip_ratio", String.format("%.3f", skipRatio()));
        out.put("overdrafts_taken", overdraftsTaken.sum());
        out.put("removals_suggested", removalsSuggested.sum());
        out.put("removal_enabled", removalEnabled);
        return out;
    }

    @Override
    public String toString() {
        return "EntityScheduler[" + RegionIdRegistry.labelOf(regionId)
                + " granted=" + ticksGranted.sum()
                + " skipped=" + ticksSkipped.sum() + "]";
    }
}
