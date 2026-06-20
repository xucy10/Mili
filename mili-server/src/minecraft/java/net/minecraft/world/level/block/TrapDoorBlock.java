package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class TrapDoorBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<TrapDoorBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(BlockSetType.CODEC.fieldOf("block_set_type").forGetter(trapDoorBlock -> trapDoorBlock.type), propertiesCodec())
            .apply(instance, TrapDoorBlock::new)
    );
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateAll(Block.boxZ(16.0, 13.0, 16.0));
    private final BlockSetType type;

    @Override
    public MapCodec<? extends TrapDoorBlock> codec() {
        return CODEC;
    }

    protected TrapDoorBlock(BlockSetType type, BlockBehaviour.Properties properties) {
        super(properties.sound(type.soundType()));
        this.type = type;
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(HALF, Half.BOTTOM)
                .setValue(POWERED, false)
                .setValue(WATERLOGGED, false)
        );
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(OPEN) ? state.getValue(FACING) : (state.getValue(HALF) == Half.TOP ? Direction.DOWN : Direction.UP));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        switch (pathComputationType) {
            case LAND:
                return state.getValue(OPEN);
            case WATER:
                return state.getValue(WATERLOGGED);
            case AIR:
                return state.getValue(OPEN);
            default:
                return false;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!this.type.canOpenByHand()) {
            return InteractionResult.PASS;
        } else {
            this.toggle(state, level, pos, player);
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (explosion.canTriggerBlocks() && this.type.canOpenByWindCharge() && !state.getValue(POWERED)) {
            this.toggle(state, level, pos, null);
        }

        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }

    private void toggle(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        BlockState blockState = state.cycle(OPEN);
        level.setBlock(pos, blockState, Block.UPDATE_CLIENTS);
        if (blockState.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        this.playSound(player, level, pos, blockState.getValue(OPEN));
    }

    protected void playSound(@Nullable Player player, Level level, BlockPos pos, boolean isOpened) {
        level.playSound(
            player, pos, isOpened ? this.type.trapdoorOpen() : this.type.trapdoorClose(), SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F
        );
        level.gameEvent(player, isOpened ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            boolean hasNeighborSignal = level.hasNeighborSignal(pos);
            if (hasNeighborSignal != state.getValue(POWERED)) {
                // CraftBukkit start
                org.bukkit.block.Block bblock = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
                int power = bblock.getBlockPower();
                int oldPower = state.getValue(TrapDoorBlock.OPEN) ? 15 : 0;

                if (oldPower == 0 ^ power == 0 || neighborBlock.defaultBlockState().isSignalSource()) {
                    org.bukkit.event.block.BlockRedstoneEvent eventRedstone = new org.bukkit.event.block.BlockRedstoneEvent(bblock, oldPower, power);
                    level.getCraftServer().getPluginManager().callEvent(eventRedstone);
                    hasNeighborSignal = eventRedstone.getNewCurrent() > 0;
                }
                // CraftBukkit end
                // Paper start - break redstone on trapdoors early
                boolean open = state.getValue(TrapDoorBlock.OPEN) != hasNeighborSignal;
                // note: this must run before any state for this block/its neighbours are written to the world
                // we allow the redstone event to fire so that plugins can block
                if (hasNeighborSignal && open) { // if we are now powered and it caused the trap door to open
                    // in this case, first check for the redstone on top first
                    BlockPos abovePos = pos.above();
                    BlockState above = level.getBlockState(abovePos);
                    if (above.getBlock() instanceof RedStoneWireBlock) {
                        level.setBlock(abovePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                        Block.popResource(level, abovePos, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE));
                        // now check that this didn't change our state
                        if (level.getBlockState(pos) != state) {
                            // our state was changed, so we cannot propagate this update
                            return;
                        }
                    }
                }
                if (open) {
                    // Paper end - break redstone on trapdoors early
                    state = state.setValue(OPEN, hasNeighborSignal);
                    this.playSound(null, level, pos, hasNeighborSignal);
                }

                level.setBlock(pos, state.setValue(POWERED, hasNeighborSignal), Block.UPDATE_CLIENTS);
                if (state.getValue(WATERLOGGED)) {
                    level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
                }
            }
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockState = this.defaultBlockState();
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        Direction clickedFace = context.getClickedFace();
        if (!context.replacingClickedOnBlock() && clickedFace.getAxis().isHorizontal()) {
            blockState = blockState.setValue(FACING, clickedFace)
                .setValue(HALF, context.getClickLocation().y - context.getClickedPos().getY() > 0.5 ? Half.TOP : Half.BOTTOM);
        } else {
            blockState = blockState.setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, clickedFace == Direction.UP ? Half.BOTTOM : Half.TOP);
        }

        if (context.getLevel().hasNeighborSignal(context.getClickedPos())) {
            blockState = blockState.setValue(OPEN, true).setValue(POWERED, true);
        }

        return blockState.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, HALF, POWERED, WATERLOGGED);
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

    protected BlockSetType getType() {
        return this.type;
    }
}
