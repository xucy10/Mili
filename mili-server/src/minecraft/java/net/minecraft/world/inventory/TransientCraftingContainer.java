package net.minecraft.world.inventory;

import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;

public class TransientCraftingContainer implements CraftingContainer {
    private final NonNullList<ItemStack> items;
    private final int width;
    private final int height;
    private final AbstractContainerMenu menu;

    // CraftBukkit start - add fields
    public List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> currentRecipe;
    public net.minecraft.world.Container resultInventory;
    private Player owner;
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
    public List<org.bukkit.entity.HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public @javax.annotation.Nullable org.bukkit.inventory.InventoryHolder getOwner() {
        return (this.owner == null) ? null : this.owner.getBukkitEntity();
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    @Override
    public void setMaxStackSize(int size) {
        this.maxStack = size;
        this.resultInventory.setMaxStackSize(size);
    }

    @Override
    public org.bukkit.Location getLocation() {
        return this.menu instanceof CraftingMenu ? ((CraftingMenu) this.menu).access.getLocation() : this.owner.getBukkitEntity().getLocation();
    }

    @Override
    public net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> getCurrentRecipe() {
        return this.currentRecipe;
    }

    @Override
    public void setCurrentRecipe(net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> currentRecipe) {
        this.currentRecipe = currentRecipe;
    }

    public TransientCraftingContainer(AbstractContainerMenu menu, int width, int height, Player player) {
        this(menu, width, height);
        this.owner = player;
    }
    // CraftBukkit end

    public TransientCraftingContainer(AbstractContainerMenu menu, int width, int height) {
        this(menu, width, height, NonNullList.withSize(width * height, ItemStack.EMPTY));
    }

    private TransientCraftingContainer(AbstractContainerMenu menu, int width, int height, NonNullList<ItemStack> items) {
        this.items = items;
        this.menu = menu;
        this.width = width;
        this.height = height;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
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
    public ItemStack getItem(int slot) {
        return slot >= this.getContainerSize() ? ItemStack.EMPTY : this.items.get(slot);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack itemStack = ContainerHelper.removeItem(this.items, slot, amount);
        if (!itemStack.isEmpty()) {
            this.menu.slotsChanged(this);
        }

        return itemStack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        this.menu.slotsChanged(this);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public List<ItemStack> getItems() {
        return List.copyOf(this.items);
    }

    @Override
    public void fillStackedContents(StackedItemContents stackedContents) {
        for (ItemStack itemStack : this.items) {
            stackedContents.accountSimpleStack(itemStack);
        }
    }
}
