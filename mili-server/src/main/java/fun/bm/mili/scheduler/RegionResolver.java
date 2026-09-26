package fun.bm.mili.scheduler;

import io.papermc.paper.threadedregions.TickRegionScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Resolves which region owns a given position.
 * <p>
 * fix.md §10: {@code CrossRegionHelper} must distinguish source region from target
 * region instead of calling {@code level.getCurrentWorldData()} twice and concluding
 * "never cross region".  The correct model is:
 * <pre>
 *     Source Position -> Source Region (the region this thread is ticking)
 *     Target Position -> Regionizer     -> Target Region
 * </pre>
 * <p>
 * Mili start - fix: the two lookups must return the <b>same type</b>, otherwise
 * {@code source == target} is meaningless and {@link RegionIdRegistry} hands out two ids
 * for one region.  This file previously claimed the two sides were "normalised to the same
 * upstream type ({@code RegionizedWorldData}, concretely {@code TickRegions.TickRegionData})",
 * but that is not true in Folia:
 * <ul>
 *   <li>{@code io.papermc.paper.threadedregions.RegionizedWorldData} is a {@code final class}
 *       (what {@code level.getCurrentWorldData()} returns);</li>
 *   <li>{@code io.papermc.paper.threadedregions.TickRegions.TickRegionData} is a different
 *       {@code final class} ({@code implements ThreadedRegionizer.ThreadedRegionData}) and is
 *       what {@code region.getData()} returns.</li>
 * </ul>
 * Neither extends the other, so comparing them could never be true — {@link #isLocal} was
 * false for every position — and {@link RegionIdRegistry#idOf(Object)}'s Folia-aware branch
 * (which matches {@code ThreadedRegionizer.ThreadedRegion}) was dead code, so Mili never used
 * Folia's own stable region id.
 * <p>
 * The canonical identity is therefore the {@code ThreadedRegion} itself: it is exactly what
 * {@code TickRegionScheduler.getCurrentRegion()} and {@code regioniser.getRegionAt*} return,
 * it carries {@code public final long id}, and it is the type {@code RegionIdRegistry} knows.
 */
public final class RegionResolver {

    private RegionResolver() {}

    /**
     * The region the calling thread is currently ticking, or {@code null}.
     * <p>
     * Returns {@code null} when the caller is not a tick thread; a non-region thread must
     * never be able to claim "I am inside region X".
     */
    public static Object currentTickRegion() {
        try {
            if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThread()) {
                return null;
            }
            return TickRegionScheduler.getCurrentRegion();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The region the calling thread is currently inside, or {@code null}.
     *
     * @param level kept for call-site symmetry; the answer is a property of the current
     *              thread, not of the level, so it is not used for the lookup
     */
    public static Object currentRegion(ServerLevel level) {
        return currentTickRegion();
    }

    /** Region owning {@code (blockX, blockZ)}, or {@code null} if unloaded/unresolvable. */
    public static Object regionAtBlock(ServerLevel level, int blockX, int blockZ) {
        return regionAtChunk(level, blockX >> 4, blockZ >> 4);
    }

    public static Object regionAtBlock(ServerLevel level, BlockPos pos) {
        if (pos == null) return null;
        return regionAtBlock(level, pos.getX(), pos.getZ());
    }

    /**
     * Region owning {@code (chunkX, chunkZ)}, or {@code null} if unresolvable.
     * <p>
     * Uses the synchronised accessor on purpose: it takes the regionizer's optimistic read
     * and falls back to its read lock, so it is legal from any thread.
     * {@code getRegionAtUnsynchronised} reads the section map with no synchronisation at all
     * and is only safe for a caller that already holds the region lock — using it here would
     * be a data race, not a speed-up.
     *
     * @return the {@code ThreadedRegion} itself, never {@code region.getData()} — see the
     *         class comment for why the region and its data are not interchangeable
     */
    public static Object regionAtChunk(ServerLevel level, int chunkX, int chunkZ) {
        if (level == null) return null;
        try {
            return level.regioniser.getRegionAtSynchronised(chunkX, chunkZ);
        } catch (Throwable t) {
            // Region lookup must never break the caller: unresolved means "not cross-region".
            return null;
        }
    }

    /** Stable Mili id of the region owning {@code (blockX, blockZ)}; 0 when unresolvable. */
    public static long regionIdAtBlock(ServerLevel level, int blockX, int blockZ) {
        return RegionIdRegistry.idOf(regionAtBlock(level, blockX, blockZ));
    }

    /**
     * Whether {@code target} belongs to the region the caller is running in.
     * <p>
     * Resolving the target through the same regionizer as the source keeps the comparison
     * symmetrical — comparing an id from one lookup path against an id from another would
     * silently never match.
     */
    public static boolean isLocal(ServerLevel level, BlockPos target) {
        Object current = currentTickRegion();
        if (current == null || target == null) return true;
        Object targetRegion = regionAtBlock(level, target);
        if (targetRegion == null) return true;
        return current == targetRegion;
    }

    /**
     * Resolve the target region for a cross-region operation.
     *
     * @return the target region object, or {@code null} if it cannot be resolved
     */
    public static Object resolveTarget(ServerLevel level, BlockPos target) {
        return regionAtBlock(level, target);
    }
}
