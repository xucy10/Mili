package net.minecraft.world.level.block.entity;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
// Leaves start - Lithium Sleeping Block Entity
import java.util.Objects;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.CompoundContainer;
import net.minecraft.server.level.ServerLevel;
import org.leavesmc.leaves.lithium.api.inventory.LithiumInventory;
import org.leavesmc.leaves.lithium.common.block.entity.SleepingBlockEntity;
import org.leavesmc.leaves.lithium.common.block.entity.inventory_change_tracking.InventoryChangeListener;
import org.leavesmc.leaves.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker;
import org.leavesmc.leaves.lithium.common.block.entity.inventory_comparator_tracking.ComparatorTracker;
import org.leavesmc.leaves.lithium.common.hopper.BlockStateOnlyInventory;
import org.leavesmc.leaves.lithium.common.hopper.HopperCachingState;
import org.leavesmc.leaves.lithium.common.hopper.HopperHelper;
import org.leavesmc.leaves.lithium.common.hopper.InventoryHelper;
import org.leavesmc.leaves.lithium.common.hopper.LithiumStackList;
import org.leavesmc.leaves.lithium.common.hopper.UpdateReceiver;
import org.leavesmc.leaves.lithium.common.tracking.entity.ChunkSectionEntityMovementListener;
import org.leavesmc.leaves.lithium.common.tracking.entity.ChunkSectionEntityMovementTracker;
import org.leavesmc.leaves.lithium.common.tracking.entity.ChunkSectionInventoryEntityTracker;
import org.leavesmc.leaves.lithium.common.tracking.entity.ChunkSectionItemEntityMovementTracker;
// Leaves end - Lithium Sleeping Block Entity

public class HopperBlockEntity extends RandomizableContainerBlockEntity implements Hopper, SleepingBlockEntity, ChunkSectionEntityMovementListener, LithiumInventory, InventoryChangeListener, UpdateReceiver, InventoryChangeTracker { // Leaves - Lithium Sleeping Block Entity
    public static final int MOVE_ITEM_SPEED = 8;
    public static final int HOPPER_CONTAINER_SIZE = 5;
    private static final int[][] CACHED_SLOTS = new int[54][];
    private static final int NO_COOLDOWN_TIME = -1;
    private static final Component DEFAULT_NAME = Component.translatable("container.hopper");
    private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
    public int cooldownTime = -1;
    private long tickedGameTime = Long.MIN_VALUE; // Folia - region threading
    private Direction facing;

    // CraftBukkit start - add fields and methods
    public List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;

    @Override
    public List<ItemStack> getContents() {
        return this.items;
    }

    @Override
    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.add(player);
    }

    @Override
    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.remove(player);
    }

    @Override
    public java.util.List<org.bukkit.entity.HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }
    // CraftBukkit end

    // Folia start - region threading
    @Override
    public void updateTicks(final long fromTickOffset, final long fromRedstoneTimeOffset) {
        super.updateTicks(fromTickOffset, fromRedstoneTimeOffset);
        if (this.tickedGameTime != Long.MIN_VALUE && this.tickedGameTime != Long.MAX_VALUE) { // Luminol - Lithium sleeping block entity for folia
            this.tickedGameTime += fromRedstoneTimeOffset;
        }
        // Luminol start - Lithium sleeping block entity for folia
        if (this.insertInventoryEntityFailedSearchTime != Long.MIN_VALUE)
            this.insertInventoryEntityFailedSearchTime += fromRedstoneTimeOffset;

        if (this.extractInventoryEntityFailedSearchTime != Long.MIN_VALUE)
            this.extractInventoryEntityFailedSearchTime += fromRedstoneTimeOffset;

        if (this.collectItemEntityAttemptTime != Long.MIN_VALUE)
            this.collectItemEntityAttemptTime += fromRedstoneTimeOffset;
        // Luminol end
    }
    // Folia end - region threading

    public HopperBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.HOPPER, pos, blockState);
        this.facing = blockState.getValue(HopperBlock.FACING);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, this.items);
        }

        this.cooldownTime = input.getIntOr("TransferCooldown", -1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, this.items);
        }

        output.putInt("TransferCooldown", this.cooldownTime);
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        this.unpackLootTable(null);
        return ContainerHelper.removeItem(this.getItems(), index, count);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.unpackLootTable(null);
        this.getItems().set(index, stack);
        stack.limitSize(this.getMaxStackLeaves(stack)); // Mili - item over-stack util
    }

    @Override
    public void setBlockState(BlockState blockState) {
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && this.level != null && !this.level.isClientSide() && blockState.getValue(HopperBlock.FACING) != this.getBlockState().getValue(HopperBlock.FACING)) this.invalidateCachedData(); // Leaves - Lithium Sleeping Block Entity
        super.setBlockState(blockState);
        this.facing = blockState.getValue(HopperBlock.FACING);
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    public static void pushItemsTick(Level level, BlockPos pos, BlockState state, HopperBlockEntity blockEntity) {
        blockEntity.cooldownTime--;
        blockEntity.tickedGameTime = level.getRedstoneGameTime(); // Folia - region threading
        if (!blockEntity.isOnCooldown()) {
            blockEntity.setCooldown(0);
            // Spigot start
            boolean result = tryMoveItems(level, pos, state, blockEntity, () -> {
                return suckInItems(level, blockEntity);
            });
            if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) blockEntity.checkSleepingConditions(); // Leaves - Lithium Sleeping Block Entity
            if (!result && blockEntity.level.spigotConfig.hopperCheck > 1) {
                blockEntity.setCooldown(blockEntity.level.spigotConfig.hopperCheck);
            }
            // Spigot end
        }
    }

    // Paper start - Perf: Optimize Hoppers
    private static final int HOPPER_EMPTY = 0;
    private static final int HOPPER_HAS_ITEMS = 1;
    private static final int HOPPER_IS_FULL = 2;

    private static int getFullState(final HopperBlockEntity hopper) {
        hopper.unpackLootTable(null);

        final List<ItemStack> hopperItems = hopper.items;

        boolean empty = true;
        boolean full = true;

        for (int i = 0, len = hopperItems.size(); i < len; ++i) {
            final ItemStack stack = hopperItems.get(i);
            if (stack.isEmpty()) {
                full = false;
                continue;
            }

            if (!full) {
                // can't be full
                return HOPPER_HAS_ITEMS;
            }

            empty = false;

            if (stack.getCount() != stack.getMaxStackSize()) {
                // can't be full or empty
                return HOPPER_HAS_ITEMS;
            }
        }

        return empty ? HOPPER_EMPTY : (full ? HOPPER_IS_FULL : HOPPER_HAS_ITEMS);
    }
    // Paper end - Perf: Optimize Hoppers

    private static boolean tryMoveItems(Level level, BlockPos pos, BlockState state, HopperBlockEntity blockEntity, BooleanSupplier validator) {
        if (level.isClientSide()) {
            return false;
        } else {
            if (!blockEntity.isOnCooldown() && state.getValue(HopperBlock.ENABLED)) {
                boolean flag = false;
                // final int fullState = getFullState(blockEntity); // Paper - Perf: Optimize Hoppers  // Luminol - vanilla hopper
                if (/*fullState != HOPPER_EMPTY*/!blockEntity.isEmpty()) { // Paper - Perf: Optimize Hoppers  // Luminol - vanilla hopper
                    flag = ejectItems(level, pos, blockEntity);
                }

                if (/*fullState != HOPPER_IS_FULL || flag*/!blockEntity.inventoryFull()) { // Paper - Perf: Optimize Hoppers  // Luminol - vanilla hopper
                    flag |= validator.getAsBoolean(); // Paper - note: this is not a validator, it's what adds/sucks in items
                }

                // Leaves start - Wool hopper counter
                if (fun.bm.mili.config.modules.function.WoolHopperCounterConfig.unlimitedSpeed && org.leavesmc.leaves.util.HopperCounter.isEnabled()) {
                    net.minecraft.world.item.DyeColor woolColor = org.leavesmc.leaves.util.WoolUtils.getWoolColorAtPosition(level, blockEntity.getBlockPos().relative(state.getValue(HopperBlock.FACING)));
                    if (woolColor != null) {
                        for (int i = 0; i < Short.MAX_VALUE; i++) {
                            flag |= suckInItems(level, blockEntity);
                            if (!flag) {
                                break;
                            } else {
                                woolHopperCounter(level, pos, state, HopperBlockEntity.getContainerAt(level, pos));
                            }
                        }
                    }
                }
                // Leaves end - Wool hopper counter

                if (flag) {
                    blockEntity.setCooldown(level.spigotConfig.hopperTransfer); // Spigot
                    // Leaves start - Wool hopper counter
                    if (fun.bm.mili.config.modules.function.WoolHopperCounterConfig.unlimitedSpeed && org.leavesmc.leaves.util.HopperCounter.isEnabled() && woolHopperCounter(level, pos, state, HopperBlockEntity.getContainerAt(level, pos))) {
                        blockEntity.setCooldown(0);
                        return true;
                    }
                    // Leaves end - Wool hopper counter
                    setChanged(level, pos, state);
                    // Leaves start - pca
                    if (fun.bm.mili.config.modules.function.protocol.PcaSyncProtocolConfig.enable) {
                        org.leavesmc.leaves.protocol.PcaSyncProtocol.syncBlockEntityToClient(blockEntity);
                    }
                    // Leaves end - pca
                    // Leaves start - Lithium Sleeping Block Entity
                    if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled
                            && !blockEntity.isOnCooldown()
                            && !blockEntity.isSleeping()
                            && !state.getValue(HopperBlock.ENABLED)) {
                        blockEntity.lithium$startSleeping();
                    }
                    // Leaves end - Lithium Sleeping Block Entity
                    return true;
                }
            }

            return false;
        }
    }

    private boolean inventoryFull() {
        for (ItemStack itemStack : this.items) {
            if (itemStack.isEmpty() || itemStack.getCount() != itemStack.getMaxStackSize()) {
                return false;
            }
        }

        return true;
    }

    // Paper start - Perf: Optimize Hoppers
    // Folia - region threading - moved to RegionizedWorldData

    private static boolean hopperPush(final Level level, final Container destination, final Direction direction, final HopperBlockEntity hopper) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = level.getCurrentWorldData(); // Folia - region threading
        worldData.skipPushModeEventFire = worldData.skipHopperEvents; // Folia - region threading
        boolean foundItem = false;
        for (int i = 0; i < hopper.getContainerSize(); ++i) {
            final ItemStack item = hopper.getItem(i);
            if (!item.isEmpty()) {
                foundItem = true;
                ItemStack origItemStack = item;
                ItemStack movedItem = origItemStack;

                final int originalItemCount = origItemStack.getCount();
                final int movedItemCount = Math.min(level.spigotConfig.hopperAmount, originalItemCount);
                origItemStack.setCount(movedItemCount);

                // We only need to fire the event once to give protection plugins a chance to cancel this event
                // Because nothing uses getItem, every event call should end up the same result.
                if (!worldData.skipPushModeEventFire) { // Folia - region threading
                    movedItem = callPushMoveEvent(destination, movedItem, hopper);
                    if (movedItem == null) { // cancelled
                        origItemStack.setCount(originalItemCount);
                        return false;
                    }
                }

                final ItemStack remainingItem = addItem(hopper, destination, movedItem, direction);
                final int remainingItemCount = remainingItem.getCount();
                if (remainingItemCount != movedItemCount) {
                    origItemStack = origItemStack.copy(true);
                    origItemStack.setCount(originalItemCount);
                    if (!origItemStack.isEmpty()) {
                        origItemStack.setCount(originalItemCount - movedItemCount + remainingItemCount);
                    }
                    hopper.setItem(i, origItemStack);
                    destination.setChanged();
                    return true;
                }
                origItemStack.setCount(originalItemCount);
            }
        }
        if (foundItem && level.paperConfig().hopper.cooldownWhenFull && !fun.bm.mili.config.modules.function.TechnicalSurvivalModeConfig.enabled) { // Inventory was full - cooldown // Mili - mc technical survival mode
            hopper.setCooldown(level.spigotConfig.hopperTransfer);
        }
        return false;
    }

    private static boolean hopperPull(final Level level, final Hopper hopper, final Container container, ItemStack origItemStack, final int i) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = level.getCurrentWorldData(); // Folia - region threading
        ItemStack movedItem = origItemStack;
        final int originalItemCount = origItemStack.getCount();
        final int movedItemCount = Math.min(level.spigotConfig.hopperAmount, originalItemCount);
        container.setChanged(); // original logic always marks source inv as changed even if no move happens.
        movedItem.setCount(movedItemCount);

        if (!worldData.skipPullModeEventFire) { // Folia - region threading
            movedItem = callPullMoveEvent(hopper, container, movedItem);
            if (movedItem == null) { // cancelled
                origItemStack.setCount(originalItemCount);
                // Drastically improve performance by returning true.
                // No plugin could have relied on the behavior of false as the other call
                // site for IMIE did not exhibit the same behavior
                return true;
            }
        }

        final ItemStack remainingItem = addItem(container, hopper, movedItem, null);
        final int remainingItemCount = remainingItem.getCount();
        if (remainingItemCount != movedItemCount) {
            origItemStack = origItemStack.copy(true);
            origItemStack.setCount(originalItemCount);
            if (!origItemStack.isEmpty()) {
                origItemStack.setCount(originalItemCount - movedItemCount + remainingItemCount);
            }

            // IGNORE_TILE_UPDATES.set(true); // Folia - region threading // Luminol - vanilla hopper
            container.setItem(i, origItemStack);
            //IGNORE_TILE_UPDATES.set(false); // Folia - region threading // Luminol - vanilla hopper
            container.setChanged();
            return true;
        }
        origItemStack.setCount(originalItemCount);

        if (level.paperConfig().hopper.cooldownWhenFull && !fun.bm.mili.config.modules.function.TechnicalSurvivalModeConfig.enabled) { // Mili - mc technical survival mode
            applyCooldown(hopper);
        }

        return false;
    }

    @Nullable
    private static ItemStack callPushMoveEvent(Container destination, ItemStack itemStack, HopperBlockEntity hopper) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData(); // Folia - region threading
        final org.bukkit.inventory.Inventory destinationInventory = getInventory(destination);
        final io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent event = new io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent(
            hopper.getOwner(false).getInventory(),
            org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack),
            destinationInventory,
            true
        );
        final boolean result = event.callEvent();
        if (!event.calledGetItem && !event.calledSetItem) {
            worldData.skipPushModeEventFire = true; // Folia - region threading
        }
        if (!result) {
            applyCooldown(hopper);
            return null;
        }

        if (event.calledSetItem) {
            return org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
        } else {
            return itemStack;
        }
    }

    @Nullable
    private static ItemStack callPullMoveEvent(final Hopper hopper, final Container container, final ItemStack itemstack) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData(); // Folia - region threading
        final org.bukkit.inventory.Inventory sourceInventory = getInventory(container);
        final org.bukkit.inventory.Inventory destination = getInventory(hopper);

        // Mirror is safe as no plugins ever use this item
        final io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent event = new io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent(sourceInventory, org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack), destination, false);
        final boolean result = event.callEvent();
        if (!event.calledGetItem && !event.calledSetItem) {
            worldData.skipPullModeEventFire = true; // Folia - region threading
        }
        if (!result) {
            applyCooldown(hopper);
            return null;
        }

        if (event.calledSetItem) {
            return org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
        } else {
            return itemstack;
        }
    }

    private static org.bukkit.inventory.Inventory getInventory(final Container container) {
        final org.bukkit.inventory.Inventory sourceInventory;
        if (container instanceof net.minecraft.world.CompoundContainer compoundContainer) {
            // Have to special-case large chests as they work oddly
            sourceInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest(compoundContainer);
        } else if (container instanceof BlockEntity blockEntity) {
            sourceInventory = blockEntity.getOwner(false).getInventory();
        } else if (container.getOwner() != null) {
            sourceInventory = container.getOwner().getInventory();
        } else {
            sourceInventory = new org.bukkit.craftbukkit.inventory.CraftInventory(container);
        }
        return sourceInventory;
    }

    private static void applyCooldown(final Hopper hopper) {
        if (hopper instanceof HopperBlockEntity blockEntity && blockEntity.getLevel() != null) {
            blockEntity.setCooldown(blockEntity.getLevel().spigotConfig.hopperTransfer);
            blockEntity.skipNextSleepCheckAfterCooldown = true; // Leaves - Lithium Sleeping Block Entity
        }
    }

    private static boolean allMatch(Container container, Direction direction, java.util.function.BiPredicate<ItemStack, Integer> test) {
        if (container instanceof WorldlyContainer) {
            for (int slot : ((WorldlyContainer) container).getSlotsForFace(direction)) {
                if (!test.test(container.getItem(slot), slot)) {
                    return false;
                }
            }
        } else {
            int size = container.getContainerSize();
            for (int slot = 0; slot < size; slot++) {
                if (!test.test(container.getItem(slot), slot)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean anyMatch(Container container, Direction direction, java.util.function.BiPredicate<ItemStack, Integer> test) {
        if (container instanceof WorldlyContainer) {
            for (int slot : ((WorldlyContainer) container).getSlotsForFace(direction)) {
                if (test.test(container.getItem(slot), slot)) {
                    return true;
                }
            }
        } else {
            int size = container.getContainerSize();
            for (int slot = 0; slot < size; slot++) {
                if (test.test(container.getItem(slot), slot)) {
                    return true;
                }
            }
        }
        return true;
    }
    private static final java.util.function.BiPredicate<ItemStack, Integer> STACK_SIZE_TEST = (itemStack, i) -> itemStack.getCount() >= itemStack.getMaxStackSize();
    private static final java.util.function.BiPredicate<ItemStack, Integer> IS_EMPTY_TEST = (itemStack, i) -> itemStack.isEmpty();
    // Paper end - Perf: Optimize Hoppers

    private static boolean ejectItems(Level level, BlockPos pos, HopperBlockEntity blockEntity) {
        // Leaves start - hopper counter
        if (org.leavesmc.leaves.util.HopperCounter.isEnabled()) {
            if (woolHopperCounter(level, pos, level.getBlockState(pos), HopperBlockEntity.getContainerAt(level, pos))) {
                return true;
            }
        }
        // Leaves end - hopper counter
        Container attachedContainer = me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled ? blockEntity.getInsertInventory(level) : getAttachedContainer(level, pos, blockEntity); // Leaves - Lithium Sleeping Block Entity
        if (attachedContainer == null) {
            return false;
        } else {
            Direction opposite = blockEntity.facing.getOpposite();
            // Leaves start - Lithium Sleeping Block Entity
            if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) {
                Boolean res = lithiumInsert(level, pos, blockEntity, attachedContainer);
                if (res != null) {
                    return res;
                }
            }
            // Leaves end - Lithium Sleeping Block Entity
            if (isFullContainer(attachedContainer, opposite)) {
                return false;
            } else {
                // Paper start - Perf: Optimize Hoppers
                // Luminol start - vanilla hopper
                // return hopperPush(level, attachedContainer, opposite, blockEntity);
                for (int i = 0; i < blockEntity.getContainerSize(); i++) {
                    ItemStack item = blockEntity.getItem(i);
                    if (!item.isEmpty()) {
                        boolean noItemCost = fun.bm.mili.carpet.config.modules.GeneralCompatConfig.hopperNoItemCost && org.leavesmc.leaves.util.WoolUtils.getWoolColorAtPosition(level, pos.above()) != null;
                        int count = item.getCount();
                        // CraftBukkit start - Call event when pushing items into other inventories
                        ItemStack original = item.copy();
                        org.bukkit.craftbukkit.inventory.CraftItemStack oitemstack = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(
                            blockEntity.removeItem(i, level.spigotConfig.hopperAmount)
                        ); // Spigot

                        org.bukkit.inventory.Inventory destinationInventory;
                        // Have to special case large chests as they work oddly
                        if (attachedContainer instanceof final net.minecraft.world.CompoundContainer compoundContainer) {
                            destinationInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest(compoundContainer);
                        } else if (attachedContainer.getOwner() != null) {
                            destinationInventory = attachedContainer.getOwner().getInventory();
                        } else {
                            destinationInventory = new org.bukkit.craftbukkit.inventory.CraftInventory(attachedContainer);
                        }

                        org.bukkit.event.inventory.InventoryMoveItemEvent event = new org.bukkit.event.inventory.InventoryMoveItemEvent(
                            blockEntity.getOwner().getInventory(),
                            oitemstack,
                            destinationInventory,
                            true
                        );
                        if (!event.callEvent()) {
                            blockEntity.setItem(i, original);
                            blockEntity.setCooldown(level.spigotConfig.hopperTransfer); // Delay hopper checks // Spigot
                            return false;
                        }
                        int origCount = event.getItem().getAmount(); // Spigot
                        ItemStack itemStack = HopperBlockEntity.addItem(blockEntity, attachedContainer, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem()), opposite);
                        // CraftBukkit end
                        int moved = origCount - itemStack.getCount();

                        if (moved > 0) {
                            attachedContainer.setChanged();
                            if (noItemCost) {
                                blockEntity.setItem(i, original);
                                return true;
                            }
                        }

                        if (itemStack.isEmpty()) {
                            return true;
                        }

                        item.setCount(count);
                        // Spigot start
                        item.shrink(moved);
                        if (count <= level.spigotConfig.hopperAmount) {
                            // Spigot end
                            blockEntity.setItem(i, item);
                        }
                    }
                }

                return false;
                // Paper end - Perf: Optimize Hoppers
            }
        }
    }

    // Leaves start - hopper counter
    private static boolean woolHopperCounter(Level level, BlockPos blockPos, BlockState state, @Nullable Container container) {
        if (container == null) {
            return false;
        }
        net.minecraft.world.item.DyeColor woolColor = org.leavesmc.leaves.util.WoolUtils.getWoolColorAtPosition(level, blockPos.relative(state.getValue(HopperBlock.FACING)));
        if (woolColor != null) {
            for (int i = 0; i < container.getContainerSize(); ++i) {
                if (!container.getItem(i).isEmpty()) {
                    ItemStack itemstack = container.getItem(i);
                    org.leavesmc.leaves.util.HopperCounter.getCounter(woolColor).add(level.getServer(), itemstack);
                    container.setItem(i, ItemStack.EMPTY);
                }
            }
            return true;
        }
        return false;
    }
    // Leaves end - hopper counter

    private static int[] getSlots(Container container, Direction direction) {
        if (container instanceof WorldlyContainer worldlyContainer) {
            return worldlyContainer.getSlotsForFace(direction);
        } else {
            int containerSize = container.getContainerSize();
            if (containerSize < CACHED_SLOTS.length) {
                int[] ints = CACHED_SLOTS[containerSize];
                if (ints != null) {
                    return ints;
                } else {
                    int[] ints1 = createFlatSlots(containerSize);
                    CACHED_SLOTS[containerSize] = ints1;
                    return ints1;
                }
            } else {
                return createFlatSlots(containerSize);
            }
        }
    }

    private static int[] createFlatSlots(int size) {
        int[] ints = new int[size];
        int i = 0;

        while (i < ints.length) {
            ints[i] = i++;
        }

        return ints;
    }

    private static boolean isFullContainer(Container container, Direction direction) {
        int[] slots = getSlots(container, direction);

        for (int i : slots) {
            ItemStack item = container.getItem(i);
            if (item.getCount() < item.getMaxStackSize()) {
                return false;
            }
        }

        return true;
    }

    public static boolean suckInItems(Level level, Hopper hopper) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData(); // Folia - region threading
        BlockPos blockPos = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ());
        BlockState blockState = level.getBlockState(blockPos);
        Container sourceContainer = me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled ? getExtractInventory(level, hopper, blockPos, blockState) : getSourceContainer(level, hopper, blockPos, blockState); // Leaves - Lithium Sleeping Block Entity
        if (sourceContainer != null) {
            Direction direction = Direction.DOWN;
            worldData.skipPullModeEventFire = worldData.skipHopperEvents; // Paper - Perf: Optimize Hoppers // Folia - region threading
            // Leaves start - Lithium Sleeping Block Entity
            if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) {
                Boolean res = lithiumExtract(level, hopper, sourceContainer);
                if (res != null) {
                    return res;
                }
            }
            // Leaves end - Lithium Sleeping Block Entity

            for (int i : getSlots(sourceContainer, direction)) {
                if (tryTakeInItemFromSlot(hopper, sourceContainer, i, direction, level)) { // Spigot
                    return true;
                }
            }

            return false;
        } else {
            boolean flag = hopper.isGridAligned() && (!fun.bm.mili.config.modules.function.OldFeatureConfig.oldHopperSuckInBehavior && blockState.isCollisionShapeFullBlock(level, blockPos)) && !blockState.is(BlockTags.DOES_NOT_BLOCK_HOPPERS); // Leaves - oldHopperSuckInBehavior
            if (!flag) {
                for (ItemEntity itemEntity : me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled ? lithiumGetInputItemEntities(level, hopper) : getItemsAtAndAbove(level, hopper)) { // Leaves - Lithium Sleeping Block Entity
                    if (addItem(hopper, itemEntity)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    private static boolean tryTakeInItemFromSlot(Hopper hopper, Container container, int slot, Direction direction, Level level) { // Spigot
        ItemStack item = container.getItem(slot);
        if (!item.isEmpty() && canTakeItemFromContainer(hopper, container, item, slot, direction)) {
            // Paper start - Perf: Optimize Hoppers
            // Luminol start - vanilla hopper
            // return hopperPull(level, hopper, container, item, slot);
            int count = item.getCount();
            // CraftBukkit start - Call event on collection of items from inventories into the hopper
            ItemStack original = item.copy();
            org.bukkit.craftbukkit.inventory.CraftItemStack oitemstack = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(
                container.removeItem(slot, level.spigotConfig.hopperAmount) // Spigot
            );

            org.bukkit.inventory.Inventory sourceInventory;
            // Have to special case large chests as they work oddly
            if (container instanceof final net.minecraft.world.CompoundContainer compoundContainer) {
                sourceInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest(compoundContainer);
            } else if (container.getOwner() != null) {
                sourceInventory = container.getOwner().getInventory();
            } else {
                sourceInventory = new org.bukkit.craftbukkit.inventory.CraftInventory(container);
            }

            org.bukkit.event.inventory.InventoryMoveItemEvent event = new org.bukkit.event.inventory.InventoryMoveItemEvent(
                sourceInventory,
                oitemstack,
                hopper.getOwner().getInventory(),
                false
            );

            if (!event.callEvent()) {
                container.setItem(slot, original);

                if (hopper instanceof final HopperBlockEntity hopperBlockEntity) {
                    hopperBlockEntity.setCooldown(level.spigotConfig.hopperTransfer); // Spigot
                }

                return false;
            }
            int origCount = event.getItem().getAmount(); // Spigot
            ItemStack itemStack = HopperBlockEntity.addItem(container, hopper, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem()), null);
            // CraftBukkit end

            if (itemStack.isEmpty()) {
                container.setChanged();
                return true;
            }

            item.setCount(count);
            // Spigot start
            item.shrink(origCount - itemStack.getCount());
            if (count <= level.spigotConfig.hopperAmount) {
                // Spigot end
                container.setItem(slot, item);
            }
            // Paper end - Perf: Optimize Hoppers
        }

        return false;
    }

    public static boolean addItem(Container container, ItemEntity item) {
        if (fun.bm.mili.config.modules.function.WoolHopperCounterConfig.unlimitedSpeed && org.leavesmc.leaves.util.HopperCounter.isEnabled() && item.isRemoved()) return false; // Leaves - Wool hopper counter
        boolean flag = false;
        // CraftBukkit start
        if (org.bukkit.event.inventory.InventoryPickupItemEvent.getHandlerList().getRegisteredListeners().length > 0) { // Paper - optimize hoppers
        org.bukkit.event.inventory.InventoryPickupItemEvent event = new org.bukkit.event.inventory.InventoryPickupItemEvent(
            getInventory(container), (org.bukkit.entity.Item) item.getBukkitEntity() // Paper - Perf: Optimize Hoppers; use getInventory() to avoid snapshot creation
        );
        if (!event.callEvent()) {
            return false;
        }
        // CraftBukkit end
        } // Paper - Perf: Optimize Hoppers
        ItemStack itemStack = item.getItem().copy();
        // Mili start - item over-stack util
        int restoreCount = 0;
        ItemStack itemStack1 = itemStack.copy();
        if (org.leavesmc.leaves.util.ItemOverstackUtils.isStackable(item.getItem())) {
            // We need the hopper suck in the item with the vanilla max stack size each time
            int vanillaMax = itemStack.getMaxStackSize();
            restoreCount = Math.max(0, itemStack.getCount() - vanillaMax);
            itemStack.setCount(itemStack.getCount() - restoreCount);
        }
        itemStack1.setCount(addItem(null, container, itemStack, null).getCount());
        itemStack1.setCount(itemStack1.getCount() + restoreCount);
        // Mili end - item over-stack util
        if (itemStack1.isEmpty()) {
            flag = true;
            item.setItem(ItemStack.EMPTY);
            item.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
        } else {
            item.setItem(itemStack1);
        }

        return flag || (restoreCount != 0); // Mili - item over-stack util
    }

    public static ItemStack addItem(@Nullable Container source, Container destination, ItemStack stack, @Nullable Direction direction) {
        if (destination instanceof WorldlyContainer worldlyContainer && direction != null) {
            int[] slotsForFace = worldlyContainer.getSlotsForFace(direction);

            for (int i = 0; i < slotsForFace.length && !stack.isEmpty(); i++) {
                stack = tryMoveInItem(source, destination, stack, slotsForFace[i], direction);
            }
        } else {
            int containerSize = destination.getContainerSize();

            for (int i = 0; i < containerSize && !stack.isEmpty(); i++) {
                stack = tryMoveInItem(source, destination, stack, i, direction);
            }
        }

        return stack;
    }

    private static boolean canPlaceItemInContainer(Container container, ItemStack stack, int slot, @Nullable Direction direction) {
        return container.canPlaceItem(slot, stack)
            && !(container instanceof WorldlyContainer worldlyContainer && !worldlyContainer.canPlaceItemThroughFace(slot, stack, direction));
    }

    private static boolean canTakeItemFromContainer(Container source, Container destination, ItemStack stack, int slot, Direction direction) {
        return destination.canTakeItem(source, slot, stack)
            && !(destination instanceof WorldlyContainer worldlyContainer && !worldlyContainer.canTakeItemThroughFace(slot, stack, direction));
    }

    private static ItemStack tryMoveInItem(@Nullable Container source, Container destination, ItemStack stack, int slot, @Nullable Direction direction) {
        ItemStack item = destination.getItem(slot);
        if (canPlaceItemInContainer(destination, stack, slot, direction)) {
            boolean flag = false;
            boolean isEmpty = destination.isEmpty();
            if (item.isEmpty()) {
                // Spigot start - SPIGOT-6693, SimpleContainer#setItem
                ItemStack leftover = ItemStack.EMPTY; // Paper - Make hoppers respect inventory max stack size
                if (!stack.isEmpty() && (stack.getCount() > destination.getMaxStackSize() || stack.getCount() > stack.getMaxStackSize())) { // Mili - item over-stack util
                    leftover = stack; // Paper - Make hoppers respect inventory max stack size
                    stack = stack.split(Math.min(destination.getMaxStackSize(), stack.getMaxStackSize())); // Mili - item over-stack util
                }
                // Spigot end
                //IGNORE_TILE_UPDATES.set(Boolean.TRUE); // Paper - Perf: Optimize Hoppers // Folia - region threading // Luminol - vanilla hopper
                destination.setItem(slot, stack);
                //IGNORE_TILE_UPDATES.set(Boolean.FALSE); // Paper - Perf: Optimize Hoppers // Folia - region threading // Luminol - vanilla hopper
                stack = leftover; // Paper - Make hoppers respect inventory max stack size
                flag = true;
            } else if (canMergeItems(item, stack)) {
                int i = Math.min(stack.getMaxStackSize(), destination.getMaxStackSize()) - item.getCount(); // Paper - Make hoppers respect inventory max stack size
                int min = Math.min(stack.getCount(), i);
                stack.shrink(min);
                item.grow(min);
                flag = min > 0;
            }

            if (flag) {
                if (isEmpty && destination instanceof HopperBlockEntity hopperBlockEntity && !hopperBlockEntity.isOnCustomCooldown()) {
                    int min = 0;
                    if (source instanceof HopperBlockEntity hopperBlockEntity1 && hopperBlockEntity.tickedGameTime >= hopperBlockEntity1.tickedGameTime) {
                        min = 1;
                    }

                    hopperBlockEntity.setCooldown(hopperBlockEntity.level.spigotConfig.hopperTransfer - min); // Spigot
                }

                destination.setChanged();
            }
        }

        return stack;
    }

    // CraftBukkit start
    private static @Nullable Container runHopperInventorySearchEvent(
        Container container,
        org.bukkit.craftbukkit.block.CraftBlock hopper,
        org.bukkit.craftbukkit.block.CraftBlock searchLocation,
        org.bukkit.event.inventory.HopperInventorySearchEvent.ContainerType containerType
    ) {
        org.bukkit.event.inventory.HopperInventorySearchEvent event = new org.bukkit.event.inventory.HopperInventorySearchEvent(
            (container != null) ? new org.bukkit.craftbukkit.inventory.CraftInventory(container) : null,
            containerType,
            hopper,
            searchLocation
        );
        event.callEvent();
        return (event.getInventory() != null) ? ((org.bukkit.craftbukkit.inventory.CraftInventory) event.getInventory()).getInventory() : null;
    }
    // CraftBukkit end

    private static @Nullable Container getAttachedContainer(Level level, BlockPos pos, HopperBlockEntity blockEntity) {
        // Paper start
        BlockPos searchPosition = pos.relative(blockEntity.facing);
        Container inventory = getContainerAt(level, searchPosition);
        if (org.bukkit.event.inventory.HopperInventorySearchEvent.getHandlerList().getRegisteredListeners().length == 0) return inventory;

        org.bukkit.craftbukkit.block.CraftBlock hopper = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
        org.bukkit.craftbukkit.block.CraftBlock searchBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, searchPosition);
        return HopperBlockEntity.runHopperInventorySearchEvent(
            inventory,
            hopper,
            searchBlock,
            org.bukkit.event.inventory.HopperInventorySearchEvent.ContainerType.DESTINATION
        );
        // Paper end
    }

    private static @Nullable Container getSourceContainer(Level level, Hopper hopper, BlockPos pos, BlockState state) {
        // Paper start
        final Container inventory = HopperBlockEntity.getContainerAt(level, pos, state, hopper.getLevelX(), hopper.getLevelY() + 1.0D, hopper.getLevelZ());
        if (org.bukkit.event.inventory.HopperInventorySearchEvent.getHandlerList().getRegisteredListeners().length == 0) return inventory;

        final BlockPos hopperPos = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY(), hopper.getLevelZ());
        org.bukkit.craftbukkit.block.CraftBlock hopperBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, hopperPos);
        org.bukkit.craftbukkit.block.CraftBlock containerBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, hopperPos.above());
        return HopperBlockEntity.runHopperInventorySearchEvent(
            inventory,
            hopperBlock,
            containerBlock,
            org.bukkit.event.inventory.HopperInventorySearchEvent.ContainerType.SOURCE
        );
        // Paper end
    }

    public static List<ItemEntity> getItemsAtAndAbove(Level level, Hopper hopper) {
        AABB aabb = hopper.getSuckAabb().move(hopper.getLevelX() - 0.5, hopper.getLevelY() - 0.5, hopper.getLevelZ() - 0.5);
        return level.getEntitiesOfClass(ItemEntity.class, aabb, EntitySelector.ENTITY_STILL_ALIVE);
    }

    public static @Nullable Container getContainerAt(Level level, BlockPos pos) {
        return getContainerAt(level, pos, level.getBlockState(pos), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, false); // Paper - Optimize hoppers  // Luminol - vanilla hopper
    }

    private static @Nullable Container getContainerAt(Level level, BlockPos pos, BlockState state, double x, double y, double z) {
        // Paper start - Perf: Optimize Hoppers
        return HopperBlockEntity.getContainerAt(level, pos, state, x, y, z, false);
    }
    private static @Nullable Container getContainerAt(Level level, BlockPos pos, BlockState state, double x, double y, double z, final boolean optimizeEntities) {
        // Paper end - Perf: Optimize Hoppers
        Container blockContainer = getBlockContainer(level, pos, state);
        if (blockContainer == null /*&& (!optimizeEntities || !level.paperConfig().hopper.ignoreOccludingBlocks || !state.getBukkitMaterial().isOccluding())*/) { // Paper - Perf: Optimize Hoppers  // Luminol - vanilla hopper
            blockContainer = getEntityContainer(level, x, y, z);
        }

        return blockContainer;
    }

    private static @Nullable Container getBlockContainer(Level level, BlockPos pos, BlockState state) {
        if (!level.spigotConfig.hopperCanLoadChunks && !level.hasChunkAt(pos)) return null; // Spigot
        Block block = state.getBlock();
        if (block instanceof WorldlyContainerHolder) {
            return ((WorldlyContainerHolder)block).getContainer(state, level, pos);
        } else if (state.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container container) {
            if (container instanceof ChestBlockEntity && block instanceof ChestBlock) {
                container = ChestBlock.getContainer((ChestBlock)block, state, level, pos, true);
            }

            return container;
        } else {
            return null;
        }
    }

    private static @Nullable Container getEntityContainer(Level level, double x, double y, double z) {
        List<Entity> entities = level.getEntitiesOfClass(
            (Class) Container.class, new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5), EntitySelector.CONTAINER_ENTITY_SELECTOR // Paper - Perf: Optimize hoppers
        );
        return !entities.isEmpty() ? (Container)entities.get(level.random.nextInt(entities.size())) : null;
    }

    private static boolean canMergeItems(ItemStack stack1, ItemStack stack2) {
        return stack1.getCount() < stack1.getMaxStackSize() && ItemStack.isSameItemSameComponents(stack1, stack2); // Paper - Perf: Optimize Hoppers; used to return true for full itemstacks?!
    }

    @Override
    public double getLevelX() {
        return this.worldPosition.getX() + 0.5;
    }

    @Override
    public double getLevelY() {
        return this.worldPosition.getY() + 0.5;
    }

    @Override
    public double getLevelZ() {
        return this.worldPosition.getZ() + 0.5;
    }

    @Override
    public boolean isGridAligned() {
        return true;
    }

    public void setCooldown(int cooldownTime) {
        // Leaves start - Lithium Sleeping Block Entity
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) {
            if (cooldownTime == 7) {
                if (this.tickedGameTime == Long.MAX_VALUE) {
                    this.sleepOnlyCurrentTick();
                } else {
                    this.wakeUpNow();
                }
            } else if (cooldownTime > 0 && this.sleepingTicker != null) {
                this.wakeUpNow();
            }
        }
        // Leaves end - Lithium Sleeping Block Entity
        this.cooldownTime = cooldownTime;
    }

    private boolean isOnCooldown() {
        return this.cooldownTime > 0;
    }

    private boolean isOnCustomCooldown() {
        return this.cooldownTime > 8;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) this.lithium$emitStackListReplaced(); // Leaves - Lithium Sleeping Block Entity
    }

    public static void entityInside(Level level, BlockPos pos, BlockState state, Entity entity, HopperBlockEntity blockEntity) {
        if (entity instanceof ItemEntity itemEntity
            && !itemEntity.getItem().isEmpty()
            && entity.getBoundingBox().move(-pos.getX(), -pos.getY(), -pos.getZ()).intersects(blockEntity.getSuckAabb())) {
            tryMoveItems(level, pos, state, blockEntity, () -> addItem(blockEntity, itemEntity));
        }
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return new HopperMenu(id, player, this);
    }

    // Leaves start - Lithium Sleeping Block Entity
    private LevelChunk.@Nullable RebindableTickingBlockEntityWrapper tickWrapper = null;
    @Nullable private TickingBlockEntity sleepingTicker = null;
    private long myModCountAtLastInsert, myModCountAtLastExtract, myModCountAtLastItemCollect;
    private boolean skipNextSleepCheckAfterCooldown = false;

    private HopperCachingState.BlockInventory insertionMode = HopperCachingState.BlockInventory.UNKNOWN;
    private HopperCachingState.BlockInventory extractionMode = HopperCachingState.BlockInventory.UNKNOWN;

    //The currently used block inventories
    @Nullable
    private Container insertBlockInventory, extractBlockInventory;

    //The currently used inventories (optimized type, if not present, skip optimizations)
    @Nullable
    private LithiumInventory insertInventory, extractInventory;
    @Nullable //Null iff corresp. LithiumInventory field is null
    private LithiumStackList insertStackList, extractStackList;
    //Mod count used to avoid transfer attempts that are known to fail (no change since last attempt)
    private long insertStackListModCount, extractStackListModCount;

    @Nullable
    private List<ChunkSectionItemEntityMovementTracker> collectItemEntityTracker;
    private boolean collectItemEntityTrackerWasEmpty;
    @Nullable
    private AABB collectItemEntityBox;
    private long collectItemEntityAttemptTime;

    @Nullable
    private List<ChunkSectionInventoryEntityTracker> extractInventoryEntityTracker;
    @Nullable
    private AABB extractInventoryEntityBox;
    private long extractInventoryEntityFailedSearchTime;

    @Nullable
    private List<ChunkSectionInventoryEntityTracker> insertInventoryEntityTracker;
    @Nullable
    private AABB insertInventoryEntityBox;
    private long insertInventoryEntityFailedSearchTime;

    private boolean shouldCheckSleep;

    private void checkSleepingConditions() {
        if (this.cooldownTime > 0 || this.getLevel() == null || skipNextSleepCheckAfterCooldown) {
            return;
        }
        if (isSleeping()) {
            return;
        }
        if (!this.shouldCheckSleep) {
            this.shouldCheckSleep = true;
            return;
        }
        boolean listenToExtractTracker = false;
        boolean listenToInsertTracker = false;
        boolean listenToExtractEntities = false;
        boolean listenToItemEntities = false;
        boolean listenToInsertEntities = false;

        LithiumStackList thisStackList = InventoryHelper.getLithiumStackList(this);

        if (this.extractionMode != HopperCachingState.BlockInventory.BLOCK_STATE && thisStackList.getFullSlots() != thisStackList.size()) {
            if (this.extractionMode == HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
                Container blockInventory = this.extractBlockInventory;
                if (this.extractStackList != null &&
                        blockInventory instanceof InventoryChangeTracker) {
                    if (!this.extractStackList.maybeSendsComparatorUpdatesOnFailedExtract() || (blockInventory instanceof ComparatorTracker comparatorTracker && !comparatorTracker.lithium$hasAnyComparatorNearby())) {
                        listenToExtractTracker = true;
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            } else if (this.extractionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY) {
                BlockState hopperState = this.getBlockState();
                listenToExtractEntities = true;

                BlockPos blockPos = this.getBlockPos().above();
                BlockState blockState = this.getLevel().getBlockState(blockPos);
                if (!blockState.isCollisionShapeFullBlock(this.getLevel(), blockPos) || blockState.is(BlockTags.DOES_NOT_BLOCK_HOPPERS)) {
                    listenToItemEntities = true;
                }
            } else {
                return;
            }
        }
        if (this.insertionMode != HopperCachingState.BlockInventory.BLOCK_STATE && 0 < thisStackList.getOccupiedSlots()) {
            if (this.insertionMode == HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
                Container blockInventory = this.insertBlockInventory;
                if (this.insertStackList != null && blockInventory instanceof InventoryChangeTracker) {
                    listenToInsertTracker = true;
                } else {
                    return;
                }
            } else if (this.insertionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY) {
                BlockState hopperState = this.getBlockState();
                listenToInsertEntities = true;
            } else {
                return;
            }
        }

        if (listenToExtractTracker) {
            ((InventoryChangeTracker) this.extractBlockInventory).listenForContentChangesOnce(this.extractStackList, this);
        }
        if (listenToInsertTracker) {
            ((InventoryChangeTracker) this.insertBlockInventory).listenForContentChangesOnce(this.insertStackList, this);
        }
        if (listenToInsertEntities) {
            if (this.insertInventoryEntityTracker == null || this.insertInventoryEntityTracker.isEmpty()) {
                return;
            }
            ChunkSectionEntityMovementTracker.listenToEntityMovementOnce(this, insertInventoryEntityTracker);
        }
        if (listenToExtractEntities) {
            if (this.extractInventoryEntityTracker == null || this.extractInventoryEntityTracker.isEmpty()) {
                return;
            }
            ChunkSectionEntityMovementTracker.listenToEntityMovementOnce(this, extractInventoryEntityTracker);
        }
        if (listenToItemEntities) {
            if (this.collectItemEntityTracker == null || this.collectItemEntityTracker.isEmpty()) {
                return;
            }
            ChunkSectionEntityMovementTracker.listenToEntityMovementOnce(this, collectItemEntityTracker);
        }

        this.listenForContentChangesOnce(thisStackList, this);
        lithium$startSleeping();
    }

    @Override
    public void lithium$setSleepingTicker(@Nullable TickingBlockEntity sleepingTicker) {
        this.sleepingTicker = sleepingTicker;
    }

    @Override
    public @Nullable TickingBlockEntity lithium$getSleepingTicker() {
        return sleepingTicker;
    }

    @Override
    public void lithium$setTickWrapper(LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper) {
        this.tickWrapper = tickWrapper;
        this.lithium$setSleepingTicker(null);
    }

    @Override
    public LevelChunk.@Nullable RebindableTickingBlockEntityWrapper lithium$getTickWrapper() {
        return tickWrapper;
    }

    @Override
    public boolean lithium$startSleeping() {
        if (this.isSleeping()) {
            return false;
        }

        LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = this.lithium$getTickWrapper();
        if (tickWrapper != null) {
            this.lithium$setSleepingTicker(tickWrapper.ticker);
            tickWrapper.rebind(SleepingBlockEntity.SLEEPING_BLOCK_ENTITY_TICKER);

            // Set the last tick time to max value, so other hoppers transferring into this hopper will set it to 7gt
            // cooldown. Then when waking up, we make sure to not tick this hopper in the same gametick.
            // This makes the observable hopper cooldown not be different from vanilla.
            this.tickedGameTime = Long.MAX_VALUE;
            return true;
        }
        return false;
    }

    @Override
    public void handleEntityMovement() {
        this.wakeUpNow();
    }

    @Override
    public NonNullList<ItemStack> getInventoryLithium() {
        return items;
    }

    @Override
    public void setInventoryLithium(NonNullList<ItemStack> inventory) {
        this.items = inventory;
    }

    @Override
    public void lithium$handleInventoryContentModified(Container inventory) {
        wakeUpNow();
    }

    @Override
    public void lithium$handleInventoryRemoved(Container inventory) {
        wakeUpNow();
        if (inventory == this.insertBlockInventory) {
            this.invalidateBlockInsertionData();
        }
        if (inventory == this.extractBlockInventory) {
            this.invalidateBlockExtractionData();
        }
        if (inventory == this) {
            this.invalidateCachedData();
        }
    }

    @Override
    public boolean lithium$handleComparatorAdded(Container inventory) {
        if (inventory == this.extractBlockInventory) {
            wakeUpNow();
            return true;
        }
        return false;
    }

    @Override
    public void lithium$invalidateCacheOnNeighborUpdate(boolean fromAbove) {
        //Clear the block inventory cache (composter inventories and no inventory present) on block update / observer update
        if (fromAbove) {
            if (this.extractionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY || this.extractionMode == HopperCachingState.BlockInventory.BLOCK_STATE) {
                this.invalidateBlockExtractionData();
            }
        } else {
            if (this.insertionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY || this.insertionMode == HopperCachingState.BlockInventory.BLOCK_STATE) {
                this.invalidateBlockInsertionData();
            }
        }
    }

    @Override
    public void lithium$invalidateCacheOnUndirectedNeighborUpdate() {
        if (this.extractionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY || this.extractionMode == HopperCachingState.BlockInventory.BLOCK_STATE) {
            this.invalidateBlockExtractionData();
        }
        if (this.insertionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY || this.insertionMode == HopperCachingState.BlockInventory.BLOCK_STATE) {
            this.invalidateBlockInsertionData();
        }
    }

    @Override
    public void lithium$invalidateCacheOnNeighborUpdate(Direction fromDirection) {
        boolean fromAbove = fromDirection == Direction.UP;
        if (fromAbove || this.getBlockState().getValue(HopperBlock.FACING) == fromDirection) {
            this.lithium$invalidateCacheOnNeighborUpdate(fromAbove);
        }
    }

    private void invalidateBlockInsertionData() {
        this.insertionMode = HopperCachingState.BlockInventory.UNKNOWN;
        this.insertBlockInventory = null;
        this.insertInventory = null;
        this.insertStackList = null;
        this.insertStackListModCount = 0;

        wakeUpNow();
    }

    private void invalidateCachedData() {
        this.shouldCheckSleep = false;
        this.invalidateInsertionData();
        this.invalidateExtractionData();
    }

    private void invalidateInsertionData() {
        if (this.level instanceof ServerLevel) {
            if (this.insertInventoryEntityTracker != null) {
                ChunkSectionEntityMovementTracker.unregister(this.insertInventoryEntityTracker);
                this.insertInventoryEntityTracker = null;
                this.insertInventoryEntityBox = null;
                this.insertInventoryEntityFailedSearchTime = 0L;
            }
        }

        if (this.insertionMode == HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
            assert this.insertBlockInventory != null;
            ((InventoryChangeTracker) this.insertBlockInventory).stopListenForMajorInventoryChanges(this);
        }
        this.invalidateBlockInsertionData();
    }

    private void invalidateExtractionData() {
        if (this.level instanceof ServerLevel) {
            if (this.extractInventoryEntityTracker != null) {
                ChunkSectionEntityMovementTracker.unregister(this.extractInventoryEntityTracker);
                this.extractInventoryEntityTracker = null;
                this.extractInventoryEntityBox = null;
                this.extractInventoryEntityFailedSearchTime = 0L;
            }
            if (this.collectItemEntityTracker != null) {
                ChunkSectionEntityMovementTracker.unregister(this.collectItemEntityTracker);
                this.collectItemEntityTracker = null;
                this.collectItemEntityBox = null;
                this.collectItemEntityTrackerWasEmpty = false;
            }
        }
        if (this.extractionMode == HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
            assert this.extractBlockInventory != null;
            ((InventoryChangeTracker) this.extractBlockInventory).stopListenForMajorInventoryChanges(this);
        }
        this.invalidateBlockExtractionData();
    }

    private void invalidateBlockExtractionData() {
        this.extractionMode = HopperCachingState.BlockInventory.UNKNOWN;
        this.extractBlockInventory = null;
        this.extractInventory = null;
        this.extractStackList = null;
        this.extractStackListModCount = 0;

        this.wakeUpNow();
    }

    private static @Nullable Container getExtractInventory(Level world, Hopper hopper, BlockPos extractBlockPos, BlockState extractBlockState) {
        if (!(hopper instanceof HopperBlockEntity hopperBlockEntity)) {
            return getSourceContainer(world, hopper, extractBlockPos, extractBlockState); //Hopper Minecarts do not cache Inventories
        }

        Container blockInventory = hopperBlockEntity.lithium$getExtractBlockInventory(world, extractBlockPos, extractBlockState);
        if (blockInventory == null) {
            blockInventory = hopperBlockEntity.lithium$getExtractEntityInventory(world);
        }
        return org.bukkit.event.inventory.HopperInventorySearchEvent.getHandlerList().getRegisteredListeners().length == 0 ? blockInventory : runHopperInventorySearchEvent(
                blockInventory,
                org.bukkit.craftbukkit.block.CraftBlock.at(world, hopperBlockEntity.getBlockPos()),
                org.bukkit.craftbukkit.block.CraftBlock.at(world, extractBlockPos),
                org.bukkit.event.inventory.HopperInventorySearchEvent.ContainerType.SOURCE
        );
    }

    public @Nullable Container lithium$getExtractBlockInventory(Level world, BlockPos extractBlockPos, BlockState extractBlockState) {
        Container blockInventory = this.extractBlockInventory;
        if (this.extractionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY) {
            return null;
        } else if (this.extractionMode == HopperCachingState.BlockInventory.BLOCK_STATE) {
            return blockInventory;
        } else if (this.extractionMode == HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
            return blockInventory;
        } else if (this.extractionMode == HopperCachingState.BlockInventory.BLOCK_ENTITY) {
            BlockEntity blockEntity = (BlockEntity) Objects.requireNonNull(blockInventory);
            //Movable Block Entity compatibility - position comparison
            BlockPos pos = blockEntity.getBlockPos();
            if (!(blockEntity).isRemoved() && pos.equals(extractBlockPos)) {
                LithiumInventory optimizedInventory;
                if ((optimizedInventory = this.extractInventory) != null) {
                    LithiumStackList insertInventoryStackList = InventoryHelper.getLithiumStackList(optimizedInventory);
                    //This check is necessary as sometimes the stacklist is silently replaced (e.g. command making furnace read inventory from nbt)
                    if (insertInventoryStackList == this.extractStackList) {
                        return optimizedInventory;
                    } else {
                        this.invalidateBlockExtractionData();
                    }
                } else {
                    return blockInventory;
                }
            }
        }

        //No Cached Inventory: Get like vanilla and cache
        blockInventory = getBlockContainer(world, extractBlockPos, extractBlockState);
        blockInventory = HopperHelper.replaceDoubleInventory(blockInventory);
        this.cacheExtractBlockInventory(blockInventory);
        return blockInventory;
    }

    public @Nullable Container lithium$getInsertBlockInventory(Level world) {
        Container blockInventory = this.insertBlockInventory;
        if (this.insertionMode == HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY) {
            return null;
        } else if (this.insertionMode == HopperCachingState.BlockInventory.BLOCK_STATE) {
            return blockInventory;
        } else if (this.insertionMode == HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY) {
            return blockInventory;
        } else if (this.insertionMode == HopperCachingState.BlockInventory.BLOCK_ENTITY) {
            BlockEntity blockEntity = (BlockEntity) Objects.requireNonNull(blockInventory);
            //Movable Block Entity compatibility - position comparison
            BlockPos pos = blockEntity.getBlockPos();
            Direction direction = this.facing;
            BlockPos transferPos = this.getBlockPos().relative(direction);
            if (!(blockEntity).isRemoved() &&
                    pos.equals(transferPos)) {
                LithiumInventory optimizedInventory;
                if ((optimizedInventory = this.insertInventory) != null) {
                    LithiumStackList insertInventoryStackList = InventoryHelper.getLithiumStackList(optimizedInventory);
                    //This check is necessary as sometimes the stacklist is silently replaced (e.g. command making furnace read inventory from nbt)
                    if (insertInventoryStackList == this.insertStackList) {
                        return optimizedInventory;
                    } else {
                        this.invalidateBlockInsertionData();
                    }
                } else {
                    return blockInventory;
                }
            }
        }

        //No Cached Inventory: Get like vanilla and cache
        Direction direction = this.facing;
        BlockPos insertBlockPos = this.getBlockPos().relative(direction);
        BlockState blockState = world.getBlockState(insertBlockPos);
        blockInventory = getBlockContainer(world, insertBlockPos, blockState);
        blockInventory = HopperHelper.replaceDoubleInventory(blockInventory);
        this.cacheInsertBlockInventory(blockInventory);
        return blockInventory;
    }

    public @Nullable Container getInsertInventory(Level world) {
        Container blockInventory = getInsertInventory0(world);
        return org.bukkit.event.inventory.HopperInventorySearchEvent.getHandlerList().getRegisteredListeners().length == 0 ? blockInventory : runHopperInventorySearchEvent(
                blockInventory,
                org.bukkit.craftbukkit.block.CraftBlock.at(world, this.getBlockPos()),
                org.bukkit.craftbukkit.block.CraftBlock.at(world, this.getBlockPos().relative(this.facing)),
                org.bukkit.event.inventory.HopperInventorySearchEvent.ContainerType.DESTINATION
        );
    }

    public @Nullable Container getInsertInventory0(Level world) {
        Container blockInventory = this.lithium$getInsertBlockInventory(world);
        if (blockInventory != null) {
            return blockInventory;
        }

        if (this.insertInventoryEntityTracker == null) {
            this.initInsertInventoryTracker(world);
        }
        if (ChunkSectionEntityMovementTracker.isUnchangedSince(this.insertInventoryEntityFailedSearchTime, this.insertInventoryEntityTracker)) {
            this.insertInventoryEntityFailedSearchTime = this.tickedGameTime;
            return null;
        }
        this.insertInventoryEntityFailedSearchTime = Long.MIN_VALUE;
        this.shouldCheckSleep = false;

        List<Container> inventoryEntities = ChunkSectionInventoryEntityTracker.getEntities(world, this.insertInventoryEntityBox);
        if (inventoryEntities.isEmpty()) {
            this.insertInventoryEntityFailedSearchTime = this.tickedGameTime;
            //Remember failed entity search timestamp. This allows shortcutting if no entity movement happens.
            return null;
        }
        Container inventory = inventoryEntities.get(world.random.nextInt(inventoryEntities.size()));
        if (inventory instanceof LithiumInventory optimizedInventory) {
            LithiumStackList insertInventoryStackList = InventoryHelper.getLithiumStackList(optimizedInventory);
            if (inventory != this.insertInventory || this.insertStackList != insertInventoryStackList) {
                this.cacheInsertLithiumInventory(optimizedInventory);
            }
        }

        return inventory;
    }

    private void initCollectItemEntityTracker() {
        assert this.level instanceof ServerLevel;
        AABB inputBox = this.getSuckAabb().move(this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
        this.collectItemEntityBox = inputBox;
        this.collectItemEntityTracker =
                ChunkSectionItemEntityMovementTracker.registerAt(
                        (ServerLevel) this.level,
                        inputBox
                );
        this.collectItemEntityAttemptTime = Long.MIN_VALUE;
    }

    private void initExtractInventoryTracker(Level world) {
        assert world instanceof ServerLevel;
        BlockPos pos = this.worldPosition.relative(Direction.UP);
        this.extractInventoryEntityBox = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        this.extractInventoryEntityTracker =
                ChunkSectionInventoryEntityTracker.registerAt(
                        (ServerLevel) this.level,
                        this.extractInventoryEntityBox
                );
        this.extractInventoryEntityFailedSearchTime = Long.MIN_VALUE;
    }

    private void initInsertInventoryTracker(Level world) {
        assert world instanceof ServerLevel;
        Direction direction = this.facing;
        BlockPos pos = this.worldPosition.relative(direction);
        this.insertInventoryEntityBox = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        this.insertInventoryEntityTracker =
                ChunkSectionInventoryEntityTracker.registerAt(
                        (ServerLevel) this.level,
                        this.insertInventoryEntityBox
                );
        this.insertInventoryEntityFailedSearchTime = Long.MIN_VALUE;
    }

    private @Nullable Container lithium$getExtractEntityInventory(Level world) {
        if (this.extractInventoryEntityTracker == null) {
            this.initExtractInventoryTracker(world);
        }
        if (ChunkSectionEntityMovementTracker.isUnchangedSince(this.extractInventoryEntityFailedSearchTime, this.extractInventoryEntityTracker)) {
            this.extractInventoryEntityFailedSearchTime = this.tickedGameTime;
            return null;
        }
        this.extractInventoryEntityFailedSearchTime = Long.MIN_VALUE;
        this.shouldCheckSleep = false;

        List<Container> inventoryEntities = ChunkSectionInventoryEntityTracker.getEntities(world, this.extractInventoryEntityBox);
        if (inventoryEntities.isEmpty()) {
            this.extractInventoryEntityFailedSearchTime = this.tickedGameTime;
            //only set unchanged when no entity present. this allows shortcutting this case
            //shortcutting the entity present case requires checking its change counter
            return null;
        }
        Container inventory = inventoryEntities.get(world.random.nextInt(inventoryEntities.size()));
        if (inventory instanceof LithiumInventory optimizedInventory) {
            LithiumStackList extractInventoryStackList = InventoryHelper.getLithiumStackList(optimizedInventory);
            if (inventory != this.extractInventory || this.extractStackList != extractInventoryStackList) {
                //not caching the inventory (NO_BLOCK_INVENTORY prevents it)
                //make change counting on the entity inventory possible, without caching it as block inventory
                this.cacheExtractLithiumInventory(optimizedInventory);
            }
        }
        return inventory;
    }

    /**
     * Makes this hopper remember the given inventory.
     *
     * @param insertInventory Block inventory / Blockentity inventory to be remembered
     */
    private void cacheInsertBlockInventory(@Nullable Container insertInventory) {
        assert !(insertInventory instanceof Entity);
        if (insertInventory instanceof LithiumInventory optimizedInventory) {
            this.cacheInsertLithiumInventory(optimizedInventory);
        } else {
            this.insertInventory = null;
            this.insertStackList = null;
            this.insertStackListModCount = 0;
        }

        if (insertInventory instanceof BlockEntity || insertInventory instanceof CompoundContainer) {
            this.insertBlockInventory = insertInventory;
            if (insertInventory instanceof InventoryChangeTracker) {
                this.insertionMode = HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY;
                ((InventoryChangeTracker) insertInventory).listenForMajorInventoryChanges(this);
            } else {
                this.insertionMode = HopperCachingState.BlockInventory.BLOCK_ENTITY;
            }
        } else {
            if (insertInventory == null) {
                this.insertBlockInventory = null;
                this.insertionMode = HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY;
            } else {
                this.insertBlockInventory = insertInventory;
                this.insertionMode = insertInventory instanceof BlockStateOnlyInventory ? HopperCachingState.BlockInventory.BLOCK_STATE : HopperCachingState.BlockInventory.UNKNOWN;
            }
        }
    }

    private void cacheInsertLithiumInventory(LithiumInventory optimizedInventory) {
        LithiumStackList insertInventoryStackList = InventoryHelper.getLithiumStackList(optimizedInventory);
        this.insertInventory = optimizedInventory;
        this.insertStackList = insertInventoryStackList;
        this.insertStackListModCount = insertInventoryStackList.getModCount() - 1;
    }

    private void cacheExtractLithiumInventory(LithiumInventory optimizedInventory) {
        LithiumStackList extractInventoryStackList = InventoryHelper.getLithiumStackList(optimizedInventory);
        this.extractInventory = optimizedInventory;
        this.extractStackList = extractInventoryStackList;
        this.extractStackListModCount = extractInventoryStackList.getModCount() - 1;
    }

    /**
     * Makes this hopper remember the given inventory.
     *
     * @param extractInventory Block inventory / Blockentity inventory to be remembered
     */
    private void cacheExtractBlockInventory(@Nullable Container extractInventory) {
        assert !(extractInventory instanceof Entity);
        if (extractInventory instanceof LithiumInventory optimizedInventory) {
            this.cacheExtractLithiumInventory(optimizedInventory);
        } else {
            this.extractInventory = null;
            this.extractStackList = null;
            this.extractStackListModCount = 0;
        }

        if (extractInventory instanceof BlockEntity || extractInventory instanceof CompoundContainer) {
            this.extractBlockInventory = extractInventory;
            if (extractInventory instanceof InventoryChangeTracker) {
                this.extractionMode = HopperCachingState.BlockInventory.REMOVAL_TRACKING_BLOCK_ENTITY;
                ((InventoryChangeTracker) extractInventory).listenForMajorInventoryChanges(this);
            } else {
                this.extractionMode = HopperCachingState.BlockInventory.BLOCK_ENTITY;
            }
        } else {
            if (extractInventory == null) {
                this.extractBlockInventory = null;
                this.extractionMode = HopperCachingState.BlockInventory.NO_BLOCK_INVENTORY;
            } else {
                this.extractBlockInventory = extractInventory;
                this.extractionMode = extractInventory instanceof BlockStateOnlyInventory ? HopperCachingState.BlockInventory.BLOCK_STATE : HopperCachingState.BlockInventory.UNKNOWN;
            }
        }
    }

    private static List<ItemEntity> lithiumGetInputItemEntities(Level world, Hopper hopper) {
        if (!(hopper instanceof HopperBlockEntity hopperBlockEntity)) {
            return getItemsAtAndAbove(world, hopper); //optimizations not implemented for hopper minecarts
        }

        if (hopperBlockEntity.collectItemEntityTracker == null) {
            hopperBlockEntity.initCollectItemEntityTracker();
        }

        long modCount = InventoryHelper.getLithiumStackList(hopperBlockEntity).getModCount();

        if ((hopperBlockEntity.collectItemEntityTrackerWasEmpty || hopperBlockEntity.myModCountAtLastItemCollect == modCount) &&
                ChunkSectionEntityMovementTracker.isUnchangedSince(hopperBlockEntity.collectItemEntityAttemptTime, hopperBlockEntity.collectItemEntityTracker)) {
            hopperBlockEntity.collectItemEntityAttemptTime = hopperBlockEntity.tickedGameTime;
            return java.util.Collections.emptyList();
        }

        hopperBlockEntity.myModCountAtLastItemCollect = modCount;
        hopperBlockEntity.shouldCheckSleep = false;

        List<ItemEntity> itemEntities = ChunkSectionItemEntityMovementTracker.getEntities(world, hopperBlockEntity.collectItemEntityBox);
        hopperBlockEntity.collectItemEntityAttemptTime = hopperBlockEntity.tickedGameTime;
        hopperBlockEntity.collectItemEntityTrackerWasEmpty = itemEntities.isEmpty();
        //set unchanged so that if this extract fails and there is no other change to hoppers or items, extracting
        // items can be skipped.
        return itemEntities;
    }

    private static @Nullable Boolean lithiumInsert(Level world, BlockPos pos, HopperBlockEntity hopperBlockEntity, @Nullable Container insertInventory) {
        if (insertInventory == null || hopperBlockEntity instanceof net.minecraft.world.WorldlyContainer) {
            //call the vanilla code to allow other mods inject features
            //e.g. carpet mod allows hoppers to insert items into wool blocks
            return null;
        }

        LithiumStackList hopperStackList = InventoryHelper.getLithiumStackList(hopperBlockEntity);
        if (hopperBlockEntity.insertInventory == insertInventory && hopperStackList.getModCount() == hopperBlockEntity.myModCountAtLastInsert) {
            if (hopperBlockEntity.insertStackList != null && hopperBlockEntity.insertStackList.getModCount() == hopperBlockEntity.insertStackListModCount) {
//                ComparatorUpdatePattern.NO_UPDATE.apply(hopperBlockEntity, hopperStackList); //commented because it's a noop, Hoppers do not send useless comparator updates
                return false;
            }
        }

        boolean insertInventoryWasEmptyHopperNotDisabled = insertInventory instanceof HopperBlockEntity hopperInv &&
                !hopperInv.isOnCustomCooldown() && hopperBlockEntity.insertStackList != null &&
                hopperBlockEntity.insertStackList.getOccupiedSlots() == 0;

        boolean insertInventoryHandlesModdedCooldown =
                insertInventory.canReceiveTransferCooldown() &&
                        hopperBlockEntity.insertStackList != null ?
                        hopperBlockEntity.insertStackList.getOccupiedSlots() == 0 :
                        insertInventory.isEmpty();

        var currentWorldData = world.getCurrentWorldData(); // Luminol - Region threading for lithium sleeping block entity
        currentWorldData.skipPushModeEventFire = currentWorldData.skipHopperEvents; // Luminol
        //noinspection ConstantConditions
        if (!(hopperBlockEntity.insertInventory == insertInventory && hopperBlockEntity.insertStackList.getFullSlots() == hopperBlockEntity.insertStackList.size())) {
            Direction fromDirection = hopperBlockEntity.facing.getOpposite();
            int size = hopperStackList.size();
            for (int i = 0; i < size; ++i) {
                ItemStack transferStack = hopperStackList.get(i);
                if (!transferStack.isEmpty()) {
                    if (!currentWorldData.skipPushModeEventFire && canTakeItemFromContainer(insertInventory, hopperBlockEntity, transferStack, i, Direction.DOWN)) { // Luminol
                        transferStack = callPushMoveEvent(insertInventory, transferStack, hopperBlockEntity);
                        if (transferStack == null) { // cancelled
                            break;
                        }
                    }
                    boolean transferSuccess = HopperHelper.tryMoveSingleItem(insertInventory, transferStack, fromDirection);
                    if (transferSuccess) {
                        if (insertInventoryWasEmptyHopperNotDisabled) {
                            HopperBlockEntity receivingHopper = (HopperBlockEntity) insertInventory;
                            int k = 8;
                            if (receivingHopper.tickedGameTime >= hopperBlockEntity.tickedGameTime) {
                                k = 7;
                            }
                            receivingHopper.setCooldown(k);
                        }
                        if (insertInventoryHandlesModdedCooldown) {
                            insertInventory.setTransferCooldown(hopperBlockEntity.tickedGameTime);
                        }
                        insertInventory.setChanged();
                        return true;
                    }
                }
            }
        }
        hopperBlockEntity.myModCountAtLastInsert = hopperStackList.getModCount();
        if (hopperBlockEntity.insertStackList != null) {
            hopperBlockEntity.insertStackListModCount = hopperBlockEntity.insertStackList.getModCount();
        }
        return false;
    }

    private static @Nullable Boolean lithiumExtract(Level world, Hopper to, Container from) {
        if (!(to instanceof HopperBlockEntity hopperBlockEntity)) {
            return null; //optimizations not implemented for hopper minecarts
        }

        if (from != hopperBlockEntity.extractInventory || hopperBlockEntity.extractStackList == null) {
            return null; //from inventory is not an optimized inventory, vanilla fallback
        }

        LithiumStackList hopperStackList = InventoryHelper.getLithiumStackList(hopperBlockEntity);
        LithiumStackList fromStackList = hopperBlockEntity.extractStackList;

        if (hopperStackList.getModCount() == hopperBlockEntity.myModCountAtLastExtract) {
            if (fromStackList.getModCount() == hopperBlockEntity.extractStackListModCount) {
                if (!(from instanceof ComparatorTracker comparatorTracker) || comparatorTracker.lithium$hasAnyComparatorNearby()) {
                    //noinspection CollectionAddedToSelf
                    fromStackList.runComparatorUpdatePatternOnFailedExtract(fromStackList, from);
                }
                return false;
            }
        }

        var currentWorldData = world.getCurrentWorldData(); // Luminol - Region threading for lithium sleeping block entity
        int[] availableSlots = from instanceof WorldlyContainer ? ((WorldlyContainer) from).getSlotsForFace(Direction.DOWN) : null;
        int fromSize = availableSlots != null ? availableSlots.length : from.getContainerSize();
        for (int i = 0; i < fromSize; i++) {
            int fromSlot = availableSlots != null ? availableSlots[i] : i;
            ItemStack itemStack = fromStackList.get(fromSlot);
            if (!itemStack.isEmpty() && canTakeItemFromContainer(to, from, itemStack, fromSlot, Direction.DOWN)) {
                if (!currentWorldData.skipPullModeEventFire) { // Luminol
                    itemStack = callPullMoveEvent(to, from, itemStack);
                    if (itemStack == null) { // cancelled
                        return true;
                    }
                }
                //calling removeStack is necessary due to its side effects (markDirty in LootableContainerBlockEntity)
                ItemStack takenItem = from.removeItem(fromSlot, 1);
                assert !takenItem.isEmpty();
                boolean transferSuccess = HopperHelper.tryMoveSingleItem(to, takenItem, null);
                if (transferSuccess) {
                    to.setChanged();
                    from.setChanged();
                    return true;
                }
                //put the item back similar to vanilla
                ItemStack restoredStack = fromStackList.get(fromSlot);
                if (restoredStack.isEmpty()) {
                    restoredStack = takenItem;
                } else {
                    restoredStack.grow(1);
                }
                //calling setStack is necessary due to its side effects (markDirty in LootableContainerBlockEntity)
                from.setItem(fromSlot, restoredStack);
            }
        }
        hopperBlockEntity.myModCountAtLastExtract = hopperStackList.getModCount();
        if (fromStackList != null) {
            hopperBlockEntity.extractStackListModCount = fromStackList.getModCount();
        }
        return false;
    }
    // Leaves end - Lithium Sleeping Block Entity

    // Leaves start - pca
    @Override
    public void setChanged() {
        super.setChanged();
        if (fun.bm.mili.config.modules.function.protocol.PcaSyncProtocolConfig.enable) {
            org.leavesmc.leaves.protocol.PcaSyncProtocol.syncBlockEntityToClient(this);
        }
    }
    // Leaves end - pca
}
