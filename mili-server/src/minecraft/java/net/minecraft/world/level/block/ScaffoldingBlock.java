package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ScaffoldingBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<ScaffoldingBlock> CODEC = simpleCodec(ScaffoldingBlock::new);
    private static final int TICK_DELAY = 1;
    private static final VoxelShape SHAPE_STABLE = Shapes.or(
        Block.column(16.0, 14.0, 16.0), Shapes.rotateHorizontal(Block.box(0.0, 0.0, 0.0, 2.0, 16.0, 2.0)).values().stream().reduce(Shapes.empty(), Shapes::or)
    );
    private static final VoxelShape SHAPE_UNSTABLE_BOTTOM = Block.column(16.0, 0.0, 2.0);
    private static final VoxelShape SHAPE_UNSTABLE = Shapes.or(
        SHAPE_STABLE, SHAPE_UNSTABLE_BOTTOM, Shapes.rotateHorizontal(Block.boxZ(16.0, 0.0, 2.0, 0.0, 2.0)).values().stream().reduce(Shapes.empty(), Shapes::or)
    );
    private static final VoxelShape SHAPE_BELOW_BLOCK = Shapes.block().move(0.0, -1.0, 0.0).optimize();
    public static final int STABILITY_MAX_DISTANCE = 7;
    public static final IntegerProperty DISTANCE = BlockStateProperties.STABILITY_DISTANCE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty BOTTOM = BlockStateProperties.BOTTOM;

    @Override
    public MapCodec<ScaffoldingBlock> codec() {
        return CODEC;
    }

    protected ScaffoldingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(DISTANCE, 7).setValue(WATERLOGGED, false).setValue(BOTTOM, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DISTANCE, WATERLOGGED, BOTTOM);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!context.isHoldingItem(state.getBlock().asItem())) {
            return state.getValue(BOTTOM) ? SHAPE_UNSTABLE : SHAPE_STABLE;
        } else {
            return Shapes.block();
        }
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return useContext.getItemInHand().is(this.asItem());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos clickedPos = context.getClickedPos();
        Level level = context.getLevel();
        int distance = getDistance(level, clickedPos);
        return this.defaultBlockState()
            .setValue(WATERLOGGED, level.getFluidState(clickedPos).getType() == Fluids.WATER)
            .setValue(DISTANCE, distance)
            .setValue(BOTTOM, this.isBottom(level, clickedPos, distance));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, 1);
        }
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

        if (!level.isClientSide()) {
            scheduledTickAccess.scheduleTick(pos, this, 1);
        }

        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int distance = getDistance(level, pos);
        BlockState blockState = state.setValue(DISTANCE, distance).setValue(BOTTOM, this.isBottom(level, pos, distance));
        if (blockState.getValue(DISTANCE) == 7 && !org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(level, pos, blockState.getFluidState().createLegacyBlock()).isCancelled()) { // CraftBukkit - BlockFadeEvent // Paper - fix wrong block state
            if (state.getValue(DISTANCE) == 7) {
                FallingBlockEntity.fall(level, pos, blockState);
            } else {
                level.destroyBlock(pos, true);
            }
        } else if (state != blockState) {
            level.setBlock(pos, blockState, Block.UPDATE_ALL);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return getDistance(level, pos) < 7;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context.isPlacement()) {
            return Shapes.empty();
        } else if (context.isAbove(Shapes.block(), pos, true) && !context.isDescending()) {
            return SHAPE_STABLE;
        } else {
            return state.getValue(DISTANCE) != 0 && state.getValue(BOTTOM) && context.isAbove(SHAPE_BELOW_BLOCK, pos, true)
                ? SHAPE_UNSTABLE_BOTTOM
                : Shapes.empty();
        }
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    private boolean isBottom(BlockGetter level, BlockPos pos, int distance) {
        return distance > 0 && !level.getBlockState(pos.below()).is(this);
    }

    public static int getDistance(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable().move(Direction.DOWN);
        BlockState blockState = level.getBlockState(mutableBlockPos);
        int i = 7;
        if (blockState.is(Blocks.SCAFFOLDING)) {
            i = blockState.getValue(DISTANCE);
        } else if (blockState.isFaceSturdy(level, mutableBlockPos, Direction.UP)) {
            return 0;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState blockState1 = level.getBlockState(mutableBlockPos.setWithOffset(pos, direction));
            if (blockState1.is(Blocks.SCAFFOLDING)) {
                i = Math.min(i, blockState1.getValue(DISTANCE) + 1);
                if (i == 1) {
                    break;
                }
            }
        }

        return i;
    }
}
