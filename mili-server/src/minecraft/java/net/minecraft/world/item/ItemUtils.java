package net.minecraft.world.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class ItemUtils {
    public static InteractionResult startUsingInstantly(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    public static ItemStack createFilledResult(ItemStack emptyStack, Player player, ItemStack filledStack, boolean preventDuplicates) {
        boolean hasInfiniteMaterials = player.hasInfiniteMaterials();
        if (preventDuplicates && hasInfiniteMaterials) {
            if (!player.getInventory().contains(filledStack)) {
                player.getInventory().add(filledStack);
            }

            return emptyStack;
        } else {
            emptyStack.consume(1, player);
            if (emptyStack.isEmpty()) {
                return filledStack;
            } else {
                if (!player.getInventory().add(filledStack)) {
                    player.drop(filledStack, false);
                }

                return emptyStack;
            }
        }
    }

    public static ItemStack createFilledResult(ItemStack emptyStack, Player player, ItemStack filledStack) {
        return createFilledResult(emptyStack, player, filledStack, true);
    }

    public static void onContainerDestroyed(ItemEntity container, Iterable<ItemStack> contents) {
        Level level = container.level();
        if (!level.isClientSide()) {
            // Paper start - call EntityDropItemEvent
            contents.forEach(itemStack -> {
                ItemEntity droppedItem = new ItemEntity(level, container.getX(), container.getY(), container.getZ(), itemStack);
                org.bukkit.event.entity.EntityDropItemEvent event = new org.bukkit.event.entity.EntityDropItemEvent(container.getBukkitEntity(), (org.bukkit.entity.Item) droppedItem.getBukkitEntity());
                if (event.callEvent()) {
                    level.addFreshEntity(droppedItem);
                }
            });
            // Paper end - call EntityDropItemEvent
        }
    }
}
