package net.minecraft.world.entity.player;

import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class PlayerEquipment extends EntityEquipment {
    private final Player player;

    public PlayerEquipment(Player player) {
        this.player = player;
    }

    @Override
    public ItemStack set(EquipmentSlot slot, ItemStack stack) {
        return slot == EquipmentSlot.MAINHAND ? this.player.getInventory().setSelectedItem(stack) : super.set(slot, stack);
    }

    @Override
    public ItemStack get(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? this.player.getInventory().getSelectedItem() : super.get(slot);
    }

    @Override
    public boolean isEmpty() {
        return this.player.getInventory().getSelectedItem().isEmpty() && super.isEmpty();
    }
}
