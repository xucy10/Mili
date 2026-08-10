package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;

public class HangingSignItem extends SignItem {
    public HangingSignItem(Block block, Block wallBlock, Item.Properties properties) {
        super(properties, block, wallBlock, Direction.UP);
    }

    @Override
    protected boolean canPlace(LevelReader level, BlockState state, BlockPos pos) {
        return !(state.getBlock() instanceof WallHangingSignBlock wallHangingSignBlock && !wallHangingSignBlock.canPlace(state, level, pos))
            && super.canPlace(level, state, pos);
    }
}
