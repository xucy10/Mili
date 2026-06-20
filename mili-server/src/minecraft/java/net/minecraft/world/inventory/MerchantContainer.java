package net.minecraft.world.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

public class MerchantContainer implements Container {
    private final Merchant merchant;
    private final NonNullList<ItemStack> itemStacks = NonNullList.withSize(3, ItemStack.EMPTY);
    private @Nullable MerchantOffer activeOffer;
    public int selectionHint;
    private int futureXp;
    // CraftBukkit start - add fields and methods
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;

    public java.util.List<ItemStack> getContents() {
        return this.itemStacks;
    }

    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.add(player);
    }

    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.remove(player);
        this.merchant.setTradingPlayer(null); // SPIGOT-4860
    }

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

    public @javax.annotation.Nullable org.bukkit.inventory.InventoryHolder getOwner() {
        return (this.merchant instanceof net.minecraft.world.entity.npc.villager.AbstractVillager abstractVillager) ? (org.bukkit.craftbukkit.entity.CraftAbstractVillager) abstractVillager.getBukkitEntity() : null;
    }

    @Override
    public @javax.annotation.Nullable org.bukkit.Location getLocation() {
        return (this.merchant instanceof net.minecraft.world.entity.npc.villager.AbstractVillager abstractVillager) ? abstractVillager.getBukkitEntity().getLocation() : null; // Paper - Fix inventories returning null Locations
    }
    // CraftBukkit end

    public MerchantContainer(Merchant merchant) {
        this.merchant = merchant;
    }

    @Override
    public int getContainerSize() {
        return this.itemStacks.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemStack : this.itemStacks) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return this.itemStacks.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack itemStack = this.itemStacks.get(index);
        if (index == 2 && !itemStack.isEmpty()) {
            return ContainerHelper.removeItem(this.itemStacks, index, itemStack.getCount());
        } else {
            ItemStack itemStack1 = ContainerHelper.removeItem(this.itemStacks, index, count);
            if (!itemStack1.isEmpty() && this.isPaymentSlot(index)) {
                this.updateSellItem();
            }

            return itemStack1;
        }
    }

    private boolean isPaymentSlot(int slot) {
        return slot == 0 || slot == 1;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return ContainerHelper.takeItem(this.itemStacks, index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.itemStacks.set(index, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        if (this.isPaymentSlot(index)) {
            this.updateSellItem();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.merchant.getTradingPlayer() == player;
    }

    @Override
    public void setChanged() {
        this.updateSellItem();
    }

    public void updateSellItem() {
        this.activeOffer = null;
        ItemStack itemStack;
        ItemStack itemStack1;
        if (this.itemStacks.get(0).isEmpty()) {
            itemStack = this.itemStacks.get(1);
            itemStack1 = ItemStack.EMPTY;
        } else {
            itemStack = this.itemStacks.get(0);
            itemStack1 = this.itemStacks.get(1);
        }

        if (itemStack.isEmpty()) {
            this.setItem(2, ItemStack.EMPTY);
            this.futureXp = 0;
        } else {
            MerchantOffers offers = this.merchant.getOffers();
            if (!offers.isEmpty()) {
                MerchantOffer recipeFor = offers.getRecipeFor(itemStack, itemStack1, this.selectionHint);
                if (recipeFor == null || recipeFor.isOutOfStock()) {
                    this.activeOffer = recipeFor;
                    recipeFor = offers.getRecipeFor(itemStack1, itemStack, this.selectionHint);
                }

                if (recipeFor != null && !recipeFor.isOutOfStock()) {
                    this.activeOffer = recipeFor;
                    this.setItem(2, recipeFor.assemble());
                    this.futureXp = recipeFor.getXp();
                } else {
                    this.setItem(2, ItemStack.EMPTY);
                    this.futureXp = 0;
                }
            }

            this.merchant.notifyTradeUpdated(this.getItem(2));
        }
    }

    public @Nullable MerchantOffer getActiveOffer() {
        return this.activeOffer;
    }

    public void setSelectionHint(int currentRecipeIndex) {
        this.selectionHint = currentRecipeIndex;
        this.updateSellItem();
    }

    @Override
    public void clearContent() {
        this.itemStacks.clear();
    }

    public int getFutureXp() {
        return this.futureXp;
    }
}
