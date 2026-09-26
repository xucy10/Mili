package fun.bm.mili.scheduler;

/**
 * Scheduling priority for an entity.
 * <p>
 * fix.md §13: entities should not simply be "ticked less" — they are scheduled against
 * a budget by priority:
 * <pre>
 *     player-near entity     -> HIGH
 *     redstone-related entity-> HIGH
 *     normal entity          -> NORMAL
 *     far-away AI            -> LOW
 * </pre>
 */
public enum EntityPriority {
    HIGH(0),
    NORMAL(1),
    LOW(2);

    private final int rank;

    EntityPriority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    /** Higher weight wins the budget. */
    public double weight() {
        return switch (this) {
            case HIGH -> 3.0;
            case NORMAL -> 1.0;
            case LOW -> 0.25;
        };
    }

    /** Fraction of the region's entity budget this class is allowed to consume. */
    public double budgetShare() {
        return switch (this) {
            case HIGH -> 0.55;
            case NORMAL -> 0.35;
            case LOW -> 0.10;
        };
    }
}
