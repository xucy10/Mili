package fun.bm.mili.scheduler;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Region ownership probe.
 * <p>
 * fix.md §2.1: {@code submitAndWait()} must first ask "is the calling thread the
 * region's current legal execution thread?".  If yes the work may run inline; if no
 * it must go through the scheduler.  It must <b>never</b> silently execute another
 * region's work on the caller thread.
 * <p>
 * Ownership is resolved in this order:
 * <ol>
 *   <li>a resolver registered by the Mili Folia hooks (authoritative),</li>
 *   <li>the owner thread Mili recorded when it dispatched region work,</li>
 *   <li>reflective probe for {@code isOwnedByCurrentRegion()} / {@code isOwnedByCurrentThread()}.</li>
 * </ol>
 * If nothing can prove ownership the answer is {@code false} — the safe direction,
 * because "false" routes the work into the region's own queue instead of running it
 * on a thread that demonstrably does not own the region.
 */
public final class RegionOwnership {

    private RegionOwnership() {}

    /** Authoritative ownership probe, installed by the Folia-side Mili hooks. */
    public interface Resolver {
        /**
         * @return {@code true} if the calling thread may legally mutate {@code region} right now
         */
        boolean isOwnedByCurrentThread(Object region);
    }

    private static volatile Resolver resolver;
    private static final ConcurrentHashMap<Object, Thread> OWNER_THREAD = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Method> PROBE_CACHE = new ConcurrentHashMap<>();
    /** Classes confirmed to expose no ownership method, so we stop probing them. */
    private static final ConcurrentHashMap<Class<?>, Boolean> NO_PROBE_MARK = new ConcurrentHashMap<>();

    private static final String[] PROBE_NAMES = {"isOwnedByCurrentRegion", "isOwnedByCurrentThread"};

    public static void setResolver(Resolver r) {
        resolver = r;
    }

    public static Resolver resolver() {
        return resolver;
    }

    /**
     * Record that {@code thread} is currently executing work for {@code region}.
     * Called by the worker runtime immediately before it runs region work.
     */
    public static void markOwner(Object region, Thread thread) {
        if (region == null) return;
        if (thread == null) {
            OWNER_THREAD.remove(region);
        } else {
            OWNER_THREAD.put(region, thread);
        }
    }

    /** Release the recorded owner. Pass the owning thread to avoid clobbering a newer owner. */
    public static void releaseOwner(Object region, Thread thread) {
        if (region == null) return;
        if (thread == null) {
            OWNER_THREAD.remove(region);
        } else {
            OWNER_THREAD.remove(region, thread);
        }
    }

    public static Thread ownerThreadOf(Object region) {
        return region == null ? null : OWNER_THREAD.get(region);
    }

    public static boolean isOwnedByCurrentThread(Object region) {
        if (region == null) return false;

        Resolver r = resolver;
        if (r != null) {
            try {
                return r.isOwnedByCurrentThread(region);
            } catch (Throwable ignored) {
                // fall through to the weaker strategies
            }
        }

        Thread owner = OWNER_THREAD.get(region);
        if (owner != null) {
            return owner == Thread.currentThread();
        }

        Boolean probe = reflectiveProbe(region);
        return probe != null && probe;
    }

    private static Boolean reflectiveProbe(Object region) {
        Class<?> type = region.getClass();
        if (NO_PROBE_MARK.containsKey(type)) return null;

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
                    // try the next name
                }
            }
            if (found == null) {
                NO_PROBE_MARK.put(type, Boolean.TRUE);
                return null;
            }
            found.setAccessible(true);
            PROBE_CACHE.put(type, found);
            probe = found;
        }
        if (probe == null) return null;

        try {
            return (Boolean) probe.invoke(region);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Drop all recorded ownership. Used on shutdown (fix.md §9). */
    public static void clear() {
        OWNER_THREAD.clear();
    }
}
