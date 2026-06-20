package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class WallHangingSignBlock extends SignBlock {
    public static final MapCodec<WallHangingSignBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(WoodType.CODEC.fieldOf("wood_type").forGetter(SignBlock::type), propertiesCodec())
            .apply(instance, WallHangingSignBlock::new)
    );
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    private static final Map<Direction.Axis, VoxelShape> SHAPES_PLANK = Shapes.rotateHorizontalAxis(Block.column(16.0, 4.0, 14.0, 16.0));
    private static final Map<Direction.Axis, VoxelShape> SHAPES = Shapes.rotateHorizontalAxis(
        Shapes.or(SHAPES_PLANK.get(Direction.Axis.Z), Block.column(14.0, 2.0, 0.0, 10.0))
    );

    @Override
    public MapCodec<WallHangingSignBlock> codec() {
        return CODEC;
    }

    public WallHangingSignBlock(WoodType type, BlockBehaviour.Properties properties) {
        super(type, properties.sound(type.hangingSignSoundType()));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        return (InteractionResult)(level.getBlockEntity(pos) instanceof SignBlockEntity signBlockEntity
                && this.shouldTryToChainAnotherHangingSign(state, player, hitResult, signBlockEntity, stack)
            ? InteractionResult.PASS
            : super.useItemOn(stack, state, level, pos, player, hand, hitResult));
    }

    private boolean shouldTryToChainAnotherHangingSign(BlockState state, Player player, BlockHitResult hitResult, SignBlockEntity sign, ItemStack stack) {
        return !sign.canExecuteClickCommands(sign.isFacingFrontText(player), player)
            && stack.getItem() instanceof HangingSignItem
            && !this.isHittingEditableSide(hitResult, state);
    }

    private boolean isHittingEditableSide(BlockHitResult hitResult, BlockState state) {
        return hitResult.getDirection().getAxis() == state.getValue(FACING).getAxis();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING).getAxis());
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return this.getShape(state, level, pos, CollisionContext.empty());
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES_PLANK.get(state.getValue(FACING).getAxis());
    }

    public boolean canPlace(BlockState state, LevelReader level, BlockPos pos) {
        Direction clockWise = state.getValue(FACING).getClockWise();
        Direction counterClockWise = state.getValue(FACING).getCounterClockWise();
        return this.canAttachTo(level, state, pos.relative(clockWise), counterClockWise)
            || this.canAttachTo(level, state, pos.relative(counterClockWise), clockWise);
    }

    public boolean canAttachTo(LevelReader level, BlockState state, BlockPos pos, Direction direction) {
        BlockState blockState = level.getBlockState(pos);
        return blockState.is(BlockTags.WALL_HANGING_SIGNS)
            ? blockState.getValue(FACING).getAxis().test(state.getValue(FACING))
            : blockState.isFaceSturdy(level, pos, direction, SupportType.FULL);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockState = this.defaultBlockState();
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        LevelReader level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();

        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis().isHorizontal() && !direction.getAxis().test(context.getClickedFace())) {
                Direction opposite = direction.getOpposite();
                blockState = blockState.setValue(FACING, opposite);
                if (blockState.canSurvive(level, clickedPos) && this.canPlace(blockState, level, clickedPos)) {
                    return blockState.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
                }
            }
        }

        return null;
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
        return direction.getAxis() == state.getValue(FACING).getClockWise().getAxis() && !state.canSurvive(level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public float getYRotationDegrees(BlockState state) {
        return state.getValue(FACING).toYRot();
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HangingSignBlockEntity(pos, state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return null; // CraftBukkit - remove unnecessary sign ticking
    }
}
