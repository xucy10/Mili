package net.minecraft.world.level.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public abstract class RedstoneWireEvaluator {
    protected final RedStoneWireBlock wireBlock;

    protected RedstoneWireEvaluator(RedStoneWireBlock wireBlock) {
        this.wireBlock = wireBlock;
    }

    public abstract void updatePowerStrength(Level level, BlockPos pos, BlockState state, @Nullable Orientation orientation, boolean updateShape);

    protected int getBlockSignal(Level level, BlockPos pos) {
        return this.wireBlock.getBlockSignal(level, pos);
    }

    protected int getWireSignal(BlockPos pos, BlockState state) {
        return state.is(this.wireBlock) ? state.getValue(RedStoneWireBlock.POWER) : 0;
    }

    protected int getIncomingWireSignal(Level level, BlockPos pos) {
        int i = 0;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = pos.relative(direction);
            BlockState blockState = level.getBlockState(blockPos);
            i = Math.max(i, this.getWireSignal(blockPos, blockState));
            BlockPos blockPos1 = pos.above();
            if (blockState.isRedstoneConductor(level, blockPos) && !level.getBlockState(blockPos1).isRedstoneConductor(level, blockPos1)) {
                BlockPos blockPos2 = blockPos.above();
                i = Math.max(i, this.getWireSignal(blockPos2, level.getBlockState(blockPos2)));
            } else if (!blockState.isRedstoneConductor(level, blockPos)) {
                BlockPos blockPos2 = blockPos.below();
                i = Math.max(i, this.getWireSignal(blockPos2, level.getBlockState(blockPos2)));
            }
        }

        return Math.max(0, i - 1);
    }
}
