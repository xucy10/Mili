package net.minecraft.world.level.block.entity;

import java.util.function.Predicate;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

public interface ListBackedContainer extends Container {
    NonNullList<ItemStack> getItems();

    default int count() {
        return (int)this.getItems().stream().filter(Predicate.not(ItemStack::isEmpty)).count();
    }

    @Override
    default int getContainerSize() {
        return this.getItems().size();
    }

    @Override
    default void clearContent() {
        this.getItems().clear();
    }

    @Override
    default boolean isEmpty() {
        return this.getItems().stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    default ItemStack getItem(int slot) {
        return this.getItems().get(slot);
    }

    @Override
    default ItemStack removeItem(int slot, int amount) {
        ItemStack itemStack = ContainerHelper.removeItem(this.getItems(), slot, amount);
        if (!itemStack.isEmpty()) {
            this.setChanged();
        }

        return itemStack;
    }

    @Override
    default ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.removeItem(this.getItems(), slot, this.getMaxStackSize());
    }

    @Override
    default boolean canPlaceItem(int slot, ItemStack stack) {
        return this.acceptsItemType(stack) && (this.getItem(slot).isEmpty() || this.getItem(slot).getCount() < this.getMaxStackSize(stack));
    }

    default boolean acceptsItemType(ItemStack stack) {
        return true;
    }

    @Override
    default void setItem(int slot, ItemStack stack) {
        this.setItemNoUpdate(slot, stack);
        this.setChanged();
    }

    default void setItemNoUpdate(int slot, ItemStack stack) {
        this.getItems().set(slot, stack);
        stack.limitSize(this.getMaxStackSize(stack));
    }
}
