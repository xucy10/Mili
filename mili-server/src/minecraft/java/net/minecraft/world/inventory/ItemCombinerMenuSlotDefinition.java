package net.minecraft.world.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

public class ItemCombinerMenuSlotDefinition {
    private final List<ItemCombinerMenuSlotDefinition.SlotDefinition> slots;
    private final ItemCombinerMenuSlotDefinition.SlotDefinition resultSlot;

    ItemCombinerMenuSlotDefinition(List<ItemCombinerMenuSlotDefinition.SlotDefinition> slots, ItemCombinerMenuSlotDefinition.SlotDefinition resultSlot) {
        if (!slots.isEmpty() && !resultSlot.equals(ItemCombinerMenuSlotDefinition.SlotDefinition.EMPTY)) {
            this.slots = slots;
            this.resultSlot = resultSlot;
        } else {
            throw new IllegalArgumentException("Need to define both inputSlots and resultSlot");
        }
    }

    public static ItemCombinerMenuSlotDefinition.Builder create() {
        return new ItemCombinerMenuSlotDefinition.Builder();
    }

    public ItemCombinerMenuSlotDefinition.SlotDefinition getSlot(int slot) {
        return this.slots.get(slot);
    }

    public ItemCombinerMenuSlotDefinition.SlotDefinition getResultSlot() {
        return this.resultSlot;
    }

    public List<ItemCombinerMenuSlotDefinition.SlotDefinition> getSlots() {
        return this.slots;
    }

    public int getNumOfInputSlots() {
        return this.slots.size();
    }

    public int getResultSlotIndex() {
        return this.getNumOfInputSlots();
    }

    public static class Builder {
        private final List<ItemCombinerMenuSlotDefinition.SlotDefinition> inputSlots = new ArrayList<>();
        private ItemCombinerMenuSlotDefinition.SlotDefinition resultSlot = ItemCombinerMenuSlotDefinition.SlotDefinition.EMPTY;

        public ItemCombinerMenuSlotDefinition.Builder withSlot(int slotIndex, int x, int y, Predicate<ItemStack> mayPlace) {
            this.inputSlots.add(new ItemCombinerMenuSlotDefinition.SlotDefinition(slotIndex, x, y, mayPlace));
            return this;
        }

        public ItemCombinerMenuSlotDefinition.Builder withResultSlot(int slotIndex, int x, int y) {
            this.resultSlot = new ItemCombinerMenuSlotDefinition.SlotDefinition(slotIndex, x, y, stack -> false);
            return this;
        }

        public ItemCombinerMenuSlotDefinition build() {
            int size = this.inputSlots.size();

            for (int i = 0; i < size; i++) {
                ItemCombinerMenuSlotDefinition.SlotDefinition slotDefinition = this.inputSlots.get(i);
                if (slotDefinition.slotIndex != i) {
                    throw new IllegalArgumentException("Expected input slots to have continous indexes");
                }
            }

            if (this.resultSlot.slotIndex != size) {
                throw new IllegalArgumentException("Expected result slot index to follow last input slot");
            } else {
                return new ItemCombinerMenuSlotDefinition(this.inputSlots, this.resultSlot);
            }
        }
    }

    public record SlotDefinition(int slotIndex, int x, int y, Predicate<ItemStack> mayPlace) {
        static final ItemCombinerMenuSlotDefinition.SlotDefinition EMPTY = new ItemCombinerMenuSlotDefinition.SlotDefinition(0, 0, 0, stack -> true);
    }
}
