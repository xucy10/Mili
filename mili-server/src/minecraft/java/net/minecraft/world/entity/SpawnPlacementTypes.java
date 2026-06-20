package net.minecraft.world.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import org.jspecify.annotations.Nullable;

public interface SpawnPlacementTypes {
    SpawnPlacementType NO_RESTRICTIONS = (level, pos, entityType) -> true;
    SpawnPlacementType IN_WATER = (level, pos, entityType) -> {
        if (entityType != null && level.getWorldBorder().isWithinBounds(pos)) {
            BlockPos blockPos = pos.above();
            return level.getFluidState(pos).is(FluidTags.WATER) && !level.getBlockState(blockPos).isRedstoneConductor(level, blockPos);
        } else {
            return false;
        }
    };
    SpawnPlacementType IN_LAVA = (level, pos, entityType) -> entityType != null
        && level.getWorldBorder().isWithinBounds(pos)
        && level.getFluidState(pos).is(FluidTags.LAVA);
    SpawnPlacementType ON_GROUND = new SpawnPlacementType() {
        @Override
        public boolean isSpawnPositionOk(LevelReader level, BlockPos pos, @Nullable EntityType<?> entityType) {
            if (entityType != null && level.getWorldBorder().isWithinBounds(pos)) {
                BlockPos blockPos = pos.above();
                BlockPos blockPos1 = pos.below();
                BlockState blockState = level.getBlockState(blockPos1);
                return blockState.isValidSpawn(level, blockPos1, entityType)
                    && this.isValidEmptySpawnBlock(level, pos, entityType)
                    && this.isValidEmptySpawnBlock(level, blockPos, entityType);
            } else {
                return false;
            }
        }

        private boolean isValidEmptySpawnBlock(LevelReader level, BlockPos pos, EntityType<?> entityType) {
            BlockState blockState = level.getBlockState(pos);
            return NaturalSpawner.isValidEmptySpawnBlock(level, pos, blockState, blockState.getFluidState(), entityType);
        }

        @Override
        public BlockPos adjustSpawnPosition(LevelReader level, BlockPos pos) {
            BlockPos blockPos = pos.below();
            return level.getBlockState(blockPos).isPathfindable(PathComputationType.LAND) ? blockPos : pos;
        }
    };
}
