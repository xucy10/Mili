package net.minecraft.world.level.block;

import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface SegmentableBlock {
    int MIN_SEGMENT = 1;
    int MAX_SEGMENT = 4;
    IntegerProperty AMOUNT = BlockStateProperties.SEGMENT_AMOUNT;

    default Function<BlockState, VoxelShape> getShapeCalculator(EnumProperty<Direction> directionProperty, IntegerProperty amountProperty) {
        Map<Direction, VoxelShape> map = Shapes.rotateHorizontal(Block.box(0.0, 0.0, 0.0, 8.0, this.getShapeHeight(), 8.0));
        return state -> {
            VoxelShape voxelShape = Shapes.empty();
            Direction direction = state.getValue(directionProperty);
            int value = state.getValue(amountProperty);

            for (int i = 0; i < value; i++) {
                voxelShape = Shapes.or(voxelShape, map.get(direction));
                direction = direction.getCounterClockWise();
            }

            return voxelShape.singleEncompassing();
        };
    }

    default IntegerProperty getSegmentAmountProperty() {
        return AMOUNT;
    }

    default double getShapeHeight() {
        return 1.0;
    }

    default boolean canBeReplaced(BlockState state, BlockPlaceContext context, IntegerProperty amountProperty) {
        return !context.isSecondaryUseActive() && context.getItemInHand().is(state.getBlock().asItem()) && state.getValue(amountProperty) < 4;
    }

    default BlockState getStateForPlacement(BlockPlaceContext context, Block block, IntegerProperty amountProperty, EnumProperty<Direction> directionProperty) {
        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos());
        return blockState.is(block)
            ? blockState.setValue(amountProperty, Math.min(4, blockState.getValue(amountProperty) + 1))
            : block.defaultBlockState().setValue(directionProperty, context.getHorizontalDirection().getOpposite());
    }
}
