package net.minecraft.world.level.block.state.pattern;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.LevelReader;
import org.jspecify.annotations.Nullable;

public class BlockPattern {
    private final Predicate<BlockInWorld>[][][] pattern;
    private final int depth;
    private final int height;
    private final int width;

    public BlockPattern(Predicate<BlockInWorld>[][][] pattern) {
        this.pattern = pattern;
        this.depth = pattern.length;
        if (this.depth > 0) {
            this.height = pattern[0].length;
            if (this.height > 0) {
                this.width = pattern[0][0].length;
            } else {
                this.width = 0;
            }
        } else {
            this.height = 0;
            this.width = 0;
        }
    }

    public int getDepth() {
        return this.depth;
    }

    public int getHeight() {
        return this.height;
    }

    public int getWidth() {
        return this.width;
    }

    @VisibleForTesting
    public Predicate<BlockInWorld>[][][] getPattern() {
        return this.pattern;
    }

    @VisibleForTesting
    public BlockPattern.@Nullable BlockPatternMatch matches(LevelReader level, BlockPos pos, Direction finger, Direction thumb) {
        LoadingCache<BlockPos, BlockInWorld> loadingCache = createLevelCache(level, false);
        return this.matches(pos, finger, thumb, loadingCache);
    }

    public BlockPattern.@Nullable BlockPatternMatch matches(BlockPos pos, Direction finger, Direction thumb, LoadingCache<BlockPos, BlockInWorld> cache) { // Leaves - private -> public
        for (int i = 0; i < this.width; i++) {
            for (int i1 = 0; i1 < this.height; i1++) {
                for (int i2 = 0; i2 < this.depth; i2++) {
                    if (!this.pattern[i2][i1][i].test(cache.getUnchecked(translateAndRotate(pos, finger, thumb, i, i1, i2)))) {
                        return null;
                    }
                }
            }
        }

        return new BlockPattern.BlockPatternMatch(pos, finger, thumb, cache, this.width, this.height, this.depth);
    }

    public BlockPattern.@Nullable BlockPatternMatch find(LevelReader level, BlockPos pos) {
        LoadingCache<BlockPos, BlockInWorld> loadingCache = createLevelCache(level, false);
        int max = Math.max(Math.max(this.width, this.height), this.depth);

        for (BlockPos blockPos : BlockPos.betweenClosed(pos, pos.offset(max - 1, max - 1, max - 1))) {
            for (Direction direction : Direction.values()) {
                for (Direction direction1 : Direction.values()) {
                    if (direction1 != direction && direction1 != direction.getOpposite()) {
                        BlockPattern.BlockPatternMatch blockPatternMatch = this.matches(blockPos, direction, direction1, loadingCache);
                        if (blockPatternMatch != null) {
                            return blockPatternMatch;
                        }
                    }
                }
            }
        }

        return null;
    }

    public static LoadingCache<BlockPos, BlockInWorld> createLevelCache(LevelReader level, boolean forceLoad) {
        return CacheBuilder.newBuilder().build(new BlockPattern.BlockCacheLoader(level, forceLoad));
    }

    protected static BlockPos translateAndRotate(BlockPos pos, Direction finger, Direction thumb, int palmOffset, int thumbOffset, int fingerOffset) {
        if (finger != thumb && finger != thumb.getOpposite()) {
            Vec3i vec3i = new Vec3i(finger.getStepX(), finger.getStepY(), finger.getStepZ());
            Vec3i vec3i1 = new Vec3i(thumb.getStepX(), thumb.getStepY(), thumb.getStepZ());
            Vec3i vec3i2 = vec3i.cross(vec3i1);
            return pos.offset(
                vec3i1.getX() * -thumbOffset + vec3i2.getX() * palmOffset + vec3i.getX() * fingerOffset,
                vec3i1.getY() * -thumbOffset + vec3i2.getY() * palmOffset + vec3i.getY() * fingerOffset,
                vec3i1.getZ() * -thumbOffset + vec3i2.getZ() * palmOffset + vec3i.getZ() * fingerOffset
            );
        } else {
            throw new IllegalArgumentException("Invalid forwards & up combination");
        }
    }

    static class BlockCacheLoader extends CacheLoader<BlockPos, BlockInWorld> {
        private final LevelReader level;
        private final boolean loadChunks;

        public BlockCacheLoader(LevelReader level, boolean loadChunks) {
            this.level = level;
            this.loadChunks = loadChunks;
        }

        @Override
        public BlockInWorld load(BlockPos pos) {
            return new BlockInWorld(this.level, pos, this.loadChunks);
        }
    }

    public static class BlockPatternMatch {
        private final BlockPos frontTopLeft;
        private final Direction forwards;
        private final Direction up;
        private final LoadingCache<BlockPos, BlockInWorld> cache;
        private final int width;
        private final int height;
        private final int depth;

        public BlockPatternMatch(
            BlockPos frontTopLeft, Direction forwards, Direction up, LoadingCache<BlockPos, BlockInWorld> cache, int width, int height, int depth
        ) {
            this.frontTopLeft = frontTopLeft;
            this.forwards = forwards;
            this.up = up;
            this.cache = cache;
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        public BlockPos getFrontTopLeft() {
            return this.frontTopLeft;
        }

        public Direction getForwards() {
            return this.forwards;
        }

        public Direction getUp() {
            return this.up;
        }

        public int getWidth() {
            return this.width;
        }

        public int getHeight() {
            return this.height;
        }

        public int getDepth() {
            return this.depth;
        }

        public BlockInWorld getBlock(int palmOffset, int thumbOffset, int fingerOffset) {
            return this.cache
                .getUnchecked(BlockPattern.translateAndRotate(this.frontTopLeft, this.getForwards(), this.getUp(), palmOffset, thumbOffset, fingerOffset));
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("up", this.up).add("forwards", this.forwards).add("frontTopLeft", this.frontTopLeft).toString();
        }
    }
}
