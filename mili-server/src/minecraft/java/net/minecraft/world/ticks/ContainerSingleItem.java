package net.minecraft.world.ticks;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface ContainerSingleItem extends Container {
    ItemStack getTheItem();

    default ItemStack splitTheItem(int amount) {
        return this.getTheItem().split(amount);
    }

    void setTheItem(ItemStack item);

    default ItemStack removeTheItem() {
        return this.splitTheItem(this.getMaxStackSize());
    }

    @Override
    default int getContainerSize() {
        return 1;
    }

    @Override
    default boolean isEmpty() {
        return this.getTheItem().isEmpty();
    }

    @Override
    default void clearContent() {
        this.removeTheItem();
    }

    @Override
    default ItemStack removeItemNoUpdate(int slot) {
        return this.removeItem(slot, this.getMaxStackSize());
    }

    @Override
    default ItemStack getItem(int slot) {
        return slot == 0 ? this.getTheItem() : ItemStack.EMPTY;
    }

    @Override
    default ItemStack removeItem(int slot, int amount) {
        return slot != 0 ? ItemStack.EMPTY : this.splitTheItem(amount);
    }

    @Override
    default void setItem(int slot, ItemStack stack) {
        if (slot == 0) {
            this.setTheItem(stack);
        }
    }

    public interface BlockContainerSingleItem extends ContainerSingleItem {
        BlockEntity getContainerBlockEntity();

        @Override
        default boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(this.getContainerBlockEntity(), player);
        }
    }
}
