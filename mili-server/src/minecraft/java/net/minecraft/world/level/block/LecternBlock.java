package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class LecternBlock extends BaseEntityBlock {
    public static final MapCodec<LecternBlock> CODEC = simpleCodec(LecternBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty HAS_BOOK = BlockStateProperties.HAS_BOOK;
    private static final VoxelShape SHAPE_COLLISION = Shapes.or(Block.column(16.0, 0.0, 2.0), Block.column(8.0, 2.0, 14.0));
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(
        Shapes.or(
            Block.boxZ(16.0, 10.0, 14.0, 1.0, 5.333333),
            Block.boxZ(16.0, 12.0, 16.0, 5.333333, 9.666667),
            Block.boxZ(16.0, 14.0, 18.0, 9.666667, 14.0),
            SHAPE_COLLISION
        )
    );
    private static final int PAGE_CHANGE_IMPULSE_TICKS = 2;

    @Override
    public MapCodec<LecternBlock> codec() {
        return CODEC;
    }

    protected LecternBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(HAS_BOOK, false));
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return SHAPE_COLLISION;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        ItemStack itemInHand = context.getItemInHand();
        Player player = context.getPlayer();
        boolean flag = false;
        if (!level.isClientSide() && player != null && player.canUseGameMasterBlocks()) {
            TypedEntityData<BlockEntityType<?>> typedEntityData = itemInHand.get(DataComponents.BLOCK_ENTITY_DATA);
            if (typedEntityData != null && typedEntityData.contains("Book")) {
                flag = true;
            }
        }

        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HAS_BOOK, flag);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_COLLISION;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
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
        builder.add(FACING, POWERED, HAS_BOOK);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LecternBlockEntity(pos, state);
    }

    public static boolean tryPlaceBook(@Nullable LivingEntity entity, Level level, BlockPos pos, BlockState state, ItemStack stack) {
        if (!state.getValue(HAS_BOOK)) {
            if (!level.isClientSide()) {
                placeBook(entity, level, pos, state, stack);
            }

            return true;
        } else {
            return false;
        }
    }

    private static void placeBook(@Nullable LivingEntity entity, Level level, BlockPos pos, BlockState state, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof LecternBlockEntity lecternBlockEntity) {
            // Paper start - Add PlayerInsertLecternBookEvent
            ItemStack eventSourcedBookStack = null;
            if (entity instanceof final net.minecraft.server.level.ServerPlayer serverPlayer) {
                final io.papermc.paper.event.player.PlayerInsertLecternBookEvent event = new io.papermc.paper.event.player.PlayerInsertLecternBookEvent(
                    serverPlayer.getBukkitEntity(),
                    org.bukkit.craftbukkit.block.CraftBlock.at(level, pos),
                    org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(stack.copyWithCount(1))
                );
                if (!event.callEvent()) return;
                eventSourcedBookStack = org.bukkit.craftbukkit.inventory.CraftItemStack.unwrap(event.getBook());
            }
            if (eventSourcedBookStack == null) {
                eventSourcedBookStack = stack.consumeAndReturn(1, entity);
            } else {
                stack.consume(1, entity);
            }
            lecternBlockEntity.setBook(eventSourcedBookStack);
            // Paper end - Add PlayerInsertLecternBookEvent
            resetBookState(entity, level, pos, state, true);
            level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    public static void resetBookState(@Nullable Entity entity, Level level, BlockPos pos, BlockState state, boolean hasBook) {
        BlockState blockState = state.setValue(POWERED, false).setValue(HAS_BOOK, hasBook);
        level.setBlock(pos, blockState, Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, blockState));
        updateBelow(level, pos, state);
    }

    public static void signalPageChange(Level level, BlockPos pos, BlockState state) {
        changePowered(level, pos, state, true);
        level.scheduleTick(pos, state.getBlock(), 2);
        level.levelEvent(LevelEvent.SOUND_PAGE_TURN, pos, 0);
    }

    private static void changePowered(Level level, BlockPos pos, BlockState state, boolean powered) {
        // Paper start - Call BlockRedstoneEvent properly
        final int currentRedstoneLevel = state.getValue(LecternBlock.POWERED) ? 15 : 0, targetRedstoneLevel = powered ? 15 : 0;
        if (currentRedstoneLevel != targetRedstoneLevel) {
            final org.bukkit.event.block.BlockRedstoneEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(level, pos, currentRedstoneLevel, targetRedstoneLevel);

            if (event.getNewCurrent() != targetRedstoneLevel) {
                return;
            }
        }
        // Paper end - Call BlockRedstoneEvent properly
        level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        updateBelow(level, pos, state);
    }

    private static void updateBelow(Level level, BlockPos pos, BlockState state) {
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, state.getValue(FACING).getOpposite(), Direction.UP);
        level.updateNeighborsAt(pos.below(), state.getBlock(), orientation);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        changePowered(level, pos, state, false);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (state.getValue(POWERED)) {
            updateBelow(level, pos, state);
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return side == Direction.UP && state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (state.getValue(HAS_BOOK)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof LecternBlockEntity) {
                return ((LecternBlockEntity)blockEntity).getRedstoneSignal();
            }
        }

        return 0;
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (state.getValue(HAS_BOOK)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        } else if (stack.is(ItemTags.LECTERN_BOOKS)) {
            return (InteractionResult)(tryPlaceBook(player, level, pos, state, stack) ? InteractionResult.SUCCESS : InteractionResult.PASS);
        } else {
            return (InteractionResult)(stack.isEmpty() && hand == InteractionHand.MAIN_HAND ? InteractionResult.PASS : InteractionResult.TRY_WITH_EMPTY_HAND);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(HAS_BOOK)) {
            if (!level.isClientSide()) {
                this.openScreen(level, pos, player);
            }

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return !state.getValue(HAS_BOOK) ? null : super.getMenuProvider(state, level, pos);
    }

    private void openScreen(Level level, BlockPos pos, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof LecternBlockEntity lecternBlockEntity && player.openMenu(lecternBlockEntity).isPresent()) { // Paper - Fix InventoryOpenEvent cancellation
            player.awardStat(Stats.INTERACT_WITH_LECTERN);
        }
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
