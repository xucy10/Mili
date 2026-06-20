package net.minecraft.world.entity.npc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public interface InventoryCarrier {
    String TAG_INVENTORY = "Inventory";

    SimpleContainer getInventory();

    static void pickUpItem(ServerLevel level, Mob mob, InventoryCarrier carrier, ItemEntity itemEntity) {
        ItemStack item = itemEntity.getItem();
        if (mob.wantsToPickUp(level, item)) {
            SimpleContainer inventory = carrier.getInventory();
            boolean canAddItem = inventory.canAddItem(item);
            if (!canAddItem) {
                return;
            }

            // CraftBukkit start
            ItemStack remaining = new SimpleContainer(inventory).addItem(item);
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(mob, itemEntity, remaining.getCount()).isCancelled()) {
                return;
            }
            // CraftBukkit end

            mob.onItemPickup(itemEntity);
            int count = item.getCount();
            ItemStack itemStack = inventory.addItem(item);
            mob.take(itemEntity, count - itemStack.getCount());
            if (itemStack.isEmpty()) {
                itemEntity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
            } else {
                item.setCount(itemStack.getCount());
            }
        }
    }

    default void readInventoryFromTag(ValueInput input) {
        input.list("Inventory", ItemStack.CODEC)
            .ifPresent(typedInputList -> this.getInventory().fromItemList((ValueInput.TypedInputList<ItemStack>)typedInputList));
    }

    default void writeInventoryToTag(ValueOutput output) {
        this.getInventory().storeAsItemList(output.list("Inventory", ItemStack.CODEC));
    }
}
