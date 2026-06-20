package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.SideChainPart;
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

public class ShelfBlock extends BaseEntityBlock implements SelectableSlotContainer, SideChainPartBlock, SimpleWaterloggedBlock {
    public static final MapCodec<ShelfBlock> CODEC = simpleCodec(ShelfBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<SideChainPart> SIDE_CHAIN_PART = BlockStateProperties.SIDE_CHAIN_PART;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(
        Shapes.or(Block.box(0.0, 12.0, 11.0, 16.0, 16.0, 13.0), Block.box(0.0, 0.0, 13.0, 16.0, 16.0, 16.0), Block.box(0.0, 0.0, 11.0, 16.0, 4.0, 13.0))
    );

    @Override
    public MapCodec<ShelfBlock> codec() {
        return CODEC;
    }

    public ShelfBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(SIDE_CHAIN_PART, SideChainPart.UNCONNECTED)
                .setValue(WATERLOGGED, false)
        );
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return pathComputationType == PathComputationType.WATER && state.getFluidState().is(FluidTags.WATER);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShelfBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, SIDE_CHAIN_PART, WATERLOGGED);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
        this.updateNeighborsAfterPoweringDown(level, pos, state);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            boolean hasNeighborSignal = level.hasNeighborSignal(pos);
            if (state.getValue(POWERED) != hasNeighborSignal) {
                // Paper start - Call BlockRedstoneEvent
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBinaryRedstoneChange(level, pos, hasNeighborSignal)) {
                    return;
                }
                // Paper end - Call BlockRedstoneEvent
                BlockState blockState = state.setValue(POWERED, hasNeighborSignal);
                if (!hasNeighborSignal) {
                    blockState = blockState.setValue(SIDE_CHAIN_PART, SideChainPart.UNCONNECTED);
                }

                level.setBlock(pos, blockState, Block.UPDATE_ALL);
                this.playSound(level, pos, hasNeighborSignal ? SoundEvents.SHELF_ACTIVATE : SoundEvents.SHELF_DEACTIVATE);
                level.gameEvent(hasNeighborSignal ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos, GameEvent.Context.of(blockState));
            }
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()))
            .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public int getRows() {
        return 1;
    }

    @Override
    public int getColumns() {
        return 3;
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (level.getBlockEntity(pos) instanceof ShelfBlockEntity shelfBlockEntity && !hand.equals(InteractionHand.OFF_HAND)) {
            OptionalInt hitSlot = this.getHitSlot(hitResult, state.getValue(FACING));
            if (hitSlot.isEmpty()) {
                return InteractionResult.PASS;
            } else {
                Inventory inventory = player.getInventory();
                if (level.isClientSide()) {
                    return (InteractionResult)(inventory.getSelectedItem().isEmpty() ? InteractionResult.PASS : InteractionResult.SUCCESS);
                } else if (!state.getValue(POWERED)) {
                    boolean flag = swapSingleItem(stack, player, shelfBlockEntity, hitSlot.getAsInt(), inventory);
                    if (flag) {
                        this.playSound(level, pos, stack.isEmpty() ? SoundEvents.SHELF_TAKE_ITEM : SoundEvents.SHELF_SINGLE_SWAP);
                    } else {
                        if (stack.isEmpty()) {
                            return InteractionResult.PASS;
                        }

                        this.playSound(level, pos, SoundEvents.SHELF_PLACE_ITEM);
                    }

                    return InteractionResult.SUCCESS.heldItemTransformedTo(stack);
                } else {
                    ItemStack selectedItem = inventory.getSelectedItem();
                    boolean flag1 = this.swapHotbar(level, pos, inventory);
                    if (!flag1) {
                        return InteractionResult.CONSUME;
                    } else {
                        this.playSound(level, pos, SoundEvents.SHELF_MULTI_SWAP);
                        return selectedItem == inventory.getSelectedItem()
                            ? InteractionResult.SUCCESS
                            : InteractionResult.SUCCESS.heldItemTransformedTo(inventory.getSelectedItem());
                    }
                }
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    private static boolean swapSingleItem(ItemStack stack, Player player, ShelfBlockEntity blockEntity, int slot, Inventory inventory) {
        ItemStack itemStack = blockEntity.swapItemNoUpdate(slot, stack);
        ItemStack itemStack1 = player.hasInfiniteMaterials() && itemStack.isEmpty() ? stack.copy() : itemStack;
        inventory.setItem(inventory.getSelectedSlot(), itemStack1);
        inventory.setChanged();
        blockEntity.setChanged(
            itemStack1.has(DataComponents.USE_EFFECTS) && !itemStack1.get(DataComponents.USE_EFFECTS).interactVibrations()
                ? null
                : GameEvent.ITEM_INTERACT_FINISH
        );
        return !itemStack.isEmpty();
    }

    private boolean swapHotbar(Level level, BlockPos pos, Inventory inventory) {
        List<BlockPos> allBlocksConnectedTo = this.getAllBlocksConnectedTo(level, pos);
        if (allBlocksConnectedTo.isEmpty()) {
            return false;
        } else {
            boolean flag = false;

            for (int i = 0; i < allBlocksConnectedTo.size(); i++) {
                ShelfBlockEntity shelfBlockEntity = (ShelfBlockEntity)level.getBlockEntity(allBlocksConnectedTo.get(i));
                if (shelfBlockEntity != null) {
                    for (int i1 = 0; i1 < shelfBlockEntity.getContainerSize(); i1++) {
                        int i2 = 9 - (allBlocksConnectedTo.size() - i) * shelfBlockEntity.getContainerSize() + i1;
                        if (i2 >= 0 && i2 <= inventory.getContainerSize()) {
                            ItemStack itemStack = inventory.removeItemNoUpdate(i2);
                            ItemStack itemStack1 = shelfBlockEntity.swapItemNoUpdate(i1, itemStack);
                            if (!itemStack.isEmpty() || !itemStack1.isEmpty()) {
                                inventory.setItem(i2, itemStack1);
                                flag = true;
                            }
                        }
                    }

                    inventory.setChanged();
                    shelfBlockEntity.setChanged(GameEvent.ENTITY_INTERACT);
                }
            }

            return flag;
        }
    }

    @Override
    public SideChainPart getSideChainPart(BlockState state) {
        return state.getValue(SIDE_CHAIN_PART);
    }

    @Override
    public BlockState setSideChainPart(BlockState state, SideChainPart chainPart) {
        return state.setValue(SIDE_CHAIN_PART, chainPart);
    }

    @Override
    public Direction getFacing(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    public boolean isConnectable(BlockState state) {
        return state.is(BlockTags.WOODEN_SHELVES) && state.hasProperty(POWERED) && state.getValue(POWERED);
    }

    @Override
    public int getMaxChainLength() {
        return 3;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (state.getValue(POWERED)) {
            this.updateSelfAndNeighborsOnPoweringUp(level, pos, state, oldState);
        } else {
            this.updateNeighborsAfterPoweringDown(level, pos, state);
        }
    }

    private void playSound(LevelAccessor level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
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

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level.isClientSide()) {
            return 0;
        } else if (direction != state.getValue(FACING).getOpposite()) {
            return 0;
        } else if (level.getBlockEntity(pos) instanceof ShelfBlockEntity shelfBlockEntity) {
            int i = shelfBlockEntity.getItem(0).isEmpty() ? 0 : 1;
            int i1 = shelfBlockEntity.getItem(1).isEmpty() ? 0 : 1;
            int i2 = shelfBlockEntity.getItem(2).isEmpty() ? 0 : 1;
            return i | i1 << 1 | i2 << 2;
        } else {
            return 0;
        }
    }
}
