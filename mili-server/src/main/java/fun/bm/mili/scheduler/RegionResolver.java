package fun.bm.mili.scheduler;

/**
 * Resolves a world coordinate to the region that owns it (fix.md §12).
 * <p>
 * This exists because {@code CrossRegionHelper.submitRedstoneCrossRegion()} used
 * to resolve <em>both</em> sides of a cross-region operation the same way:
 *
 * <pre>
 *     RegionizedWorldData src = level.getCurrentWorldData();
 *     RegionizedWorldData tgt = level.getCurrentWorldData();   // &lt;-- same object
 *     if (src == tgt) return;                                  // &lt;-- always true, always returns
 * </pre>
 *
 * So the method could never do anything, and the {@code neighbor} coordinate that
 * was supposed to identify the target region was only ever carried along as a
 * field that nobody read. The correctness model fix.md §12 asks for is:
 *
 * <pre>
 *     source position -> source region
 *     target position -> regionizer lookup -> target region
 * </pre>
 *
 * <p>The lookup itself needs the Folia regioniser, which is a Minecraft-side
 * type. So it is injected as a {@link Locator} and this class stays compilable
 * without the game on the classpath.
 */
public final class RegionResolver {

    /**
     * Installed by the adapter. Implementations are expected to be cheap and
     * side-effect free; they run on whatever thread is submitting the work.
     */
    @FunctionalInterface
    public interface Locator {
        /**
         * @param level  the opaque {@code ServerLevel} handle
         * @param blockX block x coordinate of the position being resolved
         * @param blockZ block z coordinate of the position being resolved
         * @return the owning region handle, or {@code null} if it cannot be resolved
         *         (unloaded chunk, region not yet created)
         */
        Object regionAtBlock(Object level, int blockX, int blockZ);
    }

    private static volatile Locator locator;
    private static volatile boolean warnedMissingLocator;

    private RegionResolver() {}

    public static void install(Locator l) {
        locator = l;
    }

    public static boolean isInstalled() {
        return locator != null;
    }

    /**
     * Resolve the region owning a block position.
     *
     * @return the region handle, or {@code null} when it cannot be resolved.
     *         Callers must treat {@code null} as "do not perform the cross-region
     *         operation" rather than falling back to the current region — that
     *         fallback is exactly the bug being fixed.
     */
    public static Object regionAtBlock(Object level, int blockX, int blockZ) {
        Locator l = locator;
        if (l == null) {
            if (!warnedMissingLocator) {
                warnedMissingLocator = true;
                SchedulerLog.warn("RegionResolver has no locator installed; cross-region target "
                        + "resolution is disabled. Install it from the Folia hooks.");
            }
            return null;
        }
        try {
            return l.regionAtBlock(level, blockX, blockZ);
        } catch (Throwable t) {
            SchedulerLog.warn("RegionResolver lookup failed for (%d, %d) in %s: %s",
                    blockX, blockZ, level, t);
            return null;
        }
    }

    /**
     * Resolve the stable id of the region owning a block position.
     *
     * @return the id, or {@link RegionIdRegistry#UNKNOWN} when unresolvable
     */
    public static long regionIdAtBlock(Object level, int blockX, int blockZ) {
        Object region = regionAtBlock(level, blockX, blockZ);
        return region == null ? RegionIdRegistry.UNKNOWN : RegionIdRegistry.idOf(region);
    }

    /** Reset for shutdown / tests. */
    public static void clear() {
        locator = null;
        warnedMissingLocator = false;
    }
}
