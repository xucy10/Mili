package net.minecraft.world.level.pathfinder;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.jspecify.annotations.Nullable;

public class PathTypeCache {
    private static final int SIZE = 4096;
    private static final int MASK = 4095;
    private final long[] positions = new long[4096];
    private final PathType[] pathTypes = new PathType[4096];

    public PathType getOrCompute(BlockGetter level, BlockPos pos) {
        long packedBlockPos = pos.asLong();
        int i = index(packedBlockPos);
        PathType pathType = this.get(i, packedBlockPos);
        return pathType != null ? pathType : this.compute(level, pos, i, packedBlockPos);
    }

    private @Nullable PathType get(int index, long pos) {
        return this.positions[index] == pos ? this.pathTypes[index] : null;
    }

    private PathType compute(BlockGetter level, BlockPos pos, int index, long packedPos) {
        PathType pathTypeFromState = WalkNodeEvaluator.getPathTypeFromState(level, pos);
        this.positions[index] = packedPos;
        this.pathTypes[index] = pathTypeFromState;
        return pathTypeFromState;
    }

    public void invalidate(BlockPos pos) {
        long packedBlockPos = pos.asLong();
        int i = index(packedBlockPos);
        if (this.positions[i] == packedBlockPos) {
            this.pathTypes[i] = null;
        }
    }

    private static int index(long packedPos) {
        return (int)HashCommon.mix(packedPos) & 4095;
    }
}
