package net.minecraft.world.item;

import java.util.Map;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

public class BlockItem extends Item {
    @Deprecated
    private final Block block;

    public BlockItem(Block block, Item.Properties properties) {
        super(properties);
        this.block = block;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult interactionResult = this.place(new BlockPlaceContext(context));
        return !interactionResult.consumesAction() && context.getItemInHand().has(DataComponents.CONSUMABLE)
            ? super.use(context.getLevel(), context.getPlayer(), context.getHand())
            : interactionResult;
    }

    public InteractionResult place(BlockPlaceContext context) {
        if (!this.getBlock().isEnabled(context.getLevel().enabledFeatures())) {
            return InteractionResult.FAIL;
        } else if (!context.canPlace()) {
            return InteractionResult.FAIL;
        } else {
            BlockPlaceContext blockPlaceContext = this.updatePlacementContext(context);
            if (blockPlaceContext == null) {
                return InteractionResult.FAIL;
            } else {
                BlockState placementState = this.getPlacementState(blockPlaceContext);
                // CraftBukkit start - special case for handling block placement with water lilies and snow buckets
                org.bukkit.block.BlockState bukkitState = null;
                if (this instanceof PlaceOnWaterBlockItem || this instanceof SolidBucketItem) {
                    bukkitState = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(blockPlaceContext.getLevel(), blockPlaceContext.getClickedPos());
                }
                final org.bukkit.block.BlockState oldBukkitState = bukkitState != null ? bukkitState : org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(blockPlaceContext.getLevel(), blockPlaceContext.getClickedPos()); // Paper - Reset placed block on exception
                // CraftBukkit end

                if (placementState == null) {
                    return InteractionResult.FAIL;
                } else if (!this.placeBlock(blockPlaceContext, placementState)) {
                    return InteractionResult.FAIL;
                } else {
                    BlockPos clickedPos = blockPlaceContext.getClickedPos();
                    Level level = blockPlaceContext.getLevel();
                    Player player = blockPlaceContext.getPlayer();
                    ItemStack itemInHand = blockPlaceContext.getItemInHand();
                    BlockState blockState = level.getBlockState(clickedPos);
                    if (blockState.is(placementState.getBlock())) {
                        blockState = this.updateBlockStateFromTag(clickedPos, level, itemInHand, blockState);
                        // Paper start - Reset placed block on exception
                        try {
                        this.updateCustomBlockEntityTag(clickedPos, level, player, itemInHand, blockState);
                        updateBlockEntityComponents(level, clickedPos, itemInHand);
                        } catch (Exception ex) {
                            ((org.bukkit.craftbukkit.block.CraftBlockState) oldBukkitState).revertPlace();
                            if (player instanceof ServerPlayer serverPlayer) {
                                org.apache.logging.log4j.LogManager.getLogger().error("Player {} tried placing invalid block", player.getScoreboardName(), ex);
                                serverPlayer.getBukkitEntity().kickPlayer("Packet processing error");
                                return InteractionResult.FAIL;
                            }
                            throw ex; // Rethrow exception if not placed by a player
                        }
                        // Paper end - Reset placed block on exception
                        blockState.getBlock().setPlacedBy(level, clickedPos, blockState, player, itemInHand);
                        // CraftBukkit start
                        if (bukkitState != null) {
                            org.bukkit.event.block.BlockPlaceEvent placeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPlaceEvent((net.minecraft.server.level.ServerLevel) level, player, blockPlaceContext.getHand(), bukkitState, clickedPos);
                            if (placeEvent != null && (placeEvent.isCancelled() || !placeEvent.canBuild())) {
                                ((org.bukkit.craftbukkit.block.CraftBlockState) bukkitState).revertPlace();

                                player.containerMenu.forceHeldSlot(blockPlaceContext.getHand());
                                return InteractionResult.FAIL;
                            }
                        }
                        // CraftBukkit end
                        if (player instanceof ServerPlayer) {
                            CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer)player, clickedPos, itemInHand);
                        }
                    }

                    SoundType soundType = blockState.getSoundType();
                    if (player == null) // Paper - Fix block place logic; reintroduce this for the dispenser (i.e the shulker)
                    level.playSound(
                        player,
                        clickedPos,
                        this.getPlaceSound(blockState),
                        SoundSource.BLOCKS,
                        (soundType.getVolume() + 1.0F) / 2.0F,
                        soundType.getPitch() * 0.8F
                    );
                    level.gameEvent(GameEvent.BLOCK_PLACE, clickedPos, GameEvent.Context.of(player, blockState));
                    itemInHand.consume(1, player);
                    return InteractionResult.SUCCESS.configurePaper(e -> e.placedBlockAt(clickedPos.immutable())); // Paper - track placed block position from block item
                }
            }
        }
    }

    protected SoundEvent getPlaceSound(BlockState state) {
        return state.getSoundType().getPlaceSound();
    }

    public @Nullable BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        return context;
    }

    private static void updateBlockEntityComponents(Level level, BlockPos pos, ItemStack stack) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntity.applyComponentsFromItemStack(stack);
            blockEntity.setChanged();
        }
    }

    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        return updateCustomBlockEntityTag(level, player, pos, stack);
    }

    protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
        BlockState stateForPlacement = this.getBlock().getStateForPlacement(context);
        return stateForPlacement != null && this.canPlace(context, stateForPlacement) ? stateForPlacement : null;
    }

    private BlockState updateBlockStateFromTag(BlockPos pos, Level level, ItemStack stack, BlockState state) {
        BlockItemStateProperties blockItemStateProperties = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        if (blockItemStateProperties.isEmpty()) {
            return state;
        } else {
            BlockState blockState = blockItemStateProperties.apply(state);
            if (blockState != state) {
                level.setBlock(pos, blockState, Block.UPDATE_CLIENTS);
            }

            return blockState;
        }
    }

    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        Player player = context.getPlayer();
        // CraftBukkit start
        Level world = context.getLevel(); // Paper - Cancel hit for vanished players
        boolean canBuild = (!this.mustSurvive() || state.canSurvive(world, context.getClickedPos())) && world.checkEntityCollision(state, player, CollisionContext.placementContext(player), context.getClickedPos(), true); // Paper - Cancel hit for vanished players
        org.bukkit.entity.Player bukkitPlayer = (context.getPlayer() instanceof ServerPlayer) ? (org.bukkit.entity.Player) context.getPlayer().getBukkitEntity() : null;

        org.bukkit.event.block.BlockCanBuildEvent event = new org.bukkit.event.block.BlockCanBuildEvent(
            org.bukkit.craftbukkit.block.CraftBlock.at(world, context.getClickedPos()), bukkitPlayer,
            org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(state), canBuild, org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(context.getHand())
        );
        world.getCraftServer().getPluginManager().callEvent(event);

        return event.isBuildable();
        // CraftBukkit end
    }

    protected boolean mustSurvive() {
        return true;
    }

    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return context.getLevel().setBlock(context.getClickedPos(), state, Block.UPDATE_ALL_IMMEDIATE);
    }

    public static boolean updateCustomBlockEntityTag(Level level, @Nullable Player player, BlockPos pos, ItemStack stack) {
        if (level.isClientSide()) {
            return false;
        } else {
            TypedEntityData<BlockEntityType<?>> typedEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (typedEntityData != null) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    BlockEntityType<?> type = blockEntity.getType();
                    if (type != typedEntityData.type()) {
                        return false;
                    }

                    if (!type.onlyOpCanSetNbt() || player != null && (player.canUseGameMasterBlocks() || (player.getAbilities().instabuild && player.getBukkitEntity().hasPermission("minecraft.nbt.place")))) { // Spigot - add permission
                        return typedEntityData.loadInto(blockEntity, level.registryAccess());
                    }

                    return false;
                }
            }

            return false;
        }
    }

    @Override
    public boolean shouldPrintOpWarning(ItemStack stack, @Nullable Player player) {
        if (player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            TypedEntityData<BlockEntityType<?>> typedEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (typedEntityData != null) {
                return typedEntityData.type().onlyOpCanSetNbt();
            }
        }

        return false;
    }

    public Block getBlock() {
        return this.block;
    }

    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
        blockToItemMap.put(this.getBlock(), item);
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return !(this.getBlock() instanceof ShulkerBoxBlock);
    }

    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        ItemContainerContents itemContainerContents = itemEntity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (itemContainerContents != null) {
            ItemUtils.onContainerDestroyed(itemEntity, itemContainerContents.nonEmptyItemsCopy());
        }
    }

    public static void setBlockEntityData(ItemStack stack, BlockEntityType<?> blockEntityType, TagValueOutput output) {
        output.discard("id");
        if (output.isEmpty()) {
            stack.remove(DataComponents.BLOCK_ENTITY_DATA);
        } else {
            BlockEntity.addEntityType(output, blockEntityType);
            stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(blockEntityType, output.buildResult()));
        }
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.getBlock().requiredFeatures();
    }
}
