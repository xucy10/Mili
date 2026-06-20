package net.minecraft.world;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class SimpleContainer implements Container, StackedContentsCompatible {
    private final int size;
    public final NonNullList<ItemStack> items;
    private @Nullable List<ContainerListener> listeners;

    // Paper start - add fields and methods
    public List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;
    protected org.bukkit.inventory.@Nullable InventoryHolder bukkitOwner; // Paper - annotation

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
    public List<org.bukkit.entity.HumanEntity> getViewers() {
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
    public org.bukkit.inventory.@Nullable InventoryHolder getOwner() {
        // Paper start - Add missing InventoryHolders
        if (this.bukkitOwner == null && this.bukkitOwnerCreator != null) {
            this.bukkitOwner = this.bukkitOwnerCreator.get();
        }
        // Paper end - Add missing InventoryHolders
        return this.bukkitOwner;
    }

    @Override
    public org.bukkit.@Nullable Location getLocation() {
        // Paper start - Fix inventories returning null Locations
        // When the block inventory does not have a tile state that implements getLocation, e. g. composters
        if (this.bukkitOwner instanceof org.bukkit.inventory.BlockInventoryHolder blockInventoryHolder) {
            return blockInventoryHolder.getBlock().getLocation();
        }
        // When the bukkit owner is a bukkit entity, but does not implement Container itself, e. g. horses
        if (this.bukkitOwner instanceof org.bukkit.entity.Entity entity) {
            return entity.getLocation();
        }
        // Paper end - Fix inventories returning null Locations
        return null;
    }

    public SimpleContainer(SimpleContainer original) {
        this(original.size);
        for (int slot = 0; slot < original.size; slot++) {
            this.items.set(slot, original.items.get(slot).copy());
        }
    }
    // Paper end

    public SimpleContainer(int size) {
        this(size, null);
    }

    // Paper start - Add missing InventoryHolders
    private java.util.function.@Nullable Supplier<? extends org.bukkit.inventory.InventoryHolder> bukkitOwnerCreator;

    public SimpleContainer(java.util.function.Supplier<? extends org.bukkit.inventory.InventoryHolder> bukkitOwnerCreator, int size) {
        this(size);
        this.bukkitOwnerCreator = bukkitOwnerCreator;
    }
    // Paper end - Add missing InventoryHolders

    public SimpleContainer(int size, org.bukkit.inventory.@Nullable InventoryHolder owner) {
        this.bukkitOwner = owner;
        // Paper end
        this.size = size;
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    public SimpleContainer(ItemStack... items) {
        this.size = items.length;
        this.items = NonNullList.of(ItemStack.EMPTY, items);
    }

    public void addListener(ContainerListener listener) {
        if (this.listeners == null) {
            this.listeners = Lists.newArrayList();
        }

        this.listeners.add(listener);
    }

    public void removeListener(ContainerListener listener) {
        if (this.listeners != null) {
            this.listeners.remove(listener);
        }
    }

    @Override
    public ItemStack getItem(int index) {
        return index >= 0 && index < this.items.size() ? this.items.get(index) : ItemStack.EMPTY;
    }

    public List<ItemStack> removeAllItems() {
        List<ItemStack> list = this.items.stream().filter(stack -> !stack.isEmpty()).collect(Collectors.toList());
        this.clearContent();
        return list;
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack itemStack = ContainerHelper.removeItem(this.items, index, count);
        if (!itemStack.isEmpty()) {
            this.setChanged();
        }

        return itemStack;
    }

    public ItemStack removeItemType(Item item, int amount) {
        ItemStack itemStack = new ItemStack(item, 0);

        for (int i = this.size - 1; i >= 0; i--) {
            ItemStack item1 = this.getItem(i);
            if (item1.getItem().equals(item)) {
                int i1 = amount - itemStack.getCount();
                ItemStack itemStack1 = item1.split(i1);
                itemStack.grow(itemStack1.getCount());
                if (itemStack.getCount() == amount) {
                    break;
                }
            }
        }

        if (!itemStack.isEmpty()) {
            this.setChanged();
        }

        return itemStack;
    }

    public ItemStack addItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemStack = stack.copy();
            this.moveItemToOccupiedSlotsWithSameType(itemStack);
            if (itemStack.isEmpty()) {
                return ItemStack.EMPTY;
            } else {
                this.moveItemToEmptySlots(itemStack);
                return itemStack.isEmpty() ? ItemStack.EMPTY : itemStack;
            }
        }
    }

    public boolean canAddItem(ItemStack stack) {
        boolean flag = false;

        for (ItemStack itemStack : this.items) {
            if (itemStack.isEmpty() || ItemStack.isSameItemSameComponents(itemStack, stack) && itemStack.getCount() < itemStack.getMaxStackSize()) {
                flag = true;
                break;
            }
        }

        return flag;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack itemStack = this.items.get(index);
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            this.items.set(index, ItemStack.EMPTY);
            return itemStack;
        }
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.items.set(index, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        this.setChanged();
    }

    @Override
    public int getContainerSize() {
        return this.size;
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
    public void setChanged() {
        if (this.listeners != null) {
            for (ContainerListener containerListener : this.listeners) {
                containerListener.containerChanged(this);
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.setChanged();
    }

    @Override
    public void fillStackedContents(StackedItemContents stackedContents) {
        for (ItemStack itemStack : this.items) {
            stackedContents.accountStack(itemStack);
        }
    }

    @Override
    public String toString() {
        return this.items.stream().filter(stack -> !stack.isEmpty()).collect(Collectors.toList()).toString();
    }

    private void moveItemToEmptySlots(ItemStack stack) {
        for (int i = 0; i < this.size; i++) {
            ItemStack item = this.getItem(i);
            if (item.isEmpty()) {
                this.setItem(i, stack.copyAndClear());
                return;
            }
        }
    }

    private void moveItemToOccupiedSlotsWithSameType(ItemStack stack) {
        for (int i = 0; i < this.size; i++) {
            ItemStack item = this.getItem(i);
            if (ItemStack.isSameItemSameComponents(item, stack)) {
                this.moveItemsBetweenStacks(stack, item);
                if (stack.isEmpty()) {
                    return;
                }
            }
        }
    }

    private void moveItemsBetweenStacks(ItemStack stack, ItemStack other) {
        int maxStackSize = this.getMaxStackSize(other);
        int min = Math.min(stack.getCount(), maxStackSize - other.getCount());
        if (min > 0) {
            other.grow(min);
            stack.shrink(min);
            this.setChanged();
        }
    }

    public void fromItemList(ValueInput.TypedInputList<ItemStack> input) {
        this.clearContent();

        for (ItemStack itemStack : input) {
            this.addItem(itemStack);
        }
    }

    public void storeAsItemList(ValueOutput.TypedOutputList<ItemStack> output) {
        for (int i = 0; i < this.getContainerSize(); i++) {
            ItemStack item = this.getItem(i);
            if (!item.isEmpty()) {
                output.add(item);
            }
        }
    }

    public NonNullList<ItemStack> getItems() {
        return this.items;
    }
}
