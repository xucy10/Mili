package net.minecraft.world.item.component;

import com.google.common.collect.Iterables;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class ItemContainerContents implements TooltipProvider {
    private static final int NO_SLOT = -1;
    public static final int MAX_SIZE = 256;
    public static final ItemContainerContents EMPTY = new ItemContainerContents(NonNullList.create());
    public static final Codec<ItemContainerContents> CODEC = ItemContainerContents.Slot.CODEC
        .sizeLimitedListOf(256)
        .xmap(ItemContainerContents::fromSlots, ItemContainerContents::asSlots);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemContainerContents> STREAM_CODEC = ItemStack.OPTIONAL_STREAM_CODEC
        .apply(ByteBufCodecs.list(256))
        .apply(ByteBufCodecs::increaseDepth) // Paper - Track codec depth
        .map(ItemContainerContents::new, contents -> contents.items);
    public final NonNullList<ItemStack> items;
    private final int hashCode;

    private ItemContainerContents(NonNullList<ItemStack> items) {
        if (items.size() > 256) {
            throw new IllegalArgumentException("Got " + items.size() + " items, but maximum is 256");
        } else {
            this.items = items;
            this.hashCode = ItemStack.hashStackList(items);
        }
    }

    private ItemContainerContents(int size) {
        this(NonNullList.withSize(size, ItemStack.EMPTY));
    }

    private ItemContainerContents(List<ItemStack> size) {
        this(size.size());

        for (int i = 0; i < size.size(); i++) {
            this.items.set(i, size.get(i));
        }
    }

    private static ItemContainerContents fromSlots(List<ItemContainerContents.Slot> slots) {
        OptionalInt optionalInt = slots.stream().mapToInt(ItemContainerContents.Slot::index).max();
        if (optionalInt.isEmpty()) {
            return EMPTY;
        } else {
            ItemContainerContents itemContainerContents = new ItemContainerContents(optionalInt.getAsInt() + 1);

            for (ItemContainerContents.Slot slot : slots) {
                itemContainerContents.items.set(slot.index(), slot.item());
            }

            return itemContainerContents;
        }
    }

    public static ItemContainerContents fromItems(List<ItemStack> items) {
        int i = findLastNonEmptySlot(items);
        if (i == -1) {
            return EMPTY;
        } else {
            ItemContainerContents itemContainerContents = new ItemContainerContents(i + 1);

            for (int i1 = 0; i1 <= i; i1++) {
                itemContainerContents.items.set(i1, items.get(i1).copy());
            }

            return itemContainerContents;
        }
    }

    private static int findLastNonEmptySlot(List<ItemStack> items) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (!items.get(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    private List<ItemContainerContents.Slot> asSlots() {
        List<ItemContainerContents.Slot> list = new ArrayList<>();

        for (int i = 0; i < this.items.size(); i++) {
            ItemStack itemStack = this.items.get(i);
            if (!itemStack.isEmpty()) {
                list.add(new ItemContainerContents.Slot(i, itemStack));
            }
        }

        return list;
    }

    public void copyInto(NonNullList<ItemStack> list) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack itemStack = i < this.items.size() ? this.items.get(i) : ItemStack.EMPTY;
            list.set(i, itemStack.copy());
        }
    }

    public ItemStack copyOne() {
        return this.items.isEmpty() ? ItemStack.EMPTY : this.items.get(0).copy();
    }

    public Stream<ItemStack> stream() {
        return this.items.stream().map(ItemStack::copy);
    }

    public Stream<ItemStack> nonEmptyStream() {
        return this.items.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy);
    }

    public Iterable<ItemStack> nonEmptyItems() {
        return Iterables.filter(this.items, stack -> !stack.isEmpty());
    }

    public Iterable<ItemStack> nonEmptyItemsCopy() {
        return Iterables.transform(this.nonEmptyItems(), ItemStack::copy);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ItemContainerContents itemContainerContents && ItemStack.listMatches(this.items, itemContainerContents.items);
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag flag, DataComponentGetter componentGetter) {
        int i = 0;
        int i1 = 0;

        for (ItemStack itemStack : this.nonEmptyItems()) {
            i1++;
            if (i <= 4) {
                i++;
                tooltipAdder.accept(Component.translatable("item.container.item_count", itemStack.getHoverName(), itemStack.getCount()));
            }
        }

        if (i1 - i > 0) {
            tooltipAdder.accept(Component.translatable("item.container.more_items", i1 - i).withStyle(ChatFormatting.ITALIC));
        }
    }

    record Slot(int index, ItemStack item) {
        public static final Codec<ItemContainerContents.Slot> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.intRange(0, 255).fieldOf("slot").forGetter(ItemContainerContents.Slot::index),
                    ItemStack.CODEC.fieldOf("item").forGetter(ItemContainerContents.Slot::item)
                )
                .apply(instance, ItemContainerContents.Slot::new)
        );
    }
}
