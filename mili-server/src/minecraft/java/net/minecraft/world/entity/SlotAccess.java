package net.minecraft.world.entity;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;

public interface SlotAccess {
    ItemStack get();

    boolean set(ItemStack carried);

    static SlotAccess of(final Supplier<ItemStack> getter, final Consumer<ItemStack> setter) {
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return getter.get();
            }

            @Override
            public boolean set(ItemStack carried) {
                setter.accept(carried);
                return true;
            }
        };
    }

    static SlotAccess forEquipmentSlot(final LivingEntity entity, final EquipmentSlot slot, final Predicate<ItemStack> stackFilter) {
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return entity.getItemBySlot(slot);
            }

            @Override
            public boolean set(ItemStack carried) {
                if (!stackFilter.test(carried)) {
                    return false;
                } else {
                    entity.setItemSlot(slot, carried);
                    return true;
                }
            }
        };
    }

    static SlotAccess forEquipmentSlot(LivingEntity entity, EquipmentSlot slot) {
        return forEquipmentSlot(entity, slot, stack -> true);
    }

    static SlotAccess forListElement(final List<ItemStack> items, final int slotIndex) {
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return items.get(slotIndex);
            }

            @Override
            public boolean set(ItemStack carried) {
                items.set(slotIndex, carried);
                return true;
            }
        };
    }
}
