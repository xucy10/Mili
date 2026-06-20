package net.minecraft.world.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.ClientSideMerchant;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public class MerchantMenu extends AbstractContainerMenu {
    protected static final int PAYMENT1_SLOT = 0;
    protected static final int PAYMENT2_SLOT = 1;
    protected static final int RESULT_SLOT = 2;
    private static final int INV_SLOT_START = 3;
    private static final int INV_SLOT_END = 30;
    private static final int USE_ROW_SLOT_START = 30;
    private static final int USE_ROW_SLOT_END = 39;
    private static final int SELLSLOT1_X = 136;
    private static final int SELLSLOT2_X = 162;
    private static final int BUYSLOT_X = 220;
    private static final int ROW_Y = 37;
    private final Merchant trader;
    private final MerchantContainer tradeContainer;
    private int merchantLevel;
    private boolean showProgressBar;
    private boolean canRestock;
    // CraftBukkit start
    private @javax.annotation.Nullable org.bukkit.craftbukkit.inventory.view.CraftMerchantView view = null;
    private final Inventory inventory;

    @Override
    public org.bukkit.craftbukkit.inventory.view.CraftMerchantView getBukkitView() {
        if (this.view == null) {
            this.view = new org.bukkit.craftbukkit.inventory.view.CraftMerchantView(this.inventory.player.getBukkitEntity(), new org.bukkit.craftbukkit.inventory.CraftInventoryMerchant(this.trader, this.tradeContainer), this, this.trader);
        }
        return this.view;
    }
    // CraftBukkit end

    public MerchantMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new ClientSideMerchant(playerInventory.player));
    }

    public MerchantMenu(int containerId, Inventory playerInventory, Merchant trader) {
        super(MenuType.MERCHANT, containerId);
        this.trader = trader;
        this.tradeContainer = new MerchantContainer(trader);
        this.addSlot(new Slot(this.tradeContainer, 0, 136, 37));
        this.addSlot(new Slot(this.tradeContainer, 1, 162, 37));
        this.addSlot(new MerchantResultSlot(playerInventory.player, trader, this.tradeContainer, 2, 220, 37));
        this.inventory = playerInventory; // CraftBukkit
        this.addStandardInventorySlots(playerInventory, 108, 84);
    }

    public void setShowProgressBar(boolean showProgressBar) {
        this.showProgressBar = showProgressBar;
    }

    @Override
    public void slotsChanged(Container inventory) {
        this.tradeContainer.updateSellItem();
        super.slotsChanged(inventory);
    }

    public void setSelectionHint(int currentRecipeIndex) {
        this.tradeContainer.setSelectionHint(currentRecipeIndex);
    }

    @Override
    public boolean stillValid(Player player) {
        if (!checkReachable) return true; // Paper - checkReachable
        return this.trader.stillValid(player);
    }

    public int getTraderXp() {
        return this.trader.getVillagerXp();
    }

    public int getFutureTraderXp() {
        return this.tradeContainer.getFutureXp();
    }

    public void setXp(int xp) {
        this.trader.overrideXp(xp);
    }

    public int getTraderLevel() {
        return this.merchantLevel;
    }

    public void setMerchantLevel(int level) {
        this.merchantLevel = level;
    }

    public void setCanRestock(boolean canRestock) {
        this.canRestock = canRestock;
    }

    public boolean canRestock() {
        return this.canRestock;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            if (slotIndex == 2) {
                if (!this.moveItemStackTo(item, 3, 39, true, true)) { // Paper - Add PlayerTradeEvent and PlayerPurchaseEvent
                    return ItemStack.EMPTY;
                }

                // slot.onQuickCraft(item, itemStack); // Paper - Add PlayerTradeEvent and PlayerPurchaseEvent; moved to after the non-check moveItemStackTo call
                // this.playTradeSound();
            } else if (slotIndex != 0 && slotIndex != 1) {
                if (slotIndex >= 3 && slotIndex < 30) {
                    if (!this.moveItemStackTo(item, 30, 39, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex >= 30 && slotIndex < 39 && !this.moveItemStackTo(item, 3, 30, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(item, 3, 39, false)) {
                return ItemStack.EMPTY;
            }

            if (slotIndex != 2) { // Paper - Add PlayerTradeEvent and PlayerPurchaseEvent; moved down for slot 2
            if (item.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (item.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, item);
            } // Paper start - Add PlayerTradeEvent and PlayerPurchaseEvent; handle slot 2
            if (slotIndex == 2) { // is merchant result slot
                slot.onTake(player, item);
                if (item.isEmpty()) {
                    slot.set(ItemStack.EMPTY);
                    return ItemStack.EMPTY;
                }

                this.moveItemStackTo(item, 3, 39, true, false); // This should always succeed because it's checked above

                slot.onQuickCraft(item, itemStack);
                this.playTradeSound();
                slot.set(ItemStack.EMPTY); // item should ALWAYS be empty
            }
            // Paper end - Add PlayerTradeEvent and PlayerPurchaseEvent
        }

        return itemStack;
    }

    private void playTradeSound() {
        if (!this.trader.isClientSide() && this.trader instanceof Entity) { // CraftBukkit - SPIGOT-5035
            Entity entity = (Entity)this.trader;
            entity.level()
                .playLocalSound(entity.getX(), entity.getY(), entity.getZ(), this.trader.getNotifyTradeSound(), SoundSource.NEUTRAL, 1.0F, 1.0F, false);
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.trader.setTradingPlayer(null);
        if (!this.trader.isClientSide()) {
            if (!player.isAlive() || player instanceof ServerPlayer && ((ServerPlayer)player).hasDisconnected()) {
                ItemStack itemStack = this.tradeContainer.removeItemNoUpdate(0);
                if (!itemStack.isEmpty()) {
                    player.drop(itemStack, false);
                }

                itemStack = this.tradeContainer.removeItemNoUpdate(1);
                if (!itemStack.isEmpty()) {
                    player.drop(itemStack, false);
                }
            } else if (player instanceof ServerPlayer) {
                player.getInventory().placeItemBackInInventory(this.tradeContainer.removeItemNoUpdate(0));
                player.getInventory().placeItemBackInInventory(this.tradeContainer.removeItemNoUpdate(1));
            }
        }
    }

    public void tryMoveItems(int selectedMerchantRecipe) {
        if (selectedMerchantRecipe >= 0 && this.getOffers().size() > selectedMerchantRecipe) {
            ItemStack item = this.tradeContainer.getItem(0);
            if (!item.isEmpty()) {
                if (!this.moveItemStackTo(item, 3, 39, true)) {
                    return;
                }

                this.tradeContainer.setItem(0, item);
            }

            ItemStack item1 = this.tradeContainer.getItem(1);
            if (!item1.isEmpty()) {
                if (!this.moveItemStackTo(item1, 3, 39, true)) {
                    return;
                }

                this.tradeContainer.setItem(1, item1);
            }

            if (this.tradeContainer.getItem(0).isEmpty() && this.tradeContainer.getItem(1).isEmpty()) {
                MerchantOffer merchantOffer = this.getOffers().get(selectedMerchantRecipe);
                this.moveFromInventoryToPaymentSlot(0, merchantOffer.getItemCostA());
                merchantOffer.getItemCostB().ifPresent(itemCost -> this.moveFromInventoryToPaymentSlot(1, itemCost));
            }
        }
    }

    private void moveFromInventoryToPaymentSlot(int paymentSlotIndex, ItemCost payment) {
        for (int i = 3; i < 39; i++) {
            ItemStack item = this.slots.get(i).getItem();
            if (!item.isEmpty() && payment.test(item)) {
                ItemStack item1 = this.tradeContainer.getItem(paymentSlotIndex);
                if (item1.isEmpty() || ItemStack.isSameItemSameComponents(item, item1)) {
                    int maxStackSize = item.getMaxStackSize();
                    int min = Math.min(maxStackSize - item1.getCount(), item.getCount());
                    ItemStack itemStack = item.copyWithCount(item1.getCount() + min);
                    item.shrink(min);
                    this.tradeContainer.setItem(paymentSlotIndex, itemStack);
                    if (itemStack.getCount() >= maxStackSize) {
                        break;
                    }
                }
            }
        }
    }

    public void setOffers(MerchantOffers offers) {
        this.trader.overrideOffers(offers);
    }

    public MerchantOffers getOffers() {
        return this.trader.getOffers();
    }

    public boolean showProgressBar() {
        return this.showProgressBar;
    }
}
