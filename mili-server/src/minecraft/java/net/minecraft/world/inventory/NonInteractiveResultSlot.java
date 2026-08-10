package net.minecraft.world.inventory;

import java.util.Optional;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class NonInteractiveResultSlot extends Slot {
    public NonInteractiveResultSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public void onQuickCraft(ItemStack oldStack, ItemStack newStack) {
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public Optional<ItemStack> tryRemove(int count, int decrement, Player player) {
        return Optional.empty();
    }

    @Override
    public ItemStack safeTake(int count, int decrement, Player player) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack safeInsert(ItemStack stack) {
        return stack;
    }

    @Override
    public ItemStack safeInsert(ItemStack stack, int increment) {
        return this.safeInsert(stack);
    }

    @Override
    public boolean allowModification(Player player) {
        return false;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack remove(int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
    }

    @Override
    public boolean isHighlightable() {
        return false;
    }

    @Override
    public boolean isFake() {
        return true;
    }
}
