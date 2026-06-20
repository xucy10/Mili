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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public abstract class BaseRailBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE_FLAT = Block.column(16.0, 0.0, 2.0);
    private static final VoxelShape SHAPE_SLOPE = Block.column(16.0, 0.0, 8.0);
    private final boolean isStraight;

    public static boolean isRail(Level level, BlockPos pos) {
        return isRail(level.getBlockState(pos));
    }

    public static boolean isRail(BlockState state) {
        return state.is(BlockTags.RAILS) && state.getBlock() instanceof BaseRailBlock;
    }

    protected BaseRailBlock(boolean isStraight, BlockBehaviour.Properties properties) {
        super(properties);
        this.isStraight = isStraight;
    }

    @Override
    protected abstract MapCodec<? extends BaseRailBlock> codec();

    public boolean isStraight() {
        return this.isStraight;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(this.getShapeProperty()).isSlope() ? SHAPE_SLOPE : SHAPE_FLAT;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportRigidBlock(level, pos.below());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            this.updateState(state, level, pos, movedByPiston);
        }
    }

    protected BlockState updateState(BlockState state, Level level, BlockPos pos, boolean movedByPiston) {
        state = this.updateDir(level, pos, state, true);
        if (this.isStraight) {
            level.neighborChanged(state, pos, this, null, movedByPiston);
            state = level.getBlockState(pos); // Paper - Fix some rails connecting improperly
        }

        return state;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide() && level.getBlockState(pos).is(this)) {
            RailShape railShape = state.getValue(this.getShapeProperty());
            if (shouldBeRemoved(pos, level, railShape)) {
                dropResources(state, level, pos);
                level.removeBlock(pos, movedByPiston);
            } else {
                this.updateState(state, level, pos, neighborBlock);
            }
        }
    }

    private static boolean shouldBeRemoved(BlockPos pos, Level level, RailShape shape) {
        if (!canSupportRigidBlock(level, pos.below())) {
            return true;
        } else {
            switch (shape) {
                case ASCENDING_EAST:
                    return !canSupportRigidBlock(level, pos.east());
                case ASCENDING_WEST:
                    return !canSupportRigidBlock(level, pos.west());
                case ASCENDING_NORTH:
                    return !canSupportRigidBlock(level, pos.north());
                case ASCENDING_SOUTH:
                    return !canSupportRigidBlock(level, pos.south());
                default:
                    return false;
            }
        }
    }

    protected void updateState(BlockState state, Level level, BlockPos pos, Block neighborBlock) {
    }

    protected BlockState updateDir(Level level, BlockPos pos, BlockState state, boolean alwaysPlace) {
        if (level.isClientSide()) {
            return state;
        } else {
            RailShape railShape = state.getValue(this.getShapeProperty());
            return new RailState(level, pos, state).place(level.hasNeighborSignal(pos), alwaysPlace, railShape).getState();
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston) {
            if (state.getValue(this.getShapeProperty()).isSlope()) {
                level.updateNeighborsAt(pos.above(), this);
            }

            if (this.isStraight) {
                level.updateNeighborsAt(pos, this);
                level.updateNeighborsAt(pos.below(), this);
            }
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        boolean flag = fluidState.getType() == Fluids.WATER;
        BlockState blockState = super.defaultBlockState();
        Direction horizontalDirection = context.getHorizontalDirection();
        boolean flag1 = horizontalDirection == Direction.EAST || horizontalDirection == Direction.WEST;
        return blockState.setValue(this.getShapeProperty(), flag1 ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH).setValue(WATERLOGGED, flag);
    }

    public abstract Property<RailShape> getShapeProperty();

    protected RailShape rotate(RailShape railShape, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_180 -> {
                switch (railShape) {
                    case ASCENDING_EAST:
                        yield RailShape.ASCENDING_WEST;
                    case ASCENDING_WEST:
                        yield RailShape.ASCENDING_EAST;
                    case ASCENDING_NORTH:
                        yield RailShape.ASCENDING_SOUTH;
                    case ASCENDING_SOUTH:
                        yield RailShape.ASCENDING_NORTH;
                    case NORTH_SOUTH:
                        yield RailShape.NORTH_SOUTH;
                    case EAST_WEST:
                        yield RailShape.EAST_WEST;
                    case SOUTH_EAST:
                        yield RailShape.NORTH_WEST;
                    case SOUTH_WEST:
                        yield RailShape.NORTH_EAST;
                    case NORTH_WEST:
                        yield RailShape.SOUTH_EAST;
                    case NORTH_EAST:
                        yield RailShape.SOUTH_WEST;
                    default:
                        throw new MatchException(null, null);
                }
            }
            case COUNTERCLOCKWISE_90 -> {
                switch (railShape) {
                    case ASCENDING_EAST:
                        yield RailShape.ASCENDING_NORTH;
                    case ASCENDING_WEST:
                        yield RailShape.ASCENDING_SOUTH;
                    case ASCENDING_NORTH:
                        yield RailShape.ASCENDING_WEST;
                    case ASCENDING_SOUTH:
                        yield RailShape.ASCENDING_EAST;
                    case NORTH_SOUTH:
                        yield RailShape.EAST_WEST;
                    case EAST_WEST:
                        yield RailShape.NORTH_SOUTH;
                    case SOUTH_EAST:
                        yield RailShape.NORTH_EAST;
                    case SOUTH_WEST:
                        yield RailShape.SOUTH_EAST;
                    case NORTH_WEST:
                        yield RailShape.SOUTH_WEST;
                    case NORTH_EAST:
                        yield RailShape.NORTH_WEST;
                    default:
                        throw new MatchException(null, null);
                }
            }
            case CLOCKWISE_90 -> {
                switch (railShape) {
                    case ASCENDING_EAST:
                        yield RailShape.ASCENDING_SOUTH;
                    case ASCENDING_WEST:
                        yield RailShape.ASCENDING_NORTH;
                    case ASCENDING_NORTH:
                        yield RailShape.ASCENDING_EAST;
                    case ASCENDING_SOUTH:
                        yield RailShape.ASCENDING_WEST;
                    case NORTH_SOUTH:
                        yield RailShape.EAST_WEST;
                    case EAST_WEST:
                        yield RailShape.NORTH_SOUTH;
                    case SOUTH_EAST:
                        yield RailShape.SOUTH_WEST;
                    case SOUTH_WEST:
                        yield RailShape.NORTH_WEST;
                    case NORTH_WEST:
                        yield RailShape.NORTH_EAST;
                    case NORTH_EAST:
                        yield RailShape.SOUTH_EAST;
                    default:
                        throw new MatchException(null, null);
                }
            }
            default -> railShape;
        };
    }

    protected RailShape mirror(RailShape railShape, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> {
                switch (railShape) {
                    case ASCENDING_NORTH:
                        yield RailShape.ASCENDING_SOUTH;
                    case ASCENDING_SOUTH:
                        yield RailShape.ASCENDING_NORTH;
                    case NORTH_SOUTH:
                    case EAST_WEST:
                    default:
                        yield railShape;
                    case SOUTH_EAST:
                        yield RailShape.NORTH_EAST;
                    case SOUTH_WEST:
                        yield RailShape.NORTH_WEST;
                    case NORTH_WEST:
                        yield RailShape.SOUTH_WEST;
                    case NORTH_EAST:
                        yield RailShape.SOUTH_EAST;
                }
            }
            case FRONT_BACK -> {
                switch (railShape) {
                    case ASCENDING_EAST:
                        yield RailShape.ASCENDING_WEST;
                    case ASCENDING_WEST:
                        yield RailShape.ASCENDING_EAST;
                    case ASCENDING_NORTH:
                    case ASCENDING_SOUTH:
                    case NORTH_SOUTH:
                    case EAST_WEST:
                    default:
                        yield railShape;
                    case SOUTH_EAST:
                        yield RailShape.SOUTH_WEST;
                    case SOUTH_WEST:
                        yield RailShape.SOUTH_EAST;
                    case NORTH_WEST:
                        yield RailShape.NORTH_EAST;
                    case NORTH_EAST:
                        yield RailShape.NORTH_WEST;
                }
            }
            default -> railShape;
        };
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

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
}
