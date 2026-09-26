package fun.bm.mili.scheduler;

/**
 * Zero-allocation result of an entity tick throttling decision.
 * <p>
 * fix.md §12: the entity tick hot path must not allocate a
 * {@code new EntityThrottlerReturn()} per entity per tick — under high entity counts
 * that is a sustained flood of short-lived objects.  The decision is an {@code int} so
 * the call site stays allocation-free.
 */
public final class EntityTickDecision {

    /** Tick the entity normally. */
    public static final int TICK = 0;
    /** Skip this entity's tick. */
    public static final int SKIP = 1;
    /** Remove the entity. */
    public static final int REMOVE = 2;

    private EntityTickDecision() {}

    public static boolean shouldTick(int decision) {
        return decision == TICK;
    }

    public static boolean shouldRemove(int decision) {
        return decision == REMOVE;
    }

    public static boolean shouldSkip(int decision) {
        return decision == SKIP;
    }

    public static String name(int decision) {
        return switch (decision) {
            case TICK -> "TICK";
            case SKIP -> "SKIP";
            case REMOVE -> "REMOVE";
            default -> "UNKNOWN(" + decision + ")";
        };
    }
}
