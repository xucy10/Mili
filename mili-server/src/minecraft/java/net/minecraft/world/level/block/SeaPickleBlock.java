package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class SeaPickleBlock extends VegetationBlock implements BonemealableBlock, SimpleWaterloggedBlock {
    public static final MapCodec<SeaPickleBlock> CODEC = simpleCodec(SeaPickleBlock::new);
    public static final int MAX_PICKLES = 4;
    public static final IntegerProperty PICKLES = BlockStateProperties.PICKLES;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE_ONE = Block.column(4.0, 0.0, 6.0);
    private static final VoxelShape SHAPE_TWO = Block.column(10.0, 0.0, 6.0);
    private static final VoxelShape SHAPE_THREE = Block.column(12.0, 0.0, 6.0);
    private static final VoxelShape SHAPE_FOUR = Block.column(12.0, 0.0, 7.0);

    @Override
    public MapCodec<SeaPickleBlock> codec() {
        return CODEC;
    }

    protected SeaPickleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(PICKLES, 1).setValue(WATERLOGGED, true));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos());
        if (blockState.is(this)) {
            return blockState.setValue(PICKLES, Math.min(4, blockState.getValue(PICKLES) + 1));
        } else {
            FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
            boolean flag = fluidState.getType() == Fluids.WATER;
            return super.getStateForPlacement(context).setValue(WATERLOGGED, flag);
        }
    }

    public static boolean isDead(BlockState state) {
        return !state.getValue(WATERLOGGED);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getCollisionShape(level, pos).getFaceShape(Direction.UP).isEmpty() || state.isFaceSturdy(level, pos, Direction.UP);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockPos = pos.below();
        return this.mayPlaceOn(level.getBlockState(blockPos), level, blockPos);
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
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            if (state.getValue(WATERLOGGED)) {
                scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }

            return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        }
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return !useContext.isSecondaryUseActive() && useContext.getItemInHand().is(this.asItem()) && state.getValue(PICKLES) < 4
            || super.canBeReplaced(state, useContext);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PICKLES)) {
            case 2 -> SHAPE_TWO;
            case 3 -> SHAPE_THREE;
            case 4 -> SHAPE_FOUR;
            default -> SHAPE_ONE;
        };
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PICKLES, WATERLOGGED);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return !isDead(state) && level.getBlockState(pos.below()).is(BlockTags.CORAL_BLOCKS);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        int i = 5;
        int i1 = 1;
        int i2 = 2;
        int i3 = 0;
        int i4 = pos.getX() - 2;
        int i5 = 0;

        for (int i6 = 0; i6 < 5; i6++) {
            for (int i7 = 0; i7 < i1; i7++) {
                int i8 = 2 + pos.getY() - 1;

                for (int i9 = i8 - 2; i9 < i8; i9++) {
                    BlockPos blockPos = new BlockPos(i4 + i6, i9, pos.getZ() - i5 + i7);
                    if (!blockPos.equals(pos) && random.nextInt(6) == 0 && level.getBlockState(blockPos).is(Blocks.WATER)) {
                        BlockState blockState = level.getBlockState(blockPos.below());
                        if (blockState.is(BlockTags.CORAL_BLOCKS)) {
                            level.setBlock(blockPos, Blocks.SEA_PICKLE.defaultBlockState().setValue(PICKLES, random.nextInt(4) + 1), Block.UPDATE_ALL);
                        }
                    }
                }
            }

            if (i3 < 2) {
                i1 += 2;
                i5++;
            } else {
                i1 -= 2;
                i5--;
            }

            i3++;
        }

        level.setBlock(pos, state.setValue(PICKLES, 4), Block.UPDATE_CLIENTS);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
