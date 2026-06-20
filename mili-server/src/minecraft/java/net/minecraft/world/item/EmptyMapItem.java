package net.minecraft.world.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class EmptyMapItem extends Item {
    public EmptyMapItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel) {
            org.bukkit.inventory.ItemStack emptyMap = itemInHand.asBukkitCopy(); // Paper - PlayerMapFilledEvent
            itemInHand.consume(1, player);
            player.awardStat(Stats.ITEM_USED.get(this));
            serverLevel.playSound(null, player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, player.getSoundSource(), 1.0F, 1.0F);
            ItemStack itemStack = MapItem.create(serverLevel, player.getBlockX(), player.getBlockZ(), (byte)0, true, false);
            // Paper start - PlayerMapFilledEvent
            io.papermc.paper.event.player.PlayerMapFilledEvent event = new io.papermc.paper.event.player.PlayerMapFilledEvent((org.bukkit.entity.Player) player.getBukkitEntity(), emptyMap, org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack));
            event.callEvent();
            itemStack = org.bukkit.craftbukkit.inventory.CraftItemStack.unwrap(event.getCreatedMap());
            // Paper end - PlayerMapFilledEvent
            if (itemInHand.isEmpty()) {
                return InteractionResult.SUCCESS.heldItemTransformedTo(itemStack);
            } else {
                if (!player.getInventory().add(itemStack.copy())) {
                    player.drop(itemStack, false);
                }

                return InteractionResult.SUCCESS;
            }
        } else {
            return InteractionResult.SUCCESS;
        }
    }
}
