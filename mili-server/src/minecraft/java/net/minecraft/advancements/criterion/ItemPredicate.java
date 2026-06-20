package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public record ItemPredicate(Optional<HolderSet<Item>> items, MinMaxBounds.Ints count, DataComponentMatchers components) implements Predicate<ItemStack> {
    public static final Codec<ItemPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("items").forGetter(ItemPredicate::items),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("count", MinMaxBounds.Ints.ANY).forGetter(ItemPredicate::count),
                DataComponentMatchers.CODEC.forGetter(ItemPredicate::components)
            )
            .apply(instance, ItemPredicate::new)
    );

    @Override
    public boolean test(ItemStack stack) {
        return (!this.items.isPresent() || stack.is(this.items.get()))
            && this.count.matches(stack.getCount())
            && this.components.test((DataComponentGetter)stack);
    }

    public static class Builder {
        private Optional<HolderSet<Item>> items = Optional.empty();
        private MinMaxBounds.Ints count = MinMaxBounds.Ints.ANY;
        private DataComponentMatchers components = DataComponentMatchers.ANY;

        public static ItemPredicate.Builder item() {
            return new ItemPredicate.Builder();
        }

        public ItemPredicate.Builder of(HolderGetter<Item> itemRegistry, ItemLike... items) {
            this.items = Optional.of(HolderSet.direct(item -> item.asItem().builtInRegistryHolder(), items));
            return this;
        }

        public ItemPredicate.Builder of(HolderGetter<Item> itemRegistry, TagKey<Item> tag) {
            this.items = Optional.of(itemRegistry.getOrThrow(tag));
            return this;
        }

        public ItemPredicate.Builder withCount(MinMaxBounds.Ints count) {
            this.count = count;
            return this;
        }

        public ItemPredicate.Builder withComponents(DataComponentMatchers components) {
            this.components = components;
            return this;
        }

        public ItemPredicate build() {
            return new ItemPredicate(this.items, this.count, this.components);
        }
    }
}
