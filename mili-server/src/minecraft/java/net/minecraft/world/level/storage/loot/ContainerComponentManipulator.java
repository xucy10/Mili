package net.minecraft.world.level.storage.loot;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.slot.SlotCollection;

public interface ContainerComponentManipulator<T> {
    DataComponentType<T> type();

    T empty();

    T setContents(T contents, Stream<ItemStack> items);

    Stream<ItemStack> getContents(T contents);

    default void setContents(ItemStack stack, T contents, Stream<ItemStack> items) {
        T orDefault = stack.getOrDefault(this.type(), contents);
        T object = this.setContents(orDefault, items);
        stack.set(this.type(), object);
    }

    default void setContents(ItemStack stack, Stream<ItemStack> items) {
        this.setContents(stack, this.empty(), items);
    }

    default void modifyItems(ItemStack stack, UnaryOperator<ItemStack> modifier) {
        T object = stack.get(this.type());
        if (object != null) {
            UnaryOperator<ItemStack> unaryOperator = itemStack -> {
                if (itemStack.isEmpty()) {
                    return itemStack;
                } else {
                    ItemStack itemStack1 = modifier.apply(itemStack);
                    itemStack1.limitSize(itemStack1.getMaxStackSize());
                    return itemStack1;
                }
            };
            this.setContents(stack, this.getContents(object).map(unaryOperator));
        }
    }

    default SlotCollection getSlots(ItemStack stack) {
        return () -> {
            T object = stack.get(this.type());
            return object != null ? this.getContents(object).filter(itemStack -> !itemStack.isEmpty()) : Stream.empty();
        };
    }
}
