package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class IronBarsBlock extends CrossCollisionBlock {
    public static final MapCodec<IronBarsBlock> CODEC = simpleCodec(IronBarsBlock::new);

    @Override
    public MapCodec<? extends IronBarsBlock> codec() {
        return CODEC;
    }

    protected IronBarsBlock(BlockBehaviour.Properties properties) {
        super(2.0F, 16.0F, 2.0F, 16.0F, 16.0F, properties);
        this.registerDefaultState(
            this.stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false).setValue(WATERLOGGED, false)
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        BlockPos blockPos = clickedPos.north();
        BlockPos blockPos1 = clickedPos.south();
        BlockPos blockPos2 = clickedPos.west();
        BlockPos blockPos3 = clickedPos.east();
        BlockState blockState = level.getBlockState(blockPos);
        BlockState blockState1 = level.getBlockState(blockPos1);
        BlockState blockState2 = level.getBlockState(blockPos2);
        BlockState blockState3 = level.getBlockState(blockPos3);
        return this.defaultBlockState()
            .setValue(NORTH, this.attachsTo(blockState, blockState.isFaceSturdy(level, blockPos, Direction.SOUTH)))
            .setValue(SOUTH, this.attachsTo(blockState1, blockState1.isFaceSturdy(level, blockPos1, Direction.NORTH)))
            .setValue(WEST, this.attachsTo(blockState2, blockState2.isFaceSturdy(level, blockPos2, Direction.EAST)))
            .setValue(EAST, this.attachsTo(blockState3, blockState3.isFaceSturdy(level, blockPos3, Direction.WEST)))
            .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return direction.getAxis().isHorizontal()
            ? state.setValue(
                PROPERTY_BY_DIRECTION.get(direction), this.attachsTo(neighborState, neighborState.isFaceSturdy(level, neighborPos, direction.getOpposite()))
            )
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
        if (adjacentBlockState.is(this)
            || adjacentBlockState.is(BlockTags.BARS)
                && state.is(BlockTags.BARS)
                && adjacentBlockState.hasProperty(PROPERTY_BY_DIRECTION.get(side.getOpposite()))) {
            if (!side.getAxis().isHorizontal()) {
                return true;
            }

            if (state.getValue(PROPERTY_BY_DIRECTION.get(side)) && adjacentBlockState.getValue(PROPERTY_BY_DIRECTION.get(side.getOpposite()))) {
                return true;
            }
        }

        return super.skipRendering(state, adjacentBlockState, side);
    }

    public final boolean attachsTo(BlockState state, boolean solidSide) {
        return !isExceptionForConnection(state) && solidSide || state.getBlock() instanceof IronBarsBlock || state.is(BlockTags.WALLS);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, WEST, SOUTH, WATERLOGGED);
    }
}
