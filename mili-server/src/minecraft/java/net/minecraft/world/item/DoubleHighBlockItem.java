package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class DoubleHighBlockItem extends BlockItem {
    public DoubleHighBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos().above();
        BlockState blockState = level.isWaterAt(blockPos) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        level.setBlock(blockPos, blockState, Block.UPDATE_ALL_IMMEDIATE | Block.UPDATE_KNOWN_SHAPE);
        return super.placeBlock(context, state);
    }
}
