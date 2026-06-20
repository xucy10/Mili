package net.minecraft.world.item.slot;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.ItemStack;

public interface SlotCollection {
    SlotCollection EMPTY = Stream::empty;

    Stream<ItemStack> itemCopies();

    default SlotCollection filter(Predicate<ItemStack> filter) {
        return new SlotCollection.Filtered(this, filter);
    }

    default SlotCollection flatMap(Function<ItemStack, ? extends SlotCollection> mapper) {
        return new SlotCollection.FlatMapped(this, mapper);
    }

    default SlotCollection limit(int limit) {
        return new SlotCollection.Limited(this, limit);
    }

    static SlotCollection of(SlotAccess access) {
        return () -> Stream.of(access.get().copy());
    }

    static SlotCollection of(Collection<? extends SlotAccess> slots) {
        return switch (slots.size()) {
            case 0 -> EMPTY;
            case 1 -> of(slots.iterator().next());
            default -> () -> slots.stream().map(SlotAccess::get).map(ItemStack::copy);
        };
    }

    static SlotCollection concat(SlotCollection slots1, SlotCollection slots2) {
        return () -> Stream.concat(slots1.itemCopies(), slots2.itemCopies());
    }

    static SlotCollection concat(List<? extends SlotCollection> slots) {
        return switch (slots.size()) {
            case 0 -> EMPTY;
            case 1 -> (SlotCollection)slots.getFirst();
            case 2 -> concat(slots.get(0), slots.get(1));
            default -> () -> slots.stream().flatMap(SlotCollection::itemCopies);
        };
    }

    public record Filtered(SlotCollection slots, Predicate<ItemStack> filter) implements SlotCollection {
        @Override
        public Stream<ItemStack> itemCopies() {
            return this.slots.itemCopies().filter(this.filter);
        }

        @Override
        public SlotCollection filter(Predicate<ItemStack> filter) {
            return new SlotCollection.Filtered(this.slots, this.filter.and(filter));
        }
    }

    public record FlatMapped(SlotCollection slots, Function<ItemStack, ? extends SlotCollection> mapper) implements SlotCollection {
        @Override
        public Stream<ItemStack> itemCopies() {
            return this.slots.itemCopies().map(this.mapper).flatMap(SlotCollection::itemCopies);
        }
    }

    public record Limited(SlotCollection slots, int limit) implements SlotCollection {
        @Override
        public Stream<ItemStack> itemCopies() {
            return this.slots.itemCopies().limit(this.limit);
        }

        @Override
        public SlotCollection limit(int limit) {
            return new SlotCollection.Limited(this.slots, Math.min(this.limit, limit));
        }
    }
}
