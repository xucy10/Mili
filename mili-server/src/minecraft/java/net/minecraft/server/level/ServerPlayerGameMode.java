package net.minecraft.server.level;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Objects;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerPlayerGameMode {
    private static final double FLIGHT_DISABLE_RANGE = 1.0;
    private static final Logger LOGGER = LogUtils.getLogger();
    public ServerLevel level;
    protected final ServerPlayer player;
    private GameType gameModeForPlayer = GameType.DEFAULT_MODE;
    private @Nullable GameType previousGameModeForPlayer;
    private boolean isDestroyingBlock;
    private int destroyProgressStart;
    private BlockPos destroyPos = BlockPos.ZERO;
    private int gameTicks;
    private boolean hasDelayedDestroy;
    private BlockPos delayedDestroyPos = BlockPos.ZERO;
    private int delayedTickStart;
    private int lastSentState = -1;
    public boolean captureSentBlockEntities = false; // Paper - Send block entities after destroy prediction
    public boolean capturedBlockEntity = false; // Paper - Send block entities after destroy prediction

    public ServerPlayerGameMode(ServerPlayer player) {
        this.player = player;
        this.level = player.level();
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper
    public boolean changeGameModeForPlayer(GameType gameModeForPlayer) {
        // Paper start - Expand PlayerGameModeChangeEvent
        org.bukkit.event.player.PlayerGameModeChangeEvent event = this.changeGameModeForPlayer(gameModeForPlayer, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.UNKNOWN, null);
        return event != null && !event.isCancelled();
    }

    public org.bukkit.event.player.@Nullable PlayerGameModeChangeEvent changeGameModeForPlayer(GameType gameModeForPlayer, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause playerGameModeChangeCause, net.kyori.adventure.text.@Nullable Component cancelMessage) {
        // Paper end - Expand PlayerGameModeChangeEvent
        if (gameModeForPlayer == this.gameModeForPlayer) {
            return null; // Paper - Expand PlayerGameModeChangeEvent
        } else {
            // CraftBukkit start
            org.bukkit.event.player.PlayerGameModeChangeEvent event = new org.bukkit.event.player.PlayerGameModeChangeEvent(
                this.player.getBukkitEntity(),
                org.bukkit.GameMode.getByValue(gameModeForPlayer.getId()),
                playerGameModeChangeCause, // Paper
                cancelMessage
            );
            if (!event.callEvent()) {
                return event; // Paper - Expand PlayerGameModeChangeEvent
            }
            // CraftBukkit end
            Abilities abilities = this.player.getAbilities();
            this.setGameModeForPlayer(gameModeForPlayer, this.gameModeForPlayer);
            if (abilities.flying && gameModeForPlayer != GameType.SPECTATOR && this.isInRangeOfGround()) {
                abilities.flying = false;
            }

            this.player.onUpdateAbilities();
            this.level
                .getServer()
                .getPlayerList()
                .broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, this.player), this.player); // CraftBukkit
            this.level.updateSleepingPlayerList();
            if (gameModeForPlayer == GameType.CREATIVE) {
                this.player.resetCurrentImpulseContext();
            }

            return event; // Paper - Expand PlayerGameModeChangeEvent
        }
    }

    protected void setGameModeForPlayer(GameType gameModeForPlayer, @Nullable GameType previousGameModeForPlayer) {
        this.previousGameModeForPlayer = previousGameModeForPlayer;
        this.gameModeForPlayer = gameModeForPlayer;
        Abilities abilities = this.player.getAbilities();
        gameModeForPlayer.updatePlayerAbilities(abilities);
    }

    private boolean isInRangeOfGround() {
        List<VoxelShape> list = Entity.collectAllColliders(this.player, this.level, this.player.getBoundingBox());
        return list.isEmpty() && this.player.getAvailableSpaceBelow(1.0) < 1.0;
    }

    public GameType getGameModeForPlayer() {
        return this.gameModeForPlayer;
    }

    public @Nullable GameType getPreviousGameModeForPlayer() {
        return this.previousGameModeForPlayer;
    }

    public boolean isSurvival() {
        return this.gameModeForPlayer.isSurvival();
    }

    public boolean isCreative() {
        return this.gameModeForPlayer.isCreative();
    }

    public void tick() {
        // this.gameTicks = net.minecraft.server.MinecraftServer.currentTick; // CraftBukkit
        this.gameTicks = (int) this.level.getLagCompensationTick(); // Paper - lag compensate eating
        if (this.hasDelayedDestroy) {
            BlockState blockState = !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.level, this.delayedDestroyPos) ? null : this.level.getBlockStateIfLoaded(this.delayedDestroyPos); // Paper - Don't allow digging into unloaded chunks // Folia - region threading - don't destroy blocks not owned
            if (blockState == null || blockState.isAir()) { // Paper - Don't allow digging into unloaded chunks
                this.hasDelayedDestroy = false;
            } else {
                float f = this.incrementDestroyProgress(blockState, this.delayedDestroyPos, this.delayedTickStart);
                if (f >= 1.0F) {
                    this.hasDelayedDestroy = false;
                    this.destroyBlock(this.delayedDestroyPos);
                }
            }
        } else if (this.isDestroyingBlock) {
            // Paper start - Don't allow digging into unloaded chunks; don't want to do same logic as above, return instead
            BlockState blockState = !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.level, this.destroyPos) ? null : this.level.getBlockStateIfLoaded(this.destroyPos); // Folia - region threading - don't destroy blocks not owned
            if (blockState == null) {
                this.isDestroyingBlock = false;
                return;
            }
            // Paper end - Don't allow digging into unloaded chunks
            if (blockState.isAir()) {
                this.level.destroyBlockProgress(this.player.getId(), this.destroyPos, -1);
                this.lastSentState = -1;
                this.isDestroyingBlock = false;
            } else {
                this.incrementDestroyProgress(blockState, this.destroyPos, this.destroyProgressStart);
            }
        }
    }

    private float incrementDestroyProgress(BlockState state, BlockPos pos, int startTick) {
        int i = this.gameTicks - startTick;
        float f = state.getDestroyProgress(this.player, this.player.level(), pos) * (i + 1);
        int i1 = (int)(f * 10.0F);
        if (i1 != this.lastSentState) {
            this.level.destroyBlockProgress(this.player.getId(), pos, i1);
            this.lastSentState = i1;
        }

        return f;
    }

    private void debugLogging(BlockPos pos, boolean terminate, int sequence, String message) {
        if (SharedConstants.DEBUG_BLOCK_BREAK) {
            LOGGER.debug("Server ACK {} {} {} {}", sequence, pos, terminate, message);
        }
    }

    public void handleBlockBreakAction(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction face, int maxBuildHeight, int sequence) {
        if (!this.player.isWithinBlockInteractionRange(pos, 1.0)) {
            if (true) return; // Paper - Don't allow digging into unloaded chunks; Don't notify if unreasonably far away
            this.debugLogging(pos, false, sequence, "too far");
        } else if (pos.getY() > maxBuildHeight) {
            this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
            this.debugLogging(pos, false, sequence, "too high");
        } else {
            if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                if (!this.level.mayInteract(this.player, pos)) {
                    // CraftBukkit start - fire PlayerInteractEvent
                    org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(this.player, org.bukkit.event.block.Action.LEFT_CLICK_BLOCK, pos, face, this.player.getInventory().getSelectedItem(), InteractionHand.MAIN_HAND);
                    this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
                    this.debugLogging(pos, false, sequence, "may not interact");
                    this.capturedBlockEntity = true; // Paper - Send block entities after destroy prediction
                    // CraftBukkit end
                    return;
                }

                // CraftBukkit start
                org.bukkit.event.player.PlayerInteractEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(this.player, org.bukkit.event.block.Action.LEFT_CLICK_BLOCK, pos, face, this.player.getInventory().getSelectedItem(), InteractionHand.MAIN_HAND);
                if (event.isCancelled()) {
                    this.capturedBlockEntity = true; // Paper - Send block entities after destroy prediction
                    return;
                }
                // CraftBukkit end

                if (this.player.getAbilities().instabuild) {
                    this.destroyAndAck(pos, sequence, "creative destroy");
                    return;
                }

                // Spigot start - handle debug stick left click for non-creative
                if (this.player.getMainHandItem().is(net.minecraft.world.item.Items.DEBUG_STICK)
                    && ((net.minecraft.world.item.DebugStickItem) net.minecraft.world.item.Items.DEBUG_STICK).handleInteraction(this.player, this.level.getBlockState(pos), this.level, pos, false, this.player.getMainHandItem())) {
                    return;
                }
                // Spigot end

                if (this.player.blockActionRestricted(this.level, pos, this.gameModeForPlayer)) {
                    this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
                    this.debugLogging(pos, false, sequence, "block action restricted");
                    return;
                }

                this.destroyProgressStart = this.gameTicks;
                float f = 1.0F;
                BlockState blockState = this.level.getBlockState(pos);
                if (event.useInteractedBlock() != org.bukkit.event.Event.Result.DENY && !blockState.isAir()) { // Paper
                    EnchantmentHelper.onHitBlock(
                        this.level,
                        this.player.getMainHandItem(),
                        this.player,
                        this.player,
                        EquipmentSlot.MAINHAND,
                        Vec3.atCenterOf(pos),
                        blockState,
                        item -> this.player.onEquippedItemBroken(item, EquipmentSlot.MAINHAND)
                    );
                    blockState.attack(this.level, pos, this.player);
                    f = blockState.getDestroyProgress(this.player, this.player.level(), pos);
                }

                // CraftBukkit start
                // Note that we don't need to resync blocks, block acks will handle it properly for everything but block entities already
                if (event.useItemInHand() == org.bukkit.event.Event.Result.DENY) {
                    return;
                }

                org.bukkit.event.block.BlockDamageEvent blockEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockDamageEvent(this.player, pos, face, this.player.getInventory().getSelectedItem(), f >= 1.0f); // Paper - Add BlockFace to BlockDamageEvent

                if (blockEvent.isCancelled()) {
                    return;
                }

                if (blockEvent.getInstaBreak()) {
                    f = 2.0f;
                }
                // CraftBukkit end

                if (!blockState.isAir() && f >= 1.0F) {
                    this.destroyAndAck(pos, sequence, "insta mine");
                } else {
                    if (this.isDestroyingBlock) {
                        this.player.connection.send(new ClientboundBlockUpdatePacket(this.destroyPos, this.level.getBlockState(this.destroyPos)));
                        this.debugLogging(pos, false, sequence, "abort destroying since another started (client insta mine, server disagreed)");
                    }

                    this.isDestroyingBlock = true;
                    this.destroyPos = pos.immutable();
                    int i = (int)(f * 10.0F);
                    this.level.destroyBlockProgress(this.player.getId(), pos, i);
                    this.debugLogging(pos, true, sequence, "actual start of destroying");
                    this.lastSentState = i;
                }
            } else if (action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
                if (pos.equals(this.destroyPos)) {
                    int i1 = this.gameTicks - this.destroyProgressStart;
                    BlockState blockStatex = this.level.getBlockState(pos);
                    if (!blockStatex.isAir()) {
                        float f1 = blockStatex.getDestroyProgress(this.player, this.player.level(), pos) * (i1 + 1);
                        if (f1 >= 0.7F) {
                            this.isDestroyingBlock = false;
                            this.level.destroyBlockProgress(this.player.getId(), pos, -1);
                            this.destroyAndAck(pos, sequence, "destroyed");
                            return;
                        }

                        if (!this.hasDelayedDestroy) {
                            this.isDestroyingBlock = false;
                            this.hasDelayedDestroy = true;
                            this.delayedDestroyPos = pos;
                            this.delayedTickStart = this.destroyProgressStart;
                        }
                    }
                }

                this.debugLogging(pos, true, sequence, "stopped destroying");
            } else if (action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
                this.isDestroyingBlock = false;
                // Paper start - Don't allow digging into unloaded chunks
                if (!Objects.equals(this.destroyPos, pos) && !BlockPos.ZERO.equals(this.destroyPos)) { // Paper
                    ServerPlayerGameMode.LOGGER.debug("Mismatch in destroy block pos: {} {}", this.destroyPos, pos); // CraftBukkit - SPIGOT-5457 sent by client when interact event cancelled
                    BlockState type = this.level.getBlockStateIfLoaded(this.destroyPos); // Don't load unloaded chunks for stale records here
                    if (type != null) {
                    this.level.destroyBlockProgress(this.player.getId(), this.destroyPos, -1);
                    this.debugLogging(pos, true, sequence, "aborted mismatched destroying");
                    }
                    this.destroyPos = BlockPos.ZERO;
                    // Paper end - Don't allow digging into unloaded chunks
                }

                this.level.destroyBlockProgress(this.player.getId(), pos, -1);
                this.debugLogging(pos, true, sequence, "aborted destroying");

                org.bukkit.craftbukkit.event.CraftEventFactory.callBlockDamageAbortEvent(this.player, pos, this.player.getInventory().getSelectedItem()); // CraftBukkit
            }
        }
        this.level.chunkPacketBlockController.onPlayerLeftClickBlock(this, pos, action, face, maxBuildHeight, sequence); // Paper - Anti-Xray
    }

    public void destroyAndAck(BlockPos pos, int sequence, String message) {
        if (this.destroyBlock(pos)) {
            this.debugLogging(pos, true, sequence, message);
        } else {
            this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
            this.debugLogging(pos, false, sequence, message);
        }
    }

    public boolean destroyBlock(BlockPos pos) {
        BlockState blockState = this.level.getBlockState(pos);
        // CraftBukkit start - fire BlockBreakEvent
        org.bukkit.block.Block bblock = org.bukkit.craftbukkit.block.CraftBlock.at(this.level, pos);
        org.bukkit.event.block.BlockBreakEvent event = null;
        if (this.player instanceof ServerPlayer) {
            // Sword + Creative mode pre-cancel
            boolean canAttackBlock = !this.player.getMainHandItem().canDestroyBlock(blockState, this.level, pos, this.player);
            event = new org.bukkit.event.block.BlockBreakEvent(bblock, this.player.getBukkitEntity());

            // Sword + Creative mode pre-cancel
            event.setCancelled(canAttackBlock);

            // Calculate default block experience
            BlockState updatedBlockState = this.level.getBlockState(pos);
            Block block = updatedBlockState.getBlock();

            if (!event.isCancelled() && !this.isCreative() && this.player.hasCorrectToolForDrops(block.defaultBlockState())) {
                ItemStack itemInHand = this.player.getItemBySlot(EquipmentSlot.MAINHAND);
                event.setExpToDrop(block.getExpDrop(updatedBlockState, this.level, pos, itemInHand, true));
            }

            this.level.getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                if (canAttackBlock) {
                    return false;
                }

                // Block entity data is not reset by the block acks, send after destroy prediction
                if (!this.captureSentBlockEntities) {
                    BlockEntity blockEntity = this.level.getBlockEntity(pos);
                    if (blockEntity != null) {
                        this.player.connection.send(blockEntity.getUpdatePacket());
                    }
                } else {
                    this.capturedBlockEntity = true;
                }
                return false;
            }
        }
        // CraftBukkit end

        if (false && !this.player.getMainHandItem().canDestroyBlock(blockState, this.level, pos, this.player)) { // CraftBukkit - false
            return false;
        } else {
            blockState = this.level.getBlockState(pos); // CraftBukkit - update state from plugins
            if (blockState.isAir()) return false; // CraftBukkit - A plugin set block to air without cancelling
            BlockEntity blockEntity = this.level.getBlockEntity(pos);
            Block block = blockState.getBlock();
            if (block instanceof GameMasterBlock && !this.player.canUseGameMasterBlocks() && !(block instanceof net.minecraft.world.level.block.CommandBlock && (this.player.isCreative() && this.player.getBukkitEntity().hasPermission("minecraft.commandblock")))) { // Paper - command block permission
                this.level.sendBlockUpdated(pos, blockState, blockState, Block.UPDATE_ALL);
                return false;
            } else if (this.player.blockActionRestricted(this.level, pos, this.gameModeForPlayer)) {
                return false;
            } else {
                // CraftBukkit start
                org.bukkit.block.BlockState state = bblock.getState();
                this.level.getCurrentWorldData().captureDrops = new java.util.ArrayList<>(); // Folia - region threading
                // CraftBukkit end
                BlockState blockState1 = block.playerWillDestroy(this.level, pos, blockState, this.player);
                // Leaves start - Prevent loss of item drops due to update suppression when breaking blocks
                boolean flag;
                org.leavesmc.leaves.util.UpdateSuppressionException ex = null;
                try {
                    flag = this.level.removeBlock(pos, false);
                } catch (org.leavesmc.leaves.util.UpdateSuppressionException e) {
                    ex = e;
                    ex.provideBlock(level, pos, block);
                    ex.providePlayer(this.player);
                    flag = false;
                }
                // Leaves end - Prevent loss of item drops due to update suppression when breaking blocks
                if (SharedConstants.DEBUG_BLOCK_BREAK) {
                    LOGGER.info("server broke {} {} -> {}", pos, blockState1, this.level.getBlockState(pos));
                }

                if (flag) {
                    block.destroy(this.level, pos, blockState1);
                }

                ItemStack mainHandStack = null; // Paper - Trigger bee_nest_destroyed trigger in the correct place
                boolean isCorrectTool = false; // Paper - Trigger bee_nest_destroyed trigger in the correct place
                if (this.player.preventsBlockDrops()) {
                    // return true; // CraftBukkit
                } else {
                    ItemStack mainHandItem = this.player.getMainHandItem();
                    ItemStack itemStack = mainHandItem.copy();
                    boolean hasCorrectToolForDrops = this.player.hasCorrectToolForDrops(blockState1);
                    mainHandStack = itemStack; // Paper - Trigger bee_nest_destroyed trigger in the correct place
                    isCorrectTool = hasCorrectToolForDrops; // Paper - Trigger bee_nest_destroyed trigger in the correct place
                    mainHandItem.mineBlock(this.level, blockState1, pos, this.player);
                    if (flag && hasCorrectToolForDrops) { // CraftBukkit - Check if block should drop items // Paper - fix drops not preventing stats/food exhaustion
                        block.playerDestroy(this.level, this.player, pos, blockState1, blockEntity, itemStack, event.isDropItems(), false); // Paper - fix drops not preventing stats/food exhaustion
                    }

                    // return true; // CraftBukkit
                }
                // CraftBukkit start
                java.util.List<net.minecraft.world.entity.item.ItemEntity> itemsToDrop = this.level.getCurrentWorldData().captureDrops; // Paper - capture all item additions to the world  // Folia - region threading
                this.level.getCurrentWorldData().captureDrops = null; // Paper - capture all item additions to the world; Remove this earlier so that we can actually drop stuff  // Folia - region threading
                if (event.isDropItems()) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockDropItemEvent(bblock, state, this.player, itemsToDrop); // Paper - capture all item additions to the world
                }
                if (ex != null) throw ex; // Leaves - Prevent loss of item drops due to update suppression when breaking blocks

                // Drop event experience
                if (flag) {
                    blockState.getBlock().popExperience(this.level, pos, event.getExpToDrop(), this.player); // Paper
                }
                // Paper start - Trigger bee_nest_destroyed trigger in the correct place (check impls of block#playerDestroy)
                if (mainHandStack != null) {
                    if (flag && isCorrectTool && event.isDropItems() && block instanceof net.minecraft.world.level.block.BeehiveBlock && blockEntity instanceof net.minecraft.world.level.block.entity.BeehiveBlockEntity beehiveBlockEntity) { // simulates the guard on block#playerDestroy above
                        CriteriaTriggers.BEE_NEST_DESTROYED.trigger(player, blockState, mainHandStack, beehiveBlockEntity.getOccupantCount());
                    }
                }
                // Paper end - Trigger bee_nest_destroyed trigger in the correct place

                return true;
                // CraftBukkit end
            }
        }
    }

    public InteractionResult useItem(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand) {
        if (this.gameModeForPlayer == GameType.SPECTATOR) {
            return InteractionResult.PASS;
        } else if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.PASS;
        } else {
            int count = stack.getCount();
            int damageValue = stack.getDamageValue();
            final ItemStack stackBeforeUse = stack.copy(); // Paper - Store stack before use for interact prediction check
            InteractionResult interactionResult = stack.use(level, player, hand);
            ItemStack itemStack;
            if (interactionResult instanceof InteractionResult.Success success) {
                itemStack = Objects.requireNonNullElse(success.heldItemTransformedTo(), player.getItemInHand(hand));
            } else {
                itemStack = player.getItemInHand(hand);
            }

            if (itemStack == stack && itemStack.getCount() == count && itemStack.getUseDuration(player) <= 0 && itemStack.getDamageValue() == damageValue) {
                return interactionResult;
            } else if (interactionResult instanceof InteractionResult.Fail && itemStack.getUseDuration(player) > 0 && !player.isUsingItem()) {
                return interactionResult;
            } else {
                if (stack != itemStack) {
                    player.setItemInHand(hand, itemStack);
                }

                if (itemStack.isEmpty()) {
                    player.setItemInHand(hand, ItemStack.EMPTY);
                }

                if (!player.isUsingItem()) {
                    // Paper start - Optimize sendAllDataToRemote calls
                    // This is a weird one where the Vanilla behavior is from an ancient version, but also isn't calling startUsingItem on certain instant-use items
                    // TODO Check up on this proper, possibly remove all and move into more specific places
                    if (io.papermc.paper.util.MCUtil.clientPredictsInteraction(player, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), stackBeforeUse)) {
                        player.inventoryMenu.forceHeldSlot(hand);
                    }
                    player.inventoryMenu.broadcastChanges();
                    // Paper end - Optimize sendAllDataToRemote calls
                }

                return interactionResult;
            }
        }
    }

    // CraftBukkit start - whole method
    public boolean interactResult = false;
    public boolean firedInteract = false;
    public BlockPos interactPosition;
    public InteractionHand interactHand;
    public ItemStack interactItemStack;
    public InteractionResult useItemOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult) {
        BlockPos blockPos = hitResult.getBlockPos();
        BlockState blockState = level.getBlockState(blockPos);
        boolean cancelledBlock = false;
        boolean cancelledItem = false; // Paper - correctly handle items on cooldown
        if (!blockState.getBlock().isEnabled(level.enabledFeatures())) {
            return InteractionResult.FAIL;
        } else if (this.gameModeForPlayer == GameType.SPECTATOR) {
            MenuProvider menuProvider = blockState.getMenuProvider(level, blockPos);
            cancelledBlock = !(menuProvider instanceof MenuProvider);
        }

        if (player.getCooldowns().isOnCooldown(stack)) {
            cancelledItem = true; // Paper - correctly handle items on cooldown
        }
        org.bukkit.event.player.PlayerInteractEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(player, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, blockPos, hitResult.getDirection(), stack, cancelledBlock, cancelledItem, hand, hitResult.getLocation()); // Paper - correctly handle items on cooldown
        this.firedInteract = true;
        this.interactResult = event.useItemInHand() == org.bukkit.event.Event.Result.DENY;
        this.interactPosition = blockPos.immutable();
        this.interactHand = hand;
        this.interactItemStack = stack.copy();

        if (event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) {
            // Block acks will take care of most of it, just handle some special cases here
            if (blockState.getBlock() instanceof net.minecraft.world.level.block.CakeBlock) {
                player.getBukkitEntity().sendHealthUpdate(); // SPIGOT-1341 - reset health for cake
            } else if (blockState.is(net.minecraft.world.level.block.Blocks.JIGSAW) || blockState.is(net.minecraft.world.level.block.Blocks.STRUCTURE_BLOCK) || blockState.getBlock() instanceof net.minecraft.world.level.block.CommandBlock) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerClosePacket(this.player.containerMenu.containerId));
            }
            // Paper start - Fix inventory desync; SPIGOT-2867
            if (io.papermc.paper.util.MCUtil.clientPredictsInteraction(this.player, blockState, stack)) {
                this.player.containerMenu.sendAllDataToRemote();
            } else {
                this.player.containerMenu.forceHeldSlot(hand);
            }
            // Paper end - Fix inventory desync
            this.player.resyncUsingItem(this.player); // Paper - Properly cancel usable items
            return (event.useItemInHand() != org.bukkit.event.Event.Result.ALLOW) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        } else if (this.gameModeForPlayer == GameType.SPECTATOR) {
            MenuProvider menuProvider = blockState.getMenuProvider(level, blockPos);
            if (menuProvider != null && player.openMenu(menuProvider).isPresent()) { // Paper - Fix InventoryOpenEvent cancellation
                return InteractionResult.CONSUME;
            } else {
                return InteractionResult.PASS;
            }
        } else {
            boolean flag = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
            boolean flag1 = player.isSecondaryUseActive() && flag;
            ItemStack itemStack = stack.copy();
            if (!flag1) {
                InteractionResult interactionResult = blockState.useItemOn(player.getItemInHand(hand), level, player, hand, hitResult);
                if (interactionResult.consumesAction()) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, blockPos, itemStack);
                    return interactionResult;
                }

                if (interactionResult instanceof InteractionResult.TryEmptyHandInteraction && hand == InteractionHand.MAIN_HAND) {
                    InteractionResult interactionResult1 = blockState.useWithoutItem(level, player, hitResult);
                    if (interactionResult1.consumesAction()) {
                        CriteriaTriggers.DEFAULT_BLOCK_USE.trigger(player, blockPos);
                        return interactionResult1;
                    }
                }
            }

            if (!stack.isEmpty() && !this.interactResult) { // add !interactResult SPIGOT-764
                UseOnContext useOnContext = new UseOnContext(player, hand, hitResult);
                InteractionResult interactionResult1;
                if (player.hasInfiniteMaterials()) {
                    int count = stack.getCount();
                    interactionResult1 = stack.useOn(useOnContext);
                    stack.setCount(count);
                } else {
                    interactionResult1 = stack.useOn(useOnContext);
                }

                if (interactionResult1.consumesAction()) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, blockPos, itemStack);
                }

                return interactionResult1;
            } else {
                // Paper start - Properly cancel usable items; Cancel only if cancelled + if the interact result is different from default response
                if (this.interactResult && this.interactResult != cancelledItem) {
                    this.player.resyncUsingItem(this.player);
                }
                // Paper end - Properly cancel usable items
                return InteractionResult.PASS;
            }
        }
    }

    public void setLevel(ServerLevel level) {
        this.level = level;
    }
}
