package net.minecraft.world;

import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class CompoundContainer implements Container {
    public final Container container1;
    public final Container container2;

    // CraftBukkit start - add fields and methods
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();

    @Override
    public java.util.List<ItemStack> getContents() {
        java.util.List<ItemStack> result = new java.util.ArrayList<>(this.getContainerSize());
        for (int i = 0; i < this.getContainerSize(); i++) {
            result.add(this.getItem(i));
        }
        return result;
    }

    @Override
    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.container1.onOpen(player);
        this.container2.onOpen(player);
        this.transaction.add(player);
    }

    @Override
    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.container1.onClose(player);
        this.container2.onClose(player);
        this.transaction.remove(player);
    }

    @Override
    public java.util.List<org.bukkit.entity.HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public @javax.annotation.Nullable org.bukkit.inventory.InventoryHolder getOwner() {
        return null; // This method won't be called since CraftInventoryDoubleChest doesn't defer to here
    }

    public void setMaxStackSize(int size) {
        this.container1.setMaxStackSize(size);
        this.container2.setMaxStackSize(size);
    }

    @Override
    public org.bukkit.Location getLocation() {
        return this.container1.getLocation(); // TODO: right?
    }
    // CraftBukkit end

    public CompoundContainer(Container container1, Container container2) {
        this.container1 = container1;
        this.container2 = container2;
    }

    @Override
    public int getContainerSize() {
        return this.container1.getContainerSize() + this.container2.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return this.container1.isEmpty() && this.container2.isEmpty();
    }

    public boolean contains(Container inventory) {
        return this.container1 == inventory || this.container2 == inventory;
    }

    @Override
    public ItemStack getItem(int index) {
        return index >= this.container1.getContainerSize()
            ? this.container2.getItem(index - this.container1.getContainerSize())
            : this.container1.getItem(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        return index >= this.container1.getContainerSize()
            ? this.container2.removeItem(index - this.container1.getContainerSize(), count)
            : this.container1.removeItem(index, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return index >= this.container1.getContainerSize()
            ? this.container2.removeItemNoUpdate(index - this.container1.getContainerSize())
            : this.container1.removeItemNoUpdate(index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index >= this.container1.getContainerSize()) {
            this.container2.setItem(index - this.container1.getContainerSize(), stack);
        } else {
            this.container1.setItem(index, stack);
        }
    }

    @Override
    public int getMaxStackSize() {
        return Math.min(this.container1.getMaxStackSize(), this.container2.getMaxStackSize()); // CraftBukkit - check both sides
    }

    @Override
    public void setChanged() {
        this.container1.setChanged();
        this.container2.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container1.stillValid(player) && this.container2.stillValid(player);
    }

    @Override
    public void startOpen(ContainerUser user) {
        this.container1.startOpen(user);
        this.container2.startOpen(user);
    }

    @Override
    public void stopOpen(ContainerUser user) {
        this.container1.stopOpen(user);
        this.container2.stopOpen(user);
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return index >= this.container1.getContainerSize()
            ? this.container2.canPlaceItem(index - this.container1.getContainerSize(), stack)
            : this.container1.canPlaceItem(index, stack);
    }

    @Override
    public void clearContent() {
        this.container1.clearContent();
        this.container2.clearContent();
    }
}
