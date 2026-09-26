package fun.bm.mili.scheduler;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stable identity for regions (fix.md §8).
 * <p>
 * The old code keyed every per-region structure by
 * {@code System.identityHashCode(region)}: a 32-bit value that is not unique, is
 * not stable across a JVM's lifetime in any meaningful sense, collides silently
 * (two different regions sharing one statistics bucket and one priority slot),
 * and — because the maps were keyed by {@code Integer} — could never be cleaned
 * up when a region died.
 *
 * <p>Every subsystem (scheduler, metrics, load monitor, cross-region routing,
 * entity budget, debug output) now shares one {@code long} id per region.
 *
 * <p><b>Why weak keys:</b> region objects are created and destroyed constantly as
 * players move. Holding them strongly in a registry would pin every region — and
 * through it the whole {@code RegionizedWorldData} graph — forever. A
 * {@link WeakHashMap} lets the GC reclaim a dead region even if some code path
 * forgot to unregister it, while {@link #remove(Object)} still gives the
 * deterministic cleanup path that fix.md §13/§15 require.
 *
 * <p><b>Contention:</b> lookups happen on region threads and, at most, once per
 * task submission, so a synchronized weak map is far cheaper than the
 * alternative of giving every region a mutable id field we do not own (the
 * region classes are Minecraft types we cannot modify from here).
 */
public final class RegionIdRegistry {

    /** Sentinel meaning "this region has no id yet" / "unknown region". */
    public static final long UNKNOWN = 0L;

    private static final AtomicLong NEXT_ID = new AtomicLong(0);

    private static final Map<Object, Long> ID_BY_REGION =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Regions that have already completed the destroy sequence.
     * <p>
     * Without this, a submit arriving after teardown is indistinguishable from a
     * submit for a region that has simply never been seen: {@link #idOf} would
     * happily allocate a fresh id and the scheduler would build a brand-new
     * runtime around a dead region. That is the classic "region resurrected after
     * destroy" bug — the object is still reachable from whatever code path raced
     * the teardown, so nothing else catches it.
     * <p>
     * Weak keys again, so the tombstone is bounded by the same GC that reclaims
     * the region itself.
     */
    private static final Map<Object, Boolean> DESTROYED =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Optional human-readable label per id, for /mili perf and log lines. Never a strong region ref. */
    private static final Map<Long, String> LABEL_BY_ID = new ConcurrentHashMap<>();

    private RegionIdRegistry() {}

    /**
     * Return the stable id of {@code region}, allocating one on first use.
     *
     * @return a positive id, or {@link #UNKNOWN} if {@code region} is {@code null}
     */
    public static long idOf(Object region) {
        if (region == null) return UNKNOWN;
        Long existing = ID_BY_REGION.get(region);
        if (existing != null) return existing;

        synchronized (ID_BY_REGION) {
            // Re-check under the lock: two threads may have raced here.
            Long raced = ID_BY_REGION.get(region);
            if (raced != null) return raced;
            long id = NEXT_ID.incrementAndGet();
            ID_BY_REGION.put(region, id);
            return id;
        }
    }

    /**
     * Return the id of {@code region} without allocating one.
     *
     * <p>Used on hot paths (entity throttling, cross-region routing) where
     * creating an id for a region nobody registered would be surprising.
     *
     * @return the id, or {@link #UNKNOWN} if this region has never been registered
     */
    public static long peek(Object region) {
        if (region == null) return UNKNOWN;
        Long existing = ID_BY_REGION.get(region);
        return existing == null ? UNKNOWN : existing;
    }

    /**
     * Mark {@code region} as destroyed and release its id.
     * <p>
     * This is step 9 of the region destroy sequence. After it, {@link #idOf}
     * will not hand out an id for this object again, so a late submission is
     * refused instead of resurrecting the region.
     *
     * @return the id that was released, or {@link #UNKNOWN}
     */
    public static long markDestroyed(Object region) {
        if (region == null) return UNKNOWN;
        DESTROYED.put(region, Boolean.TRUE);
        return remove(region);
    }

    /**
     * Whether {@code region} has already been through the destroy sequence.
     * <p>
     * Callers that create per-region state must check this first: a destroyed
     * region is a tombstone, not a new region.
     */
    public static boolean isDestroyed(Object region) {
        return region != null && DESTROYED.containsKey(region);
    }

    /** Forget {@code region} and its id.
     * <p>
     * Called from the region destroy sequence (fix.md §13 step 8) so that a
     * recycled object cannot inherit stale statistics.
     *
     * @return the id that was released, or {@link #UNKNOWN}
     */
    public static long remove(Object region) {
        if (region == null) return UNKNOWN;
        Long removed = ID_BY_REGION.remove(region);
        if (removed == null) return UNKNOWN;
        LABEL_BY_ID.remove(removed);
        return removed;
    }

    /** Attach a debug label (world name, region coordinates, ...) to an id. */
    public static void label(long regionId, String label) {
        if (regionId == UNKNOWN) return;
        if (label == null) {
            LABEL_BY_ID.remove(regionId);
        } else {
            LABEL_BY_ID.put(regionId, label);
        }
    }

    /** Debug label of an id, or a synthetic placeholder. */
    public static String labelOf(long regionId) {
        if (regionId == UNKNOWN) return "region#unknown";
        String label = LABEL_BY_ID.get(regionId);
        return label == null ? ("region#" + regionId) : ("region#" + regionId + "[" + label + "]");
    }

    /** Number of live (weakly reachable) region entries. Diagnostics only. */
    public static int trackedRegions() {
        return ID_BY_REGION.size();
    }

    /** Highest id handed out so far. Diagnostics only. */
    public static long highWaterMark() {
        return NEXT_ID.get();
    }

    /** Drop everything. Called on scheduler shutdown, never during normal operation. */
    public static void clear() {
        ID_BY_REGION.clear();
        LABEL_BY_ID.clear();
        DESTROYED.clear();
    }
}
