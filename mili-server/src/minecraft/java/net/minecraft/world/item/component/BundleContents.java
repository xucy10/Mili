package net.minecraft.world.item.component;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.apache.commons.lang3.math.Fraction;
import org.jspecify.annotations.Nullable;

public final class BundleContents implements TooltipComponent {
    public static final BundleContents EMPTY = new BundleContents(List.of());
    public static final Codec<BundleContents> CODEC = ItemStack.CODEC
        .listOf()
        .flatXmap(BundleContents::checkAndCreate, bundleContents -> DataResult.success(bundleContents.items));
    public static final StreamCodec<RegistryFriendlyByteBuf, BundleContents> STREAM_CODEC = ItemStack.STREAM_CODEC
        .apply(ByteBufCodecs.list())
        .apply(ByteBufCodecs::increaseDepth) // Paper - Track codec depth
        .map(BundleContents::new, contents -> contents.items);
    private static final Fraction BUNDLE_IN_BUNDLE_WEIGHT = Fraction.getFraction(1, 16);
    private static final int NO_STACK_INDEX = -1;
    public static final int NO_SELECTED_ITEM_INDEX = -1;
    final List<ItemStack> items;
    final Fraction weight;
    final int selectedItem;

    BundleContents(List<ItemStack> items, Fraction weight, int selectedItem) {
        this.items = items;
        this.weight = weight;
        this.selectedItem = selectedItem;
    }

    private static DataResult<BundleContents> checkAndCreate(List<ItemStack> items) {
        try {
            Fraction fraction = computeContentWeight(items);
            return DataResult.success(new BundleContents(items, fraction, -1));
        } catch (ArithmeticException var2) {
            return DataResult.error(() -> "Excessive total bundle weight");
        }
    }

    public BundleContents(List<ItemStack> items) {
        this(items, computeContentWeight(items), -1);
    }

    private static Fraction computeContentWeight(List<ItemStack> content) {
        Fraction fraction = Fraction.ZERO;

        for (ItemStack itemStack : content) {
            fraction = fraction.add(getWeight(itemStack).multiplyBy(Fraction.getFraction(itemStack.getCount(), 1)));
        }

        return fraction;
    }

    static Fraction getWeight(ItemStack stack) {
        BundleContents bundleContents = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundleContents != null) {
            return BUNDLE_IN_BUNDLE_WEIGHT.add(bundleContents.weight());
        } else {
            List<BeehiveBlockEntity.Occupant> list = stack.getOrDefault(DataComponents.BEES, Bees.EMPTY).bees();
            return !list.isEmpty() ? Fraction.ONE : Fraction.getFraction(1, stack.getMaxStackSize());
        }
    }

    public static boolean canItemBeInBundle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem().canFitInsideContainerItems();
    }

    // Paper start - correct bundle inventory action
    public int getMaxAmountToAdd(final ItemStack stack) {
        return Mutable.getMaxAmountToAdd(stack, this.weight);
    }
    // Paper end - correct bundle inventory action

    public int getNumberOfItemsToShow() {
        int size = this.size();
        int i = size > 12 ? 11 : 12;
        int i1 = size % 4;
        int i2 = i1 == 0 ? 0 : 4 - i1;
        return Math.min(size, i - i2);
    }

    public ItemStack getItemUnsafe(int index) {
        return this.items.get(index);
    }

    public Stream<ItemStack> itemCopyStream() {
        return this.items.stream().map(ItemStack::copy);
    }

    public Iterable<ItemStack> items() {
        return this.items;
    }

    public Iterable<ItemStack> itemsCopy() {
        return Lists.transform(this.items, ItemStack::copy);
    }

    public int size() {
        return this.items.size();
    }

    public Fraction weight() {
        return this.weight;
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    public int getSelectedItem() {
        return this.selectedItem;
    }

    public boolean hasSelectedItem() {
        return this.selectedItem != -1;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof BundleContents bundleContents
                && this.weight.equals(bundleContents.weight)
                && ItemStack.listMatches(this.items, bundleContents.items);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashStackList(this.items);
    }

    @Override
    public String toString() {
        return "BundleContents" + this.items;
    }

    public static class Mutable {
        private final List<ItemStack> items;
        private Fraction weight;
        private int selectedItem;

        public Mutable(BundleContents contents) {
            this.items = new ArrayList<>(contents.items);
            this.weight = contents.weight;
            this.selectedItem = contents.selectedItem;
        }

        public BundleContents.Mutable clearItems() {
            this.items.clear();
            this.weight = Fraction.ZERO;
            this.selectedItem = -1;
            return this;
        }

        private int findStackIndex(ItemStack stack) {
            if (!stack.isStackable()) {
                return -1;
            } else {
                for (int i = 0; i < this.items.size(); i++) {
                    if (ItemStack.isSameItemSameComponents(this.items.get(i), stack)) {
                        return i;
                    }
                }

                return -1;
            }
        }

        public int getMaxAmountToAdd(ItemStack stack) {
        // Paper start - correct bundle inventory action
        // Static overload to easily compute this value without the need for an instance of mutable.
            return getMaxAmountToAdd(stack, this.weight);
        }
        static int getMaxAmountToAdd(final ItemStack stack, final Fraction weight) {
            Fraction fraction = Fraction.ONE.subtract(weight);
        // Paper end - correct bundle inventory action
            return Math.max(fraction.divideBy(BundleContents.getWeight(stack)).intValue(), 0);
        }

        public int tryInsert(ItemStack stack) {
            if (!BundleContents.canItemBeInBundle(stack)) {
                return 0;
            } else {
                int min = Math.min(stack.getCount(), this.getMaxAmountToAdd(stack));
                if (min == 0) {
                    return 0;
                } else {
                    this.weight = this.weight.add(BundleContents.getWeight(stack).multiplyBy(Fraction.getFraction(min, 1)));
                    int i = this.findStackIndex(stack);
                    if (i != -1) {
                        ItemStack itemStack = this.items.remove(i);
                        ItemStack itemStack1 = itemStack.copyWithCount(itemStack.getCount() + min);
                        stack.shrink(min);
                        this.items.add(0, itemStack1);
                    } else {
                        this.items.add(0, stack.split(min));
                    }

                    return min;
                }
            }
        }

        public int tryTransfer(Slot slot, Player player) {
            ItemStack item = slot.getItem();
            int maxAmountToAdd = this.getMaxAmountToAdd(item);
            return BundleContents.canItemBeInBundle(item) ? this.tryInsert(slot.safeTake(item.getCount(), maxAmountToAdd, player)) : 0;
        }

        public void toggleSelectedItem(int selectedItem) {
            this.selectedItem = this.selectedItem != selectedItem && !this.indexIsOutsideAllowedBounds(selectedItem) ? selectedItem : -1;
        }

        private boolean indexIsOutsideAllowedBounds(int index) {
            return index < 0 || index >= this.items.size();
        }

        public @Nullable ItemStack removeOne() {
            if (this.items.isEmpty()) {
                return null;
            } else {
                int i = this.indexIsOutsideAllowedBounds(this.selectedItem) ? 0 : this.selectedItem;
                ItemStack itemStack = this.items.remove(i).copy();
                this.weight = this.weight.subtract(BundleContents.getWeight(itemStack).multiplyBy(Fraction.getFraction(itemStack.getCount(), 1)));
                this.toggleSelectedItem(-1);
                return itemStack;
            }
        }

        public Fraction weight() {
            return this.weight;
        }

        public BundleContents toImmutable() {
            return new BundleContents(List.copyOf(this.items), this.weight, this.selectedItem);
        }
    }
}
