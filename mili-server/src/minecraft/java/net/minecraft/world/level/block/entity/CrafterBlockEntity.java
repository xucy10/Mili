package net.minecraft.world.level.block.entity;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class CrafterBlockEntity extends RandomizableContainerBlockEntity implements CraftingContainer, org.leavesmc.leaves.lithium.common.block.entity.SleepingBlockEntity, org.leavesmc.leaves.lithium.common.block.entity.SetChangedHandlingBlockEntity {
    public static final int CONTAINER_WIDTH = 3;
    public static final int CONTAINER_HEIGHT = 3;
    public static final int CONTAINER_SIZE = 9;
    public static final int SLOT_DISABLED = 1;
    public static final int SLOT_ENABLED = 0;
    public static final int DATA_TRIGGERED = 9;
    public static final int NUM_DATA = 10;
    private static final int DEFAULT_CRAFTING_TICKS_REMAINING = 0;
    private static final int DEFAULT_TRIGGERED = 0;
    private static final Component DEFAULT_NAME = Component.translatable("container.crafter");
    private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
    public int craftingTicksRemaining = 0;
    protected final ContainerData containerData = new ContainerData() {
        private final int[] slotStates = new int[9];
        private int triggered = 0;

        @Override
        public int get(int index) {
            return index == 9 ? this.triggered : this.slotStates[index];
        }

        @Override
        public void set(int index, int value) {
            if (index == 9) {
                this.triggered = value;
            } else {
                this.slotStates[index] = value;
            }
        }

        @Override
        public int getCount() {
            return 10;
        }
    };

    // CraftBukkit start - add fields and methods
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;

    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getContents() {
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

    @Override
    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }

    @Override
    public @javax.annotation.Nullable org.bukkit.Location getLocation() {
        if (this.level == null) return null;
        return org.bukkit.craftbukkit.util.CraftLocation.toBukkit(this.worldPosition, this.level);
    }
    // CraftBukkit end

    public CrafterBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.CRAFTER, pos, blockState);
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new CrafterMenu(containerId, inventory, this, this.containerData);
    }

    public void setSlotState(int slot, boolean state) {
        if (this.slotCanBeDisabled(slot)) {
            this.containerData.set(slot, state ? 0 : 1);
            this.setChanged();
        }
    }

    public boolean isSlotDisabled(int slot) {
        return slot >= 0 && slot < 9 && this.containerData.get(slot) == 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (this.containerData.get(slot) == 1) {
            return false;
        } else {
            ItemStack itemStack = this.items.get(slot);
            int count = itemStack.getCount();
            return count < itemStack.getMaxStackSize() && (itemStack.isEmpty() || !this.smallerStackExist(count, itemStack, slot));
        }
    }

    private boolean smallerStackExist(int currentSize, ItemStack stack, int slot) {
        for (int i = slot + 1; i < 9; i++) {
            if (!this.isSlotDisabled(i)) {
                ItemStack item = this.getItem(i);
                if (item.isEmpty() || item.getCount() < currentSize && ItemStack.isSameItemSameComponents(item, stack)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.craftingTicksRemaining = input.getIntOr("crafting_ticks_remaining", 0);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, this.items);
        }

        for (int i = 0; i < 9; i++) {
            this.containerData.set(i, 0);
        }

        input.getIntArray("disabled_slots").ifPresent(ints -> {
            for (int i1 : ints) {
                if (this.slotCanBeDisabled(i1)) {
                    this.containerData.set(i1, 1);
                }
            }
        });
        this.containerData.set(9, input.getIntOr("triggered", 0));
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && this.isSleeping() && this.level != null) this.wakeUpNow(); // Leaves - Lithium Sleeping Block Entity
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("crafting_ticks_remaining", this.craftingTicksRemaining);
        if (!this.trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, this.items);
        }

        this.addDisabledSlots(output);
        this.addTriggered(output);
    }

    @Override
    public int getContainerSize() {
        return 9;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemStack : this.items) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return this.items.get(index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (this.isSlotDisabled(index)) {
            this.setSlotState(index, true);
        }

        super.setItem(index, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getWidth() {
        return 3;
    }

    @Override
    public int getHeight() {
        return 3;
    }

    @Override
    public void fillStackedContents(StackedItemContents stackedContents) {
        for (ItemStack itemStack : this.items) {
            stackedContents.accountSimpleStack(itemStack);
        }
    }

    private void addDisabledSlots(ValueOutput output) {
        IntList list = new IntArrayList();

        for (int i = 0; i < 9; i++) {
            if (this.isSlotDisabled(i)) {
                list.add(i);
            }
        }

        output.putIntArray("disabled_slots", list.toIntArray());
    }

    private void addTriggered(ValueOutput output) {
        output.putInt("triggered", this.containerData.get(9));
    }

    public void setTriggered(boolean triggered) {
        this.containerData.set(9, triggered ? 1 : 0);
    }

    @VisibleForTesting
    public boolean isTriggered() {
        return this.containerData.get(9) == 1;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrafterBlockEntity crafter) {
        int i = crafter.craftingTicksRemaining - 1;
        if (i >= 0) {
            crafter.craftingTicksRemaining = i;
            if (i == 0) {
                level.setBlock(pos, state.setValue(CrafterBlock.CRAFTING, false), Block.UPDATE_ALL);
            }
        }
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && i < 0) crafter.checkSleep(); // Leaves - Lithium Sleeping Block Entity
    }

    public void setCraftingTicksRemaining(int craftingTicksRemaining) {
        this.craftingTicksRemaining = craftingTicksRemaining;
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && this.isSleeping() && this.level != null) this.wakeUpNow(); // Leaves - Lithium Sleeping Block Entity
    }

    public int getRedstoneSignal() {
        int i = 0;

        for (int i1 = 0; i1 < this.getContainerSize(); i1++) {
            ItemStack item = this.getItem(i1);
            if (!item.isEmpty() || this.isSlotDisabled(i1)) {
                i++;
            }
        }

        return i;
    }

    private boolean slotCanBeDisabled(int slot) {
        return slot > -1 && slot < 9 && this.items.get(slot).isEmpty();
    }

    // Leaves start - Lithium Sleeping Block Entity
    private net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = null;
    private TickingBlockEntity sleepingTicker = null;

    @Override
    public net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper lithium$getTickWrapper() {
        return this.tickWrapper;
    }

    @Override
    public void lithium$setTickWrapper(net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper) {
        this.tickWrapper = tickWrapper;
        this.lithium$setSleepingTicker(null);
    }

    @Override
    public TickingBlockEntity lithium$getSleepingTicker() {
        return this.sleepingTicker;
    }

    @Override
    public void lithium$setSleepingTicker(TickingBlockEntity sleepingTicker) {
        this.sleepingTicker = sleepingTicker;
    }

    private void checkSleep() {
        if (this.craftingTicksRemaining == 0) {
            this.lithium$startSleeping();
        }
    }

    @Override
    public void lithium$handleSetChanged() {
        if (this.isSleeping() && this.level != null && !this.level.isClientSide()) {
            this.wakeUpNow();
        }
    }
    // Leaves end - Lithium Sleeping Block Entity
}
