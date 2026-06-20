package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CopperGolemStatueBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class CopperGolemStatueBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<CopperGolemStatueBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(CopperGolemStatueBlock::getWeatheringState), propertiesCodec()
            )
            .apply(instance, CopperGolemStatueBlock::new)
    );
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<CopperGolemStatueBlock.Pose> POSE = BlockStateProperties.COPPER_GOLEM_POSE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Block.column(10.0, 0.0, 14.0);
    private final WeatheringCopper.WeatherState weatheringState;

    @Override
    public MapCodec<? extends CopperGolemStatueBlock> codec() {
        return CODEC;
    }

    public CopperGolemStatueBlock(WeatheringCopper.WeatherState weatheringState, BlockBehaviour.Properties properties) {
        super(properties);
        this.weatheringState = weatheringState;
        this.registerDefaultState(
            this.defaultBlockState().setValue(FACING, Direction.NORTH).setValue(POSE, CopperGolemStatueBlock.Pose.STANDING).setValue(WATERLOGGED, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, POSE, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public WeatheringCopper.WeatherState getWeatheringState() {
        return this.weatheringState;
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (stack.is(ItemTags.AXES)) {
            return InteractionResult.PASS;
        } else {
            return this.updatePose(level, state, pos, player); // Paper - call EntityChangeBlockEvent
        }
    }

    // Paper start - call EntityChangeBlockEvent
    InteractionResult updatePose(Level level, BlockState state, BlockPos pos, Player player) {
        BlockState newState = state.setValue(POSE, state.getValue(POSE).getNextPose());
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(player, pos, newState)) {
            return InteractionResult.PASS;
        }
        level.playSound(null, pos, SoundEvents.COPPER_GOLEM_BECOME_STATUE, SoundSource.BLOCKS);
        level.setBlock(pos, newState, Block.UPDATE_ALL);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        return InteractionResult.SUCCESS;
        // Paper end - call EntityChangeBlockEvent
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return pathComputationType == PathComputationType.WATER && state.getFluidState().is(FluidTags.WATER);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CopperGolemStatueBlockEntity(pos, state);
    }

    @Override
    public boolean shouldChangedStateKeepBlockEntity(BlockState state) {
        return state.is(BlockTags.COPPER_GOLEM_STATUES);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return state.getValue(POSE).ordinal() + 1;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return level.getBlockEntity(pos) instanceof CopperGolemStatueBlockEntity copperGolemStatueBlockEntity
            ? copperGolemStatueBlockEntity.getItem(this.asItem().getDefaultInstance(), state.getValue(POSE))
            : super.getCloneItemStack(level, pos, state, includeData);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        level.updateNeighbourForOutputSignal(pos, state.getBlock());
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
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

    public static enum Pose implements StringRepresentable {
        STANDING("standing"),
        SITTING("sitting"),
        RUNNING("running"),
        STAR("star");

        public static final IntFunction<CopperGolemStatueBlock.Pose> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final Codec<CopperGolemStatueBlock.Pose> CODEC = StringRepresentable.fromEnum(CopperGolemStatueBlock.Pose::values);
        private final String name;

        private Pose(final String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public CopperGolemStatueBlock.Pose getNextPose() {
            return BY_ID.apply(this.ordinal() + 1);
        }
    }
}
