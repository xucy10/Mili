package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class DispenserBlockEntity extends RandomizableContainerBlockEntity implements org.leavesmc.leaves.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker, org.leavesmc.leaves.lithium.api.inventory.LithiumInventory { // Leaves - Lithium Sleeping Block Entity
    public static final int CONTAINER_SIZE = 9;
    private static final Component DEFAULT_NAME = Component.translatable("container.dispenser");
    private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);

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
    // CraftBukkit end

    protected DispenserBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public DispenserBlockEntity(BlockPos pos, BlockState blockState) {
        this(BlockEntityType.DISPENSER, pos, blockState);
    }

    @Override
    public int getContainerSize() {
        return 9;
    }

    public int getRandomSlot(RandomSource random) {
        this.unpackLootTable(null);
        int i = -1;
        int i1 = 1;

        for (int i2 = 0; i2 < this.items.size(); i2++) {
            if (!this.items.get(i2).isEmpty() && random.nextInt(i1++) == 0) {
                i = i2;
            }
        }

        return i;
    }

    public ItemStack insertItem(ItemStack stack) {
        int maxStackSize = this.getMaxStackSize(stack);

        for (int i = 0; i < this.items.size(); i++) {
            ItemStack itemStack = this.items.get(i);
            if (itemStack.isEmpty() || ItemStack.isSameItemSameComponents(stack, itemStack)) {
                int min = Math.min(stack.getCount(), maxStackSize - itemStack.getCount());
                if (min > 0) {
                    if (itemStack.isEmpty()) {
                        this.setItem(i, stack.split(min));
                    } else {
                        stack.shrink(min);
                        itemStack.grow(min);
                    }
                }

                if (stack.isEmpty()) {
                    break;
                }
            }
        }

        return stack;
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, this.items);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, this.items);
        }
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

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return new DispenserMenu(id, player, this);
    }

    // Leaves start - Lithium Sleeping Block Entity
    @Override
    public net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> getInventoryLithium() {
        return items;
    }

    @Override
    public void setInventoryLithium(net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> inventory) {
        items = inventory;
    }
    // Leaves end - Lithium Sleeping Block Entity
}
