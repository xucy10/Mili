package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class ChiseledBookShelfBlock extends BaseEntityBlock implements SelectableSlotContainer {
    public static final MapCodec<ChiseledBookShelfBlock> CODEC = simpleCodec(ChiseledBookShelfBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty SLOT_0_OCCUPIED = BlockStateProperties.SLOT_0_OCCUPIED;
    public static final BooleanProperty SLOT_1_OCCUPIED = BlockStateProperties.SLOT_1_OCCUPIED;
    public static final BooleanProperty SLOT_2_OCCUPIED = BlockStateProperties.SLOT_2_OCCUPIED;
    public static final BooleanProperty SLOT_3_OCCUPIED = BlockStateProperties.SLOT_3_OCCUPIED;
    public static final BooleanProperty SLOT_4_OCCUPIED = BlockStateProperties.SLOT_4_OCCUPIED;
    public static final BooleanProperty SLOT_5_OCCUPIED = BlockStateProperties.SLOT_5_OCCUPIED;
    private static final int MAX_BOOKS_IN_STORAGE = 6;
    private static final int BOOKS_PER_ROW = 3;
    public static final List<BooleanProperty> SLOT_OCCUPIED_PROPERTIES = List.of(
        SLOT_0_OCCUPIED, SLOT_1_OCCUPIED, SLOT_2_OCCUPIED, SLOT_3_OCCUPIED, SLOT_4_OCCUPIED, SLOT_5_OCCUPIED
    );

    @Override
    public MapCodec<ChiseledBookShelfBlock> codec() {
        return CODEC;
    }

    @Override
    public int getRows() {
        return 2;
    }

    @Override
    public int getColumns() {
        return 3;
    }

    public ChiseledBookShelfBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState blockState = this.stateDefinition.any().setValue(FACING, Direction.NORTH);

        for (BooleanProperty booleanProperty : SLOT_OCCUPIED_PROPERTIES) {
            blockState = blockState.setValue(booleanProperty, false);
        }

        this.registerDefaultState(blockState);
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity chiseledBookShelfBlockEntity) {
            if (!stack.is(ItemTags.BOOKSHELF_BOOKS)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            } else {
                OptionalInt hitSlot = this.getHitSlot(hitResult, state.getValue(FACING));
                if (hitSlot.isEmpty()) {
                    return InteractionResult.PASS;
                } else if (state.getValue(SLOT_OCCUPIED_PROPERTIES.get(hitSlot.getAsInt()))) {
                    return InteractionResult.TRY_WITH_EMPTY_HAND;
                } else {
                    addBook(level, pos, player, chiseledBookShelfBlockEntity, stack, hitSlot.getAsInt());
                    return InteractionResult.SUCCESS;
                }
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity chiseledBookShelfBlockEntity) {
            OptionalInt hitSlot = this.getHitSlot(hitResult, state.getValue(FACING));
            if (hitSlot.isEmpty()) {
                return InteractionResult.PASS;
            } else if (!state.getValue(SLOT_OCCUPIED_PROPERTIES.get(hitSlot.getAsInt()))) {
                return InteractionResult.CONSUME;
            } else {
                removeBook(level, pos, player, chiseledBookShelfBlockEntity, hitSlot.getAsInt());
                return InteractionResult.SUCCESS;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    private static void addBook(Level level, BlockPos pos, Player player, ChiseledBookShelfBlockEntity blockEntity, ItemStack bookStack, int slot) {
        if (!level.isClientSide()) {
            player.awardStat(Stats.ITEM_USED.get(bookStack.getItem()));
            SoundEvent soundEvent = bookStack.is(Items.ENCHANTED_BOOK)
                ? SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED
                : SoundEvents.CHISELED_BOOKSHELF_INSERT;
            blockEntity.setItem(slot, bookStack.consumeAndReturn(1, player));
            level.playSound(null, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private static void removeBook(Level level, BlockPos pos, Player player, ChiseledBookShelfBlockEntity blockEntity, int slot) {
        if (!level.isClientSide()) {
            ItemStack itemStack = blockEntity.removeItem(slot, 1);
            SoundEvent soundEvent = itemStack.is(Items.ENCHANTED_BOOK)
                ? SoundEvents.CHISELED_BOOKSHELF_PICKUP_ENCHANTED
                : SoundEvents.CHISELED_BOOKSHELF_PICKUP;
            level.playSound(null, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.getInventory().add(itemStack)) {
                player.drop(itemStack, false);
            }

            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChiseledBookShelfBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        SLOT_OCCUPIED_PROPERTIES.forEach(property -> builder.add(property));
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level.isClientSide()) {
            return 0;
        } else {
            return level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity chiseledBookShelfBlockEntity
                ? chiseledBookShelfBlockEntity.getLastInteractedSlot() + 1
                : 0;
        }
    }
}
