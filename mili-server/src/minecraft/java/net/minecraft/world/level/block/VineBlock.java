package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class VineBlock extends Block {
    public static final MapCodec<VineBlock> CODEC = simpleCodec(VineBlock::new);
    public static final BooleanProperty UP = PipeBlock.UP;
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION
        .entrySet()
        .stream()
        .filter(entry -> entry.getKey() != Direction.DOWN)
        .collect(Util.toMap());
    private final Function<BlockState, VoxelShape> shapes;

    @Override
    public MapCodec<VineBlock> codec() {
        return CODEC;
    }

    public VineBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition.any().setValue(UP, false).setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
        );
        this.shapes = this.makeShapes();
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        Map<Direction, VoxelShape> map = Shapes.rotateAll(Block.boxZ(16.0, 0.0, 1.0));
        return this.getShapeForEachState(blockState -> {
            VoxelShape voxelShape = Shapes.empty();

            for (Entry<Direction, BooleanProperty> entry : PROPERTY_BY_DIRECTION.entrySet()) {
                if (blockState.getValue(entry.getValue())) {
                    voxelShape = Shapes.or(voxelShape, map.get(entry.getKey()));
                }
            }

            return voxelShape.isEmpty() ? Shapes.block() : voxelShape;
        });
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.apply(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return this.hasFaces(this.getUpdatedState(state, level, pos));
    }

    private boolean hasFaces(BlockState state) {
        return this.countFaces(state) > 0;
    }

    private int countFaces(BlockState state) {
        int i = 0;

        for (BooleanProperty booleanProperty : PROPERTY_BY_DIRECTION.values()) {
            if (state.getValue(booleanProperty)) {
                i++;
            }
        }

        return i;
    }

    private boolean canSupportAtFace(BlockGetter level, BlockPos pos, Direction direction) {
        if (direction == Direction.DOWN) {
            return false;
        } else {
            BlockPos blockPos = pos.relative(direction);
            if (isAcceptableNeighbour(level, blockPos, direction)) {
                return true;
            } else if (direction.getAxis() == Direction.Axis.Y) {
                return false;
            } else {
                BooleanProperty booleanProperty = PROPERTY_BY_DIRECTION.get(direction);
                BlockState blockState = level.getBlockState(pos.above());
                return blockState.is(this) && blockState.getValue(booleanProperty);
            }
        }
    }

    public static boolean isAcceptableNeighbour(BlockGetter level, BlockPos neighborPos, Direction attachedFace) {
        return MultifaceBlock.canAttachTo(level, attachedFace, neighborPos, level.getBlockState(neighborPos));
    }

    private BlockState getUpdatedState(BlockState state, BlockGetter level, BlockPos pos) {
        BlockPos blockPos = pos.above();
        if (state.getValue(UP)) {
            state = state.setValue(UP, isAcceptableNeighbour(level, blockPos, Direction.DOWN));
        }

        BlockState blockState = null;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BooleanProperty propertyForFace = getPropertyForFace(direction);
            if (state.getValue(propertyForFace)) {
                boolean canSupportAtFace = this.canSupportAtFace(level, pos, direction);
                if (!canSupportAtFace) {
                    if (blockState == null) {
                        blockState = level.getBlockState(blockPos);
                    }

                    canSupportAtFace = blockState.is(this) && blockState.getValue(propertyForFace);
                }

                state = state.setValue(propertyForFace, canSupportAtFace);
            }
        }

        return state;
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
        if (direction == Direction.DOWN) {
            return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        } else {
            BlockState updatedState = this.getUpdatedState(state, level, pos);
            return !this.hasFaces(updatedState) ? Blocks.AIR.defaultBlockState() : updatedState;
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getGameRules().get(GameRules.SPREAD_VINES)) {
            if (random.nextFloat() < (level.spigotConfig.vineModifier / (100.0F * 4))) { // Spigot - SPIGOT-7159: Better modifier resolution
                Direction random1 = Direction.getRandom(random);
                BlockPos blockPos = pos.above();
                if (random1.getAxis().isHorizontal() && !state.getValue(getPropertyForFace(random1))) {
                    if (this.canSpread(level, pos)) {
                        BlockPos blockPos1 = pos.relative(random1);
                        BlockState blockState = level.getBlockState(blockPos1);
                        if (blockState.isAir()) {
                            Direction clockWise = random1.getClockWise();
                            Direction counterClockWise = random1.getCounterClockWise();
                            boolean value = state.getValue(getPropertyForFace(clockWise));
                            boolean value1 = state.getValue(getPropertyForFace(counterClockWise));
                            BlockPos blockPos2 = blockPos1.relative(clockWise);
                            BlockPos blockPos3 = blockPos1.relative(counterClockWise);
                            // CraftBukkit start - Call BlockSpreadEvent
                            BlockPos source = pos;
                            if (value && isAcceptableNeighbour(level, blockPos2, clockWise)) {
                                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, source, blockPos1, this.defaultBlockState().setValue(getPropertyForFace(clockWise), true), Block.UPDATE_CLIENTS);
                            } else if (value1 && isAcceptableNeighbour(level, blockPos3, counterClockWise)) {
                                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, source, blockPos1, this.defaultBlockState().setValue(getPropertyForFace(counterClockWise), true), Block.UPDATE_CLIENTS);
                            } else {
                                Direction opposite = random1.getOpposite();
                                if (value && level.isEmptyBlock(blockPos2) && isAcceptableNeighbour(level, pos.relative(clockWise), opposite)) {
                                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, source, blockPos2, this.defaultBlockState().setValue(getPropertyForFace(opposite), true), Block.UPDATE_CLIENTS);
                                } else if (value1 && level.isEmptyBlock(blockPos3) && isAcceptableNeighbour(level, pos.relative(counterClockWise), opposite)) {
                                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, source, blockPos3, this.defaultBlockState().setValue(getPropertyForFace(opposite), true), Block.UPDATE_CLIENTS);
                                } else if (random.nextFloat() < 0.05 && isAcceptableNeighbour(level, blockPos1.above(), Direction.UP)) {
                                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, source, blockPos1, this.defaultBlockState().setValue(UP, true), Block.UPDATE_CLIENTS);
                                }
                                // CraftBukkit end
                            }
                        } else if (isAcceptableNeighbour(level, blockPos1, random1)) {
                            org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, pos, (BlockState) state.setValue(VineBlock.getPropertyForFace(random1), true), Block.UPDATE_CLIENTS); // CraftBukkit
                        }
                    }
                } else {
                    if (random1 == Direction.UP && pos.getY() < level.getMaxY()) {
                        if (this.canSupportAtFace(level, pos, random1)) {
                            org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, pos, state.setValue(UP, true), Block.UPDATE_CLIENTS); // CraftBukkit
                            return;
                        }

                        if (level.isEmptyBlock(blockPos)) {
                            if (!this.canSpread(level, pos)) {
                                return;
                            }

                            BlockState blockState1 = state;

                            for (Direction clockWise : Direction.Plane.HORIZONTAL) {
                                if (random.nextBoolean() || !isAcceptableNeighbour(level, blockPos.relative(clockWise), clockWise)) {
                                    blockState1 = blockState1.setValue(getPropertyForFace(clockWise), false);
                                }
                            }

                            if (this.hasHorizontalConnection(blockState1)) {
                                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, pos, blockPos, blockState1, Block.UPDATE_CLIENTS); // CraftBukkit
                            }

                            return;
                        }
                    }

                    if (pos.getY() > level.getMinY()) {
                        BlockPos blockPos1 = pos.below();
                        BlockState blockState = level.getBlockState(blockPos1);
                        if (blockState.isAir() || blockState.is(this)) {
                            BlockState blockState2 = blockState.isAir() ? this.defaultBlockState() : blockState;
                            BlockState blockState3 = this.copyRandomFaces(state, blockState2, random);
                            if (blockState2 != blockState3 && this.hasHorizontalConnection(blockState3)) {
                                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, pos, blockPos1, blockState3, Block.UPDATE_CLIENTS); // CraftBukkit
                            }
                        }
                    }
                }
            }
        }
    }

    private BlockState copyRandomFaces(BlockState sourceState, BlockState spreadState, RandomSource random) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (random.nextBoolean()) {
                BooleanProperty propertyForFace = getPropertyForFace(direction);
                if (sourceState.getValue(propertyForFace)) {
                    spreadState = spreadState.setValue(propertyForFace, true);
                }
            }
        }

        return spreadState;
    }

    private boolean hasHorizontalConnection(BlockState state) {
        return state.getValue(NORTH) || state.getValue(EAST) || state.getValue(SOUTH) || state.getValue(WEST);
    }

    private boolean canSpread(BlockGetter level, BlockPos pos) {
        int i = 4;
        Iterable<BlockPos> iterable = BlockPos.betweenClosed(pos.getX() - 4, pos.getY() - 1, pos.getZ() - 4, pos.getX() + 4, pos.getY() + 1, pos.getZ() + 4);
        int i1 = 5;

        for (BlockPos blockPos : iterable) {
            if (level.getBlockState(blockPos).is(this)) {
                if (--i1 <= 0) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        BlockState blockState = useContext.getLevel().getBlockState(useContext.getClickedPos());
        return blockState.is(this) ? this.countFaces(blockState) < PROPERTY_BY_DIRECTION.size() : super.canBeReplaced(state, useContext);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos());
        boolean isBlock = blockState.is(this);
        BlockState blockState1 = isBlock ? blockState : this.defaultBlockState();

        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction != Direction.DOWN) {
                BooleanProperty propertyForFace = getPropertyForFace(direction);
                boolean flag = isBlock && blockState.getValue(propertyForFace);
                if (!flag && this.canSupportAtFace(context.getLevel(), context.getClickedPos(), direction)) {
                    return blockState1.setValue(propertyForFace, true);
                }
            }
        }

        return isBlock ? blockState1 : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, NORTH, EAST, SOUTH, WEST);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotate) {
        switch (rotate) {
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

    public static BooleanProperty getPropertyForFace(Direction face) {
        return PROPERTY_BY_DIRECTION.get(face);
    }
}
