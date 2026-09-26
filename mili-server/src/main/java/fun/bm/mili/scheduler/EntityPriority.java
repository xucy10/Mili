package fun.bm.mili.scheduler;

/**
 * Scheduling priority of an entity, as proposed in fix.md §11.
 * <p>
 * The point of the enum is that entities are no longer "just ticked less"; the
 * scheduler grants each entity compute from a {@link RegionBudget} according to
 * this priority. The numeric {@link #weight()} is what {@link EntityScheduler}
 * actually uses, so the hot path never switches on the enum itself.
 */
public enum EntityPriority {

    /** Entities near a player, and redstone-relevant entities. Never starved. */
    HIGH(3),
    /** Ordinary entities. */
    NORMAL(2),
    /** Distant or low-activity AI entities. First to lose budget. */
    LOW(1),
    /** Entities already marked for removal; they only need a cheap tick to finish dying. */
    DYING(0);

    private final int weight;

    EntityPriority(int weight) {
        this.weight = weight;
    }

    /** Relative share of the region's entity budget. */
    public int weight() {
        return weight;
    }

    /** Whether an entity at this priority may ever be removed by budget pressure. */
    public boolean isRemovable() {
        return this == LOW || this == DYING;
    }

    public static EntityPriority ofName(String name) {
        if (name == null) return NORMAL;
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "high" -> HIGH;
            case "low" -> LOW;
            case "dying" -> DYING;
            default -> NORMAL;
        };
    }
}
