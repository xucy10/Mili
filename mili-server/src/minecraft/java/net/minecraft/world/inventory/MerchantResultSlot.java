package net.minecraft.world.inventory;

import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;

public class MerchantResultSlot extends Slot {
    private final MerchantContainer slots;
    private final Player player;
    private int removeCount;
    private final Merchant merchant;

    public MerchantResultSlot(Player player, Merchant merchant, MerchantContainer slots, int slot, int x, int y) {
        super(slots, slot, x, y);
        this.player = player;
        this.merchant = merchant;
        this.slots = slots;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack remove(int amount) {
        if (this.hasItem()) {
            this.removeCount = this.removeCount + Math.min(amount, this.getItem().getCount());
        }

        return super.remove(amount);
    }

    @Override
    protected void onQuickCraft(ItemStack stack, int amount) {
        this.removeCount += amount;
        this.checkTakeAchievements(stack);
    }

    @Override
    protected void checkTakeAchievements(ItemStack stack) {
        stack.onCraftedBy(this.player, this.removeCount);
        this.removeCount = 0;
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        // this.checkTakeAchievements(stack); // Paper - Add PlayerTradeEvent and PlayerPurchaseEvent; move to after event is called and not cancelled
        MerchantOffer activeOffer = this.slots.getActiveOffer();
        // Paper start - Add PlayerTradeEvent and PlayerPurchaseEvent
        io.papermc.paper.event.player.PlayerPurchaseEvent event = null;
        if (activeOffer != null && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (this.merchant instanceof net.minecraft.world.entity.npc.villager.AbstractVillager abstractVillager) {
                event = new io.papermc.paper.event.player.PlayerTradeEvent(serverPlayer.getBukkitEntity(), (org.bukkit.entity.AbstractVillager) abstractVillager.getBukkitEntity(), activeOffer.asBukkit(), true, true);
            } else if (this.merchant instanceof org.bukkit.craftbukkit.inventory.CraftMerchantCustom.MinecraftMerchant minecraftMerchant) {
                event = new io.papermc.paper.event.player.PlayerPurchaseEvent(serverPlayer.getBukkitEntity(), minecraftMerchant.getCraftMerchant(), activeOffer.asBukkit(), false, true);
            }
            if (event != null) {
                if (!event.callEvent()) {
                    stack.setCount(0);
                    player.containerMenu.sendAllDataToRemote();
                    int level = merchant instanceof net.minecraft.world.entity.npc.villager.Villager villager ? villager.getVillagerData().level() : 1;
                    serverPlayer.sendMerchantOffers(player.containerMenu.containerId, merchant.getOffers(), level, merchant.getVillagerXp(), merchant.showProgressBar(), merchant.canRestock());
                    return;
                }
                activeOffer = org.bukkit.craftbukkit.inventory.CraftMerchantRecipe.fromBukkit(event.getTrade()).toMinecraft();
            }
        }
        this.checkTakeAchievements(stack);
        // Paper end - Add PlayerTradeEvent and PlayerPurchaseEvent
        if (activeOffer != null) {
            ItemStack item = this.slots.getItem(0);
            ItemStack item1 = this.slots.getItem(1);
            if (activeOffer.take(item, item1) || activeOffer.take(item1, item)) {
                this.merchant.processTrade(activeOffer, event); // Paper - Add PlayerTradeEvent and PlayerPurchaseEvent
                player.awardStat(Stats.TRADED_WITH_VILLAGER);
                this.slots.setItem(0, item);
                this.slots.setItem(1, item1);
            }

            this.merchant.overrideXp(this.merchant.getVillagerXp() + activeOffer.getXp());
        }
    }
}
