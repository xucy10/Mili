package fun.bm.mili.scheduler;

/**
 * Zero-allocation result of an entity tick decision (fix.md §10).
 * <p>
 * The old hot path allocated one {@code new EntityThrottlerReturn()} per entity
 * per tick, plus a capturing lambda, just to answer three booleans. Under a few
 * thousand entities that is a sustained short-lived-object flood feeding the GC
 * for no reason.
 * <p>
 * The decision is therefore a plain {@code int}. Call sites read constants
 * directly, so a {@code return EntityTickDecision.SKIP;} costs nothing at all.
 *
 * <pre>
 *     int decision = throttler.tickLimiterShouldSkip(entity);
 *     if (EntityTickDecision.shouldRemove(decision)) entity.remove(...);
 *     if (EntityTickDecision.shouldSkip(decision))   return;
 * </pre>
 */
public final class EntityTickDecision {

    /** Tick the entity normally. */
    public static final int TICK = 0;
    /** Skip this entity's tick. */
    public static final int SKIP = 1;
    /** Remove the entity. */
    public static final int REMOVE = 2;

    /**
     * Packed form used when a caller needs more than one bit of information:
     * low 2 bits are the decision, the remaining bits are the priority weight of
     * {@link EntityPriority}. Still a single {@code int}, still allocation-free.
     */
    private static final int DECISION_MASK = 0b11;
    private static final int WEIGHT_SHIFT = 2;

    private EntityTickDecision() {}

    public static int pack(int decision, EntityPriority priority) {
        return (decision & DECISION_MASK) | (priority.weight() << WEIGHT_SHIFT);
    }

    public static int decisionOf(int packed) {
        return packed & DECISION_MASK;
    }

    public static int weightOf(int packed) {
        return (packed >>> WEIGHT_SHIFT) & 0xFF;
    }

    public static boolean shouldTick(int decision) {
        return decisionOf(decision) == TICK;
    }

    public static boolean shouldSkip(int decision) {
        return decisionOf(decision) == SKIP;
    }

    public static boolean shouldRemove(int decision) {
        return decisionOf(decision) == REMOVE;
    }

    public static String name(int decision) {
        return switch (decisionOf(decision)) {
            case TICK -> "TICK";
            case SKIP -> "SKIP";
            case REMOVE -> "REMOVE";
            default -> "UNKNOWN(" + decisionOf(decision) + ")";
        };
    }
}
