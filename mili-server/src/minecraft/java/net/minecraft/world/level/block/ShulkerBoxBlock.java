package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class ShulkerBoxBlock extends BaseEntityBlock {
    public static final MapCodec<ShulkerBoxBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                DyeColor.CODEC.optionalFieldOf("color").forGetter(shulkerBoxBlock -> Optional.ofNullable(shulkerBoxBlock.color)), propertiesCodec()
            )
            .apply(instance, (optional, properties) -> new ShulkerBoxBlock(optional.orElse(null), properties))
    );
    public static final Map<Direction, VoxelShape> SHAPES_OPEN_SUPPORT = Shapes.rotateAll(Block.boxZ(16.0, 0.0, 1.0));
    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
    public static final Identifier CONTENTS = Identifier.withDefaultNamespace("contents");
    public final @Nullable DyeColor color;

    @Override
    public MapCodec<ShulkerBoxBlock> codec() {
        return CODEC;
    }

    public ShulkerBoxBlock(@Nullable DyeColor color, BlockBehaviour.Properties properties) {
        super(properties);
        this.color = color;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShulkerBoxBlockEntity(this.color, pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, BlockEntityType.SHULKER_BOX, ShulkerBoxBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel
            && level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity shulkerBoxBlockEntity
            && canOpen(state, level, pos, shulkerBoxBlockEntity) // Paper - Fix InventoryOpenEvent cancellation - expand if for belows check
            && player.openMenu(shulkerBoxBlockEntity).isPresent()) { // Paper - Fix InventoryOpenEvent cancellation
            player.awardStat(Stats.OPEN_SHULKER_BOX);
            PiglinAi.angerNearbyPiglins(serverLevel, player, true);
        }

        return InteractionResult.SUCCESS;
    }

    private static boolean canOpen(BlockState state, Level level, BlockPos pos, ShulkerBoxBlockEntity blockEntity) {
        if (blockEntity.getAnimationStatus() != ShulkerBoxBlockEntity.AnimationStatus.CLOSED) {
            return true;
        } else {
            AABB aabb = Shulker.getProgressDeltaAabb(1.0F, state.getValue(FACING), 0.0F, 0.5F, pos.getBottomCenter()).deflate(1.0E-6);
            return level.noCollision(aabb);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ShulkerBoxBlockEntity shulkerBoxBlockEntity) {
            if (!level.isClientSide() && player.preventsBlockDrops() && !shulkerBoxBlockEntity.isEmpty()) {
                ItemStack coloredItemStack = getColoredItemStack(this.getColor());
                coloredItemStack.applyComponents(blockEntity.collectComponents());
                ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, coloredItemStack);
                itemEntity.setDefaultPickUpDelay();
                level.addFreshEntity(itemEntity);
            } else {
                shulkerBoxBlockEntity.unpackLootTable(player, true); // Paper - force clear loot table so replenish data isn't persisted in the stack
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        Runnable reAdd = null; // Paper
        if (blockEntity instanceof ShulkerBoxBlockEntity shulkerBoxBlockEntity) {
            // Paper start - clear loot table if it was already used
            if (shulkerBoxBlockEntity.lootableData().getLastFill() != -1 || !params.getLevel().paperConfig().lootables.retainUnlootedShulkerBoxLootTableOnNonPlayerBreak) {
                net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> lootTableResourceKey = shulkerBoxBlockEntity.getLootTable();
                reAdd = () -> shulkerBoxBlockEntity.setLootTable(lootTableResourceKey);
                shulkerBoxBlockEntity.setLootTable(null);
            }
            // Paper end - clear loot table if it was already used
            params = params.withDynamicDrop(CONTENTS, output -> {
                for (int i = 0; i < shulkerBoxBlockEntity.getContainerSize(); i++) {
                    output.accept(shulkerBoxBlockEntity.getItem(i));
                }
            });
        }

        // Paper start - re-set loot table if it was cleared
        try {
        return super.getDrops(state, params);
        } finally {
            if (reAdd != null) reAdd.run();
        }
        // Paper end - re-set loot table if it was cleared
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity shulkerBoxBlockEntity && !shulkerBoxBlockEntity.isClosed()
            ? SHAPES_OPEN_SUPPORT.get(state.getValue(FACING).getOpposite())
            : Shapes.block();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity shulkerBoxBlockEntity
            ? Shapes.create(shulkerBoxBlockEntity.getBoundingBox(state))
            : Shapes.block();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return false;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    public static Block getBlockByColor(@Nullable DyeColor color) {
        if (color == null) {
            return Blocks.SHULKER_BOX;
        } else {
            return switch (color) {
                case WHITE -> Blocks.WHITE_SHULKER_BOX;
                case ORANGE -> Blocks.ORANGE_SHULKER_BOX;
                case MAGENTA -> Blocks.MAGENTA_SHULKER_BOX;
                case LIGHT_BLUE -> Blocks.LIGHT_BLUE_SHULKER_BOX;
                case YELLOW -> Blocks.YELLOW_SHULKER_BOX;
                case LIME -> Blocks.LIME_SHULKER_BOX;
                case PINK -> Blocks.PINK_SHULKER_BOX;
                case GRAY -> Blocks.GRAY_SHULKER_BOX;
                case LIGHT_GRAY -> Blocks.LIGHT_GRAY_SHULKER_BOX;
                case CYAN -> Blocks.CYAN_SHULKER_BOX;
                case BLUE -> Blocks.BLUE_SHULKER_BOX;
                case BROWN -> Blocks.BROWN_SHULKER_BOX;
                case GREEN -> Blocks.GREEN_SHULKER_BOX;
                case RED -> Blocks.RED_SHULKER_BOX;
                case BLACK -> Blocks.BLACK_SHULKER_BOX;
                case PURPLE -> Blocks.PURPLE_SHULKER_BOX;
            };
        }
    }

    public @Nullable DyeColor getColor() {
        return this.color;
    }

    public static ItemStack getColoredItemStack(@Nullable DyeColor color) {
        return new ItemStack(getBlockByColor(color));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
