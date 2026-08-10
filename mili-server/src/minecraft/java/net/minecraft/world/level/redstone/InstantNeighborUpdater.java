package net.minecraft.world.level.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class InstantNeighborUpdater implements NeighborUpdater {
    private final Level level;

    public InstantNeighborUpdater(Level level) {
        this.level = level;
    }

    @Override
    public void shapeUpdate(Direction direction, BlockState state, BlockPos pos, BlockPos neighborPos, @Block.UpdateFlags int flags, int recursionLeft) {
        NeighborUpdater.executeShapeUpdate(this.level, direction, pos, neighborPos, state, flags, recursionLeft - 1);
    }

    @Override
    public void neighborChanged(BlockPos pos, Block neighborBlock, @Nullable Orientation orientation) {
        BlockState blockState = this.level.getBlockState(pos);
        this.neighborChanged(blockState, pos, neighborBlock, orientation, false);
    }

    @Override
    public void neighborChanged(BlockState state, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        NeighborUpdater.executeUpdate(this.level, state, pos, neighborBlock, orientation, movedByPiston);
    }
}
