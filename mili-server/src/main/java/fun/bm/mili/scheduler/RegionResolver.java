package fun.bm.mili.scheduler;

import io.papermc.paper.threadedregions.TickRegions;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Resolves which region owns a given position.
 * <p>
 * fix.md §10: {@code CrossRegionHelper} must distinguish source region from target
 * region instead of calling {@code level.getCurrentWorldData()} twice and concluding
 * "never cross region".  The correct model is:
 * <pre>
 *     Source Position -> Source Region
 *     Target Position -> Regionizer -> Target Region
 * </pre>
 * <p>
 * Both sides are normalised to the same upstream type ({@code RegionizedWorldData},
 * concretely {@code TickRegions.TickRegionData}) so that {@code source == target} is a
 * meaningful identity comparison and {@link RegionIdRegistry} assigns one stable id
 * per region regardless of which lookup produced it.
 */
public final class RegionResolver {

    private RegionResolver() {}

    /** The region the calling thread is currently inside, or {@code null}. */
    public static Object currentRegion(ServerLevel level) {
        return level == null ? null : level.getCurrentWorldData();
    }

    /** Region owning {@code (blockX, blockZ)}, or {@code null} if unloaded/unresolvable. */
    public static Object regionAtBlock(ServerLevel level, int blockX, int blockZ) {
        return regionAtChunk(level, blockX >> 4, blockZ >> 4);
    }

    public static Object regionAtBlock(ServerLevel level, BlockPos pos) {
        if (pos == null) return null;
        return regionAtBlock(level, pos.getX(), pos.getZ());
    }

    public static Object regionAtChunk(ServerLevel level, int chunkX, int chunkZ) {
        if (level == null) return null;
        try {
            ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region =
                    level.regioniser.getRegionAtSynchronised(chunkX, chunkZ);
            if (region == null) return null;
            return region.getData();
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
     * Resolving the target region through the same regionizer as the source keeps the
     * comparison symmetrical — comparing an id from one lookup path against an id from
     * another would silently never match.
     */
    public static boolean isLocal(ServerLevel level, BlockPos target) {
        Object current = currentRegion(level);
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
