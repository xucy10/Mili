package net.minecraft.world.level.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StairBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<StairBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(BlockState.CODEC.fieldOf("base_state").forGetter(stairBlock -> stairBlock.baseState), propertiesCodec())
            .apply(instance, StairBlock::new)
    );
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final EnumProperty<StairsShape> SHAPE = BlockStateProperties.STAIRS_SHAPE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE_OUTER = Shapes.or(Block.column(16.0, 0.0, 8.0), Block.box(0.0, 8.0, 0.0, 8.0, 16.0, 8.0));
    private static final VoxelShape SHAPE_STRAIGHT = Shapes.or(SHAPE_OUTER, Shapes.rotate(SHAPE_OUTER, OctahedralGroup.BLOCK_ROT_Y_90));
    private static final VoxelShape SHAPE_INNER = Shapes.or(SHAPE_STRAIGHT, Shapes.rotate(SHAPE_STRAIGHT, OctahedralGroup.BLOCK_ROT_Y_90));
    private static final Map<Direction, VoxelShape> SHAPE_BOTTOM_OUTER = Shapes.rotateHorizontal(SHAPE_OUTER);
    private static final Map<Direction, VoxelShape> SHAPE_BOTTOM_STRAIGHT = Shapes.rotateHorizontal(SHAPE_STRAIGHT);
    private static final Map<Direction, VoxelShape> SHAPE_BOTTOM_INNER = Shapes.rotateHorizontal(SHAPE_INNER);
    private static final Map<Direction, VoxelShape> SHAPE_TOP_OUTER = Shapes.rotateHorizontal(SHAPE_OUTER, OctahedralGroup.INVERT_Y);
    private static final Map<Direction, VoxelShape> SHAPE_TOP_STRAIGHT = Shapes.rotateHorizontal(SHAPE_STRAIGHT, OctahedralGroup.INVERT_Y);
    private static final Map<Direction, VoxelShape> SHAPE_TOP_INNER = Shapes.rotateHorizontal(SHAPE_INNER, OctahedralGroup.INVERT_Y);
    private final Block base;
    protected final BlockState baseState;

    @Override
    public MapCodec<? extends StairBlock> codec() {
        return CODEC;
    }

    protected StairBlock(BlockState baseState, BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, Half.BOTTOM)
                .setValue(SHAPE, StairsShape.STRAIGHT)
                .setValue(WATERLOGGED, false)
        );
        this.base = baseState.getBlock();
        this.baseState = baseState;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean flag = state.getValue(HALF) == Half.BOTTOM;
        Direction direction = state.getValue(FACING);

        Map var10000 = switch ((StairsShape)state.getValue(SHAPE)) {
            case STRAIGHT -> flag ? SHAPE_BOTTOM_STRAIGHT : SHAPE_TOP_STRAIGHT;
            case OUTER_LEFT, OUTER_RIGHT -> flag ? SHAPE_BOTTOM_OUTER : SHAPE_TOP_OUTER;
            case INNER_RIGHT, INNER_LEFT -> flag ? SHAPE_BOTTOM_INNER : SHAPE_TOP_INNER;
        };

        return (VoxelShape)var10000.get(switch ((StairsShape)state.getValue(SHAPE)) {
            case STRAIGHT, OUTER_LEFT, INNER_RIGHT -> direction;
            case INNER_LEFT -> direction.getCounterClockWise();
            case OUTER_RIGHT -> direction.getClockWise();
        });
    }

    @Override
    public float getExplosionResistance() {
        return this.base.getExplosionResistance();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        BlockPos clickedPos = context.getClickedPos();
        FluidState fluidState = context.getLevel().getFluidState(clickedPos);
        BlockState blockState = this.defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection())
            .setValue(
                HALF,
                clickedFace != Direction.DOWN && (clickedFace == Direction.UP || !(context.getClickLocation().y - clickedPos.getY() > 0.5))
                    ? Half.BOTTOM
                    : Half.TOP
            )
            .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
        return blockState.setValue(SHAPE, getStairsShape(blockState, context.getLevel(), clickedPos));
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
            ? state.setValue(SHAPE, getStairsShape(state, level, pos))
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    private static StairsShape getStairsShape(BlockState state, BlockGetter level, BlockPos pos) {
        Direction direction = state.getValue(FACING);
        BlockState blockState = level.getBlockState(pos.relative(direction));
        if (isStairs(blockState) && state.getValue(HALF) == blockState.getValue(HALF)) {
            Direction direction1 = blockState.getValue(FACING);
            if (direction1.getAxis() != state.getValue(FACING).getAxis() && canTakeShape(state, level, pos, direction1.getOpposite())) {
                if (direction1 == direction.getCounterClockWise()) {
                    return StairsShape.OUTER_LEFT;
                }

                return StairsShape.OUTER_RIGHT;
            }
        }

        BlockState blockState1 = level.getBlockState(pos.relative(direction.getOpposite()));
        if (isStairs(blockState1) && state.getValue(HALF) == blockState1.getValue(HALF)) {
            Direction direction2 = blockState1.getValue(FACING);
            if (direction2.getAxis() != state.getValue(FACING).getAxis() && canTakeShape(state, level, pos, direction2)) {
                if (direction2 == direction.getCounterClockWise()) {
                    return StairsShape.INNER_LEFT;
                }

                return StairsShape.INNER_RIGHT;
            }
        }

        return StairsShape.STRAIGHT;
    }

    private static boolean canTakeShape(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        BlockState blockState = level.getBlockState(pos.relative(face));
        return !isStairs(blockState) || blockState.getValue(FACING) != state.getValue(FACING) || blockState.getValue(HALF) != state.getValue(HALF);
    }

    public static boolean isStairs(BlockState state) {
        return state.getBlock() instanceof StairBlock;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction direction = state.getValue(FACING);
        StairsShape stairsShape = state.getValue(SHAPE);
        switch (mirror) {
            case LEFT_RIGHT:
                if (direction.getAxis() == Direction.Axis.Z) {
                    switch (stairsShape) {
                        case OUTER_LEFT:
                            return state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.OUTER_RIGHT);
                        case INNER_RIGHT:
                            return state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.INNER_LEFT);
                        case INNER_LEFT:
                            return state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.INNER_RIGHT);
                        case OUTER_RIGHT:
                            return state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.OUTER_LEFT);
                        default:
                            return state.rotate(Rotation.CLOCKWISE_180);
                    }
                }
                break;
            case FRONT_BACK:
                if (direction.getAxis() == Direction.Axis.X) {
                    switch (stairsShape) {
                        case STRAIGHT:
                            return state.rotate(Rotation.CLOCKWISE_180);
                        case OUTER_LEFT:
                            return state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.OUTER_RIGHT);
                        case INNER_RIGHT:
                            return state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.INNER_RIGHT);
                        case INNER_LEFT:
                            return state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.INNER_LEFT);
                        case OUTER_RIGHT:
                            return state.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.OUTER_LEFT);
                    }
                }
        }

        return super.mirror(state, mirror);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, SHAPE, WATERLOGGED);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
