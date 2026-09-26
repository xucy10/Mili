package fun.bm.mili.scheduler;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Region Ownership probe (fix.md §2.1).
 * <p>
 * {@code submitAndWait()} must first answer one question: <em>is the calling
 * thread the region's current legal execution thread?</em>
 *
 * <ul>
 *   <li><b>Yes</b> — the work may run inline. That is not a shortcut, it is the
 *       only way to preserve region context when Folia already ticked us on the
 *       owning thread.</li>
 *   <li><b>No</b> — the work must be queued for the region and waited on. It must
 *       <b>never</b> run on the caller.</li>
 * </ul>
 *
 * <p>The forbidden sequence from fix.md §2.3 is exactly what the old
 * implementation did unconditionally:
 *
 * <pre>
 *     Region A thread -> submit Region B task -> queue unavailable -> Region A runs Region B work
 * </pre>
 *
 * <h2>How ownership is resolved</h2>
 * <ol>
 *   <li>A {@link Resolver} installed by the Folia-side hooks. Authoritative.</li>
 *   <li>The owner thread Mili itself recorded when it dispatched region work
 *       (keyed by {@link RegionIdRegistry} id, so no strong region reference is held).</li>
 *   <li>A reflective probe for an {@code isOwnedByCurrentRegion()} /
 *       {@code isOwnedByCurrentThread()} method on the region object.</li>
 * </ol>
 *
 * <h2>Failure direction</h2>
 * If nothing can prove ownership the answer is <b>{@code false}</b>. This is the
 * deliberate safe direction: {@code false} routes the work through the region's
 * queue (correct, merely slower), whereas an optimistic {@code true} would let a
 * foreign thread mutate another region's state — the one failure mode that
 * corrupts the world instead of just costing latency.
 */
public final class RegionOwnership {

    /**
     * Authoritative ownership probe.
     * <p>
     * Implemented on the Folia side, where the concrete region class and its
     * thread bookkeeping are actually visible. The scheduler core stays free of
     * Minecraft types so it can be compiled and unit-tested on its own.
     */
    @FunctionalInterface
    public interface Resolver {
        /**
         * @param region the opaque region handle
         * @return {@code true} if the calling thread may legally mutate {@code region} right now
         */
        boolean isOwnedByCurrentThread(Object region);
    }

    private static final String[] PROBE_NAMES = {
            "isOwnedByCurrentRegion",
            "isOwnedByCurrentThread",
            "isCurrentThreadOwned"
    };

    private static volatile Resolver resolver;

    /** regionId -> owning thread. Threads are strongly held but regions are not. */
    private static final Map<Long, Thread> OWNER_BY_REGION_ID = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Method> PROBE_CACHE = new ConcurrentHashMap<>();

    /** Region classes already known to expose no ownership method, so we stop reflecting. */
    private static final Map<Class<?>, Boolean> NO_PROBE = new ConcurrentHashMap<>();

    private RegionOwnership() {}

    /** Install the authoritative resolver. Called once during scheduler bootstrap. */
    public static void installResolver(Resolver r) {
        resolver = r;
    }

    public static Resolver resolver() {
        return resolver;
    }

    public static boolean hasResolver() {
        return resolver != null;
    }

    /**
     * Record that {@code thread} is currently executing work for the region with
     * {@code regionId}.
     * <p>
     * Called by {@link WorkerRuntime} / {@link MiliRegionRuntime} immediately
     * around every region-owned execution, so the second resolution strategy has
     * something to answer with even when no resolver is installed.
     */
    public static void markOwner(long regionId, Thread thread) {
        if (regionId == RegionIdRegistry.UNKNOWN) return;
        if (thread == null) {
            OWNER_BY_REGION_ID.remove(regionId);
        } else {
            OWNER_BY_REGION_ID.put(regionId, thread);
        }
    }

    /** Release the recorded owner, without clobbering a newer one. */
    public static void releaseOwner(long regionId, Thread thread) {
        if (regionId == RegionIdRegistry.UNKNOWN) return;
        if (thread == null) {
            OWNER_BY_REGION_ID.remove(regionId);
        } else {
            OWNER_BY_REGION_ID.remove(regionId, thread);
        }
    }

    /** The thread currently recorded as owning {@code regionId}, or {@code null}. */
    public static Thread ownerThreadOf(long regionId) {
        if (regionId == RegionIdRegistry.UNKNOWN) return null;
        return OWNER_BY_REGION_ID.get(regionId);
    }

    /**
     * Can the calling thread legally mutate {@code region} right now?
     *
     * @return {@code false} whenever ownership cannot be positively established
     */
    public static boolean isOwnedByCurrentThread(Object region) {
        if (region == null) return false;

        Resolver r = resolver;
        if (r != null) {
            try {
                return r.isOwnedByCurrentThread(region);
            } catch (Throwable ignored) {
                // A broken resolver must not take the server down; fall through
                // to the weaker strategies below.
            }
        }

        long regionId = RegionIdRegistry.peek(region);
        if (regionId != RegionIdRegistry.UNKNOWN) {
            Thread owner = OWNER_BY_REGION_ID.get(regionId);
            if (owner != null) {
                return owner == Thread.currentThread();
            }
        }

        Boolean probed = reflectiveProbe(region);
        return probed != null && probed;
    }

    /**
     * Whether ownership is currently <em>knowable</em> for this region class —
     * i.e. a resolver is installed, an owner is recorded, or the class exposes a
     * probe method. Lets metrics distinguish "definitely not the owner" from
     * "we cannot tell", which is useful when tuning hook coverage.
     */
    public static boolean canDetermineOwnership(Object region) {
        if (region == null) return false;
        if (resolver != null) return true;
        if (RegionIdRegistry.peek(region) != RegionIdRegistry.UNKNOWN
                && OWNER_BY_REGION_ID.containsKey(RegionIdRegistry.peek(region))) {
            return true;
        }
        return reflectiveProbe(region) != null;
    }

    private static Boolean reflectiveProbe(Object region) {
        Class<?> type = region.getClass();
        if (NO_PROBE.containsKey(type)) return null;

        Method probe = PROBE_CACHE.get(type);
        if (probe == null && !PROBE_CACHE.containsKey(type)) {
            Method found = null;
            for (String name : PROBE_NAMES) {
                try {
                    Method m = type.getMethod(name);
                    if (m.getReturnType() == boolean.class && m.getParameterCount() == 0) {
                        found = m;
                        break;
                    }
                } catch (Throwable ignored) {
                    // Try the next candidate name.
                }
            }
            if (found == null) {
                NO_PROBE.put(type, Boolean.TRUE);
                return null;
            }
            try {
                found.setAccessible(true);
            } catch (Throwable ignored) {
                // Leave accessibility as-is; invoke() will fail and be handled below.
            }
            PROBE_CACHE.put(type, found);
            probe = found;
        }
        if (probe == null) return null;

        try {
            Object result = probe.invoke(region);
            return result instanceof Boolean b ? b : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Drop all recorded ownership and the probe cache. Used on shutdown. */
    public static void clear() {
        OWNER_BY_REGION_ID.clear();
        PROBE_CACHE.clear();
        NO_PROBE.clear();
    }
}
