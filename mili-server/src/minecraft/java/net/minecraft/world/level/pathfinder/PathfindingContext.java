package net.minecraft.world.level.pathfinder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class PathfindingContext {
    private final CollisionGetter level;
    private final @Nullable PathTypeCache cache;
    private final BlockPos mobPosition;
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    public PathfindingContext(CollisionGetter level, Mob mob) {
        this.level = level;
        if (mob.level() instanceof ServerLevel serverLevel) {
            this.cache = serverLevel.getPathTypeCache();
        } else {
            this.cache = null;
        }

        this.mobPosition = mob.blockPosition();
    }

    public PathType getPathTypeFromState(int x, int y, int z) {
        BlockPos blockPos = this.mutablePos.set(x, y, z);
        return this.cache == null ? WalkNodeEvaluator.getPathTypeFromState(this.level, blockPos) : this.cache.getOrCompute(this.level, blockPos);
    }

    public BlockState getBlockState(BlockPos pos) {
        return this.level.getBlockState(pos);
    }

    public CollisionGetter level() {
        return this.level;
    }

    public BlockPos mobPosition() {
        return this.mobPosition;
    }
}
