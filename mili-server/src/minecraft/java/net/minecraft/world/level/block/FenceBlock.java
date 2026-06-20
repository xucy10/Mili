package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FenceBlock extends CrossCollisionBlock {
    public static final MapCodec<FenceBlock> CODEC = simpleCodec(FenceBlock::new);
    private final Function<BlockState, VoxelShape> occlusionShapes;

    @Override
    public MapCodec<FenceBlock> codec() {
        return CODEC;
    }

    public FenceBlock(BlockBehaviour.Properties properties) {
        super(4.0F, 16.0F, 4.0F, 16.0F, 24.0F, properties);
        this.registerDefaultState(
            this.stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false).setValue(WATERLOGGED, false)
        );
        this.occlusionShapes = this.makeShapes(4.0F, 16.0F, 2.0F, 6.0F, 15.0F);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return this.occlusionShapes.apply(state);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    public boolean connectsTo(BlockState state, boolean isSideSolid, Direction direction) {
        Block block = state.getBlock();
        boolean isSameFence = this.isSameFence(state);
        boolean flag = block instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(state, direction);
        return !isExceptionForConnection(state) && isSideSolid || isSameFence || flag;
    }

    private boolean isSameFence(BlockState state) {
        return state.is(BlockTags.FENCES) && state.is(BlockTags.WOODEN_FENCES) == this.defaultBlockState().is(BlockTags.WOODEN_FENCES);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return (InteractionResult)(!level.isClientSide() ? LeadItem.bindPlayerMobs(player, level, pos) : InteractionResult.PASS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        BlockPos blockPos = clickedPos.north();
        BlockPos blockPos1 = clickedPos.east();
        BlockPos blockPos2 = clickedPos.south();
        BlockPos blockPos3 = clickedPos.west();
        BlockState blockState = level.getBlockState(blockPos);
        BlockState blockState1 = level.getBlockState(blockPos1);
        BlockState blockState2 = level.getBlockState(blockPos2);
        BlockState blockState3 = level.getBlockState(blockPos3);
        return super.getStateForPlacement(context)
            .setValue(NORTH, this.connectsTo(blockState, blockState.isFaceSturdy(level, blockPos, Direction.SOUTH), Direction.SOUTH))
            .setValue(EAST, this.connectsTo(blockState1, blockState1.isFaceSturdy(level, blockPos1, Direction.WEST), Direction.WEST))
            .setValue(SOUTH, this.connectsTo(blockState2, blockState2.isFaceSturdy(level, blockPos2, Direction.NORTH), Direction.NORTH))
            .setValue(WEST, this.connectsTo(blockState3, blockState3.isFaceSturdy(level, blockPos3, Direction.EAST), Direction.EAST))
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
                PROPERTY_BY_DIRECTION.get(direction),
                this.connectsTo(neighborState, neighborState.isFaceSturdy(level, neighborPos, direction.getOpposite()), direction.getOpposite())
            )
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, WEST, SOUTH, WATERLOGGED);
    }
}
