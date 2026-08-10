package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;

public class TrappedChestBlockEntity extends ChestBlockEntity {
    public TrappedChestBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.TRAPPED_CHEST, pos, blockState);
    }

    @Override
    protected void signalOpenCount(Level level, BlockPos pos, BlockState state, int eventId, int eventParam) {
        super.signalOpenCount(level, pos, state, eventId, eventParam);
        if (eventId != eventParam) {
            Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, state.getValue(TrappedChestBlock.FACING).getOpposite(), Direction.UP);
            Block block = state.getBlock();
            level.updateNeighborsAt(pos, block, orientation);
            level.updateNeighborsAt(pos.below(), block, orientation);
        }
    }
}
