package fun.bm.mili.scheduler;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stable region identity.
 * <p>
 * fix.md §8: {@code System.identityHashCode(schedule)} must not be used as a
 * long-lived region identifier.  It is not unique (two live objects may share a
 * value), it is not stable across the metrics / scheduler / registry / cross-region /
 * entity-budget subsystems, and it silently changes meaning if a region object is
 * recreated.
 * <p>
 * This registry hands out a monotonic {@code long} per region <i>identity</i> and
 * keeps the mapping weakly so a destroyed region never leaks.
 * <p>
 * When the region object is a Folia {@code ThreadedRegionizer.ThreadedRegion}, the
 * upstream {@code id} field is preferred so Mili shares Folia's own stable id.
 */
public final class RegionIdRegistry {

    private RegionIdRegistry() {}

    private static final AtomicLong NEXT_REGION_ID = new AtomicLong(0);

    /** Identity-weak key: two different objects with the same identityHashCode never collide. */
    private static final class IdentityKey extends WeakReference<Object> {
        final int hash;

        IdentityKey(Object referent, ReferenceQueue<Object> queue) {
            super(referent, queue);
            this.hash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IdentityKey k)) return false;
            Object a = this.get();
            Object b = k.get();
            // Identity comparison on the referents, not on the weak keys themselves.
            return a != null && a == b;
        }
    }

    private static final ReferenceQueue<Object> STALE = new ReferenceQueue<>();
    private static final ConcurrentHashMap<IdentityKey, Long> IDS = new ConcurrentHashMap<>();
    private static final AtomicLong ALLOCATIONS_SINCE_PURGE = new AtomicLong();
    private static final long PURGE_INTERVAL = 512;

    /**
     * Resolve (or allocate) the stable id of a region.
     *
     * @return a stable id &gt; 0, or 0 when {@code region} is null
     */
    public static long idOf(Object region) {
        if (region == null) return 0L;

        long upstream = upstreamIdOf(region);
        if (upstream != 0L) return upstream;

        IdentityKey key = new IdentityKey(region, STALE);
        Long existing = IDS.get(key);
        if (existing != null) return existing;

        long id = NEXT_REGION_ID.incrementAndGet();
        Long prev = IDS.putIfAbsent(key, id);
        if (prev != null) {
            return prev;
        }
        if (ALLOCATIONS_SINCE_PURGE.incrementAndGet() >= PURGE_INTERVAL) {
            purgeStale();
        }
        return id;
    }

    /** Peek without allocating. Returns 0 if the region is unknown. */
    public static long peek(Object region) {
        if (region == null) return 0L;
        long upstream = upstreamIdOf(region);
        if (upstream != 0L) return upstream;
        Long existing = IDS.get(new IdentityKey(region, STALE));
        return existing != null ? existing : 0L;
    }

    /**
     * Forget a region. Called from the lifecycle teardown (fix.md §9 step 6-7) so that
     * a destroyed region never hands its old id to a successor.
     */
    public static void remove(Object region) {
        if (region == null) return;
        IDS.remove(new IdentityKey(region, STALE));
    }

    public static int trackedRegions() {
        purgeStale();
        return IDS.size();
    }

    /** Remove entries whose region has been garbage collected. */
    public static void purgeStale() {
        ALLOCATIONS_SINCE_PURGE.set(0);
        java.lang.ref.Reference<?> ref;
        int removed = 0;
        while ((ref = STALE.poll()) != null) {
            if (ref instanceof IdentityKey k) {
                IDS.remove(k);
                removed++;
            }
            if (removed > 4096) break; // never let teardown spin
        }
    }

    public static void clear() {
        IDS.clear();
    }

    /**
     * Use Folia's own {@code ThreadedRegionizer.ThreadedRegion#id} when available, so
     * Mili and Folia agree on what "region 42" means.
     */
    private static long upstreamIdOf(Object region) {
        if (region instanceof io.papermc.paper.threadedregions.ThreadedRegionizer.ThreadedRegion<?, ?> tr) {
            try {
                return tr.id;
            } catch (Throwable ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
