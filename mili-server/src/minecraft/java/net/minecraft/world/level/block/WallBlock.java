package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WallBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<WallBlock> CODEC = simpleCodec(WallBlock::new);
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final EnumProperty<WallSide> EAST = BlockStateProperties.EAST_WALL;
    public static final EnumProperty<WallSide> NORTH = BlockStateProperties.NORTH_WALL;
    public static final EnumProperty<WallSide> SOUTH = BlockStateProperties.SOUTH_WALL;
    public static final EnumProperty<WallSide> WEST = BlockStateProperties.WEST_WALL;
    public static final Map<Direction, EnumProperty<WallSide>> PROPERTY_BY_DIRECTION = ImmutableMap.copyOf(
        Maps.newEnumMap(Map.of(Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST))
    );
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private final Function<BlockState, VoxelShape> shapes;
    private final Function<BlockState, VoxelShape> collisionShapes;
    private static final VoxelShape TEST_SHAPE_POST = Block.column(2.0, 0.0, 16.0);
    private static final Map<Direction, VoxelShape> TEST_SHAPES_WALL = Shapes.rotateHorizontal(Block.boxZ(2.0, 16.0, 0.0, 9.0));

    @Override
    public MapCodec<WallBlock> codec() {
        return CODEC;
    }

    public WallBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(UP, true)
                .setValue(NORTH, WallSide.NONE)
                .setValue(EAST, WallSide.NONE)
                .setValue(SOUTH, WallSide.NONE)
                .setValue(WEST, WallSide.NONE)
                .setValue(WATERLOGGED, false)
        );
        this.shapes = this.makeShapes(16.0F, 14.0F);
        this.collisionShapes = this.makeShapes(24.0F, 24.0F);
    }

    private Function<BlockState, VoxelShape> makeShapes(float height, float width) {
        VoxelShape voxelShape = Block.column(8.0, 0.0, height);
        int i = 6;
        Map<Direction, VoxelShape> map = Shapes.rotateHorizontal(Block.boxZ(6.0, 0.0, width, 0.0, 11.0));
        Map<Direction, VoxelShape> map1 = Shapes.rotateHorizontal(Block.boxZ(6.0, 0.0, height, 0.0, 11.0));
        return this.getShapeForEachState(state -> {
            VoxelShape voxelShape1 = state.getValue(UP) ? voxelShape : Shapes.empty();

            for (Entry<Direction, EnumProperty<WallSide>> entry : PROPERTY_BY_DIRECTION.entrySet()) {
                voxelShape1 = Shapes.or(voxelShape1, switch ((WallSide)state.getValue(entry.getValue())) {
                    case NONE -> Shapes.empty();
                    case LOW -> (VoxelShape)map.get(entry.getKey());
                    case TALL -> (VoxelShape)map1.get(entry.getKey());
                });
            }

            return voxelShape1;
        }, WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.apply(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.collisionShapes.apply(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    private boolean connectsTo(BlockState state, boolean sideSolid, Direction direction) {
        Block block = state.getBlock();
        boolean flag = block instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(state, direction);
        return state.is(BlockTags.WALLS) || !isExceptionForConnection(state) && sideSolid || block instanceof IronBarsBlock || flag;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        BlockPos blockPos = clickedPos.north();
        BlockPos blockPos1 = clickedPos.east();
        BlockPos blockPos2 = clickedPos.south();
        BlockPos blockPos3 = clickedPos.west();
        BlockPos blockPos4 = clickedPos.above();
        BlockState blockState = level.getBlockState(blockPos);
        BlockState blockState1 = level.getBlockState(blockPos1);
        BlockState blockState2 = level.getBlockState(blockPos2);
        BlockState blockState3 = level.getBlockState(blockPos3);
        BlockState blockState4 = level.getBlockState(blockPos4);
        boolean flag = this.connectsTo(blockState, blockState.isFaceSturdy(level, blockPos, Direction.SOUTH), Direction.SOUTH);
        boolean flag1 = this.connectsTo(blockState1, blockState1.isFaceSturdy(level, blockPos1, Direction.WEST), Direction.WEST);
        boolean flag2 = this.connectsTo(blockState2, blockState2.isFaceSturdy(level, blockPos2, Direction.NORTH), Direction.NORTH);
        boolean flag3 = this.connectsTo(blockState3, blockState3.isFaceSturdy(level, blockPos3, Direction.EAST), Direction.EAST);
        BlockState blockState5 = this.defaultBlockState().setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
        return this.updateShape(level, blockState5, blockPos4, blockState4, flag, flag1, flag2, flag3);
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

        if (direction == Direction.DOWN) {
            return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        } else {
            return direction == Direction.UP
                ? this.topUpdate(level, state, neighborPos, neighborState)
                : this.sideUpdate(level, pos, state, neighborPos, neighborState, direction);
        }
    }

    private static boolean isConnected(BlockState state, Property<WallSide> heightProperty) {
        return state.getValue(heightProperty) != WallSide.NONE;
    }

    private static boolean isCovered(VoxelShape shape1, VoxelShape shape2) {
        return !Shapes.joinIsNotEmpty(shape2, shape1, BooleanOp.ONLY_FIRST);
    }

    private BlockState topUpdate(LevelReader level, BlockState state, BlockPos neighborPos, BlockState neighborState) {
        boolean isConnected = isConnected(state, NORTH);
        boolean isConnected1 = isConnected(state, EAST);
        boolean isConnected2 = isConnected(state, SOUTH);
        boolean isConnected3 = isConnected(state, WEST);
        return this.updateShape(level, state, neighborPos, neighborState, isConnected, isConnected1, isConnected2, isConnected3);
    }

    private BlockState sideUpdate(LevelReader level, BlockPos currentPos, BlockState state, BlockPos neighborPos, BlockState neighborState, Direction direction) {
        Direction opposite = direction.getOpposite();
        boolean flag = direction == Direction.NORTH
            ? this.connectsTo(neighborState, neighborState.isFaceSturdy(level, neighborPos, opposite), opposite)
            : isConnected(state, NORTH);
        boolean flag1 = direction == Direction.EAST
            ? this.connectsTo(neighborState, neighborState.isFaceSturdy(level, neighborPos, opposite), opposite)
            : isConnected(state, EAST);
        boolean flag2 = direction == Direction.SOUTH
            ? this.connectsTo(neighborState, neighborState.isFaceSturdy(level, neighborPos, opposite), opposite)
            : isConnected(state, SOUTH);
        boolean flag3 = direction == Direction.WEST
            ? this.connectsTo(neighborState, neighborState.isFaceSturdy(level, neighborPos, opposite), opposite)
            : isConnected(state, WEST);
        BlockPos blockPos = currentPos.above();
        BlockState blockState = level.getBlockState(blockPos);
        return this.updateShape(level, state, blockPos, blockState, flag, flag1, flag2, flag3);
    }

    private BlockState updateShape(
        LevelReader level,
        BlockState state,
        BlockPos pos,
        BlockState neighborState,
        boolean northConnection,
        boolean eastConnection,
        boolean southConnection,
        boolean westConnection
    ) {
        VoxelShape faceShape = neighborState.getCollisionShape(level, pos).getFaceShape(Direction.DOWN);
        BlockState blockState = this.updateSides(state, northConnection, eastConnection, southConnection, westConnection, faceShape);
        return blockState.setValue(UP, this.shouldRaisePost(blockState, neighborState, faceShape));
    }

    private boolean shouldRaisePost(BlockState state, BlockState neighborState, VoxelShape shape) {
        boolean flag = neighborState.getBlock() instanceof WallBlock && neighborState.getValue(UP);
        if (flag) {
            return true;
        } else {
            WallSide wallSide = state.getValue(NORTH);
            WallSide wallSide1 = state.getValue(SOUTH);
            WallSide wallSide2 = state.getValue(EAST);
            WallSide wallSide3 = state.getValue(WEST);
            boolean flag1 = wallSide1 == WallSide.NONE;
            boolean flag2 = wallSide3 == WallSide.NONE;
            boolean flag3 = wallSide2 == WallSide.NONE;
            boolean flag4 = wallSide == WallSide.NONE;
            boolean flag5 = flag4 && flag1 && flag2 && flag3 || flag4 != flag1 || flag2 != flag3;
            if (flag5) {
                return true;
            } else {
                boolean flag6 = wallSide == WallSide.TALL && wallSide1 == WallSide.TALL || wallSide2 == WallSide.TALL && wallSide3 == WallSide.TALL;
                return !flag6 && (neighborState.is(BlockTags.WALL_POST_OVERRIDE) || isCovered(shape, TEST_SHAPE_POST));
            }
        }
    }

    private BlockState updateSides(
        BlockState state, boolean northConnection, boolean eastConnection, boolean southConnection, boolean westConnection, VoxelShape wallShape
    ) {
        return state.setValue(NORTH, this.makeWallState(northConnection, wallShape, TEST_SHAPES_WALL.get(Direction.NORTH)))
            .setValue(EAST, this.makeWallState(eastConnection, wallShape, TEST_SHAPES_WALL.get(Direction.EAST)))
            .setValue(SOUTH, this.makeWallState(southConnection, wallShape, TEST_SHAPES_WALL.get(Direction.SOUTH)))
            .setValue(WEST, this.makeWallState(westConnection, wallShape, TEST_SHAPES_WALL.get(Direction.WEST)));
    }

    private WallSide makeWallState(boolean allowConnection, VoxelShape shape, VoxelShape neighborShape) {
        if (allowConnection) {
            return isCovered(shape, neighborShape) ? WallSide.TALL : WallSide.LOW;
        } else {
            return WallSide.NONE;
        }
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return !state.getValue(WATERLOGGED);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, NORTH, EAST, WEST, SOUTH, WATERLOGGED);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                return state.setValue(NORTH, state.getValue(SOUTH))
                    .setValue(EAST, state.getValue(WEST))
                    .setValue(SOUTH, state.getValue(NORTH))
                    .setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90:
                return state.setValue(NORTH, state.getValue(EAST))
                    .setValue(EAST, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(WEST))
                    .setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90:
                return state.setValue(NORTH, state.getValue(WEST))
                    .setValue(EAST, state.getValue(NORTH))
                    .setValue(SOUTH, state.getValue(EAST))
                    .setValue(WEST, state.getValue(SOUTH));
            default:
                return state;
        }
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK:
                return state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default:
                return super.mirror(state, mirror);
        }
    }
}
