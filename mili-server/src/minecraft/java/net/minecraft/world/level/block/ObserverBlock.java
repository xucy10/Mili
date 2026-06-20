package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;

public class ObserverBlock extends DirectionalBlock {
    public static final MapCodec<ObserverBlock> CODEC = simpleCodec(ObserverBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    @Override
    public MapCodec<ObserverBlock> codec() {
        return CODEC;
    }

    public ObserverBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(level, pos, 15, 0).getNewCurrent() != 0) {
                return;
            }
            // CraftBukkit end
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_CLIENTS);
        } else {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(level, pos, 0, 15).getNewCurrent() != 15) {
                return;
            }
            // CraftBukkit end
            level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, 2);
        }

        this.updateNeighborsInFront(level, pos, state);
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
        if (state.getValue(FACING) == direction && !state.getValue(POWERED)) {
            this.startSignal(level, scheduledTickAccess, pos);
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    private void startSignal(LevelReader level, ScheduledTickAccess scheduledTickAccess, BlockPos pos) {
        if (!level.isClientSide() && !scheduledTickAccess.getBlockTicks().hasScheduledTick(pos, this)) {
            scheduledTickAccess.scheduleTick(pos, this, 2);
        }
    }

    protected void updateNeighborsInFront(Level level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(FACING);
        BlockPos blockPos = pos.relative(direction.getOpposite());
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, direction.getOpposite(), null);
        level.neighborChanged(blockPos, this, orientation);
        level.updateNeighborsAtExceptFromFacing(blockPos, this, direction, orientation);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getSignal(level, pos, side);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWERED) && state.getValue(FACING) == side ? 15 : 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!state.is(oldState.getBlock())) {
            if (!level.isClientSide() && state.getValue(POWERED) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                BlockState blockState = state.setValue(POWERED, false);
                level.setBlock(pos, blockState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                this.updateNeighborsInFront(level, pos, blockState);
            }
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (state.getValue(POWERED) && level.getBlockTicks().hasScheduledTick(pos, this)) {
            this.updateNeighborsInFront(level, pos, state.setValue(POWERED, false));
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite().getOpposite());
    }
}
