package net.minecraft.world.item.crafting.display;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.block.entity.FuelValues;

public interface SlotDisplay {
    Codec<SlotDisplay> CODEC = BuiltInRegistries.SLOT_DISPLAY.byNameCodec().dispatch(SlotDisplay::type, SlotDisplay.Type::codec);
    StreamCodec<RegistryFriendlyByteBuf, SlotDisplay> STREAM_CODEC = ByteBufCodecs.registry(Registries.SLOT_DISPLAY)
        .dispatch(SlotDisplay::type, SlotDisplay.Type::streamCodec);

    <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> output);

    SlotDisplay.Type<? extends SlotDisplay> type();

    default boolean isEnabled(FeatureFlagSet enabledFeatures) {
        return true;
    }

    default List<ItemStack> resolveForStacks(ContextMap context) {
        return this.resolve(context, SlotDisplay.ItemStackContentsFactory.INSTANCE).toList();
    }

    default ItemStack resolveForFirstStack(ContextMap context) {
        return this.resolve(context, SlotDisplay.ItemStackContentsFactory.INSTANCE).findFirst().orElse(ItemStack.EMPTY);
    }

    public static class AnyFuel implements SlotDisplay {
        public static final SlotDisplay.AnyFuel INSTANCE = new SlotDisplay.AnyFuel();
        public static final MapCodec<SlotDisplay.AnyFuel> MAP_CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.AnyFuel> STREAM_CODEC = StreamCodec.unit(INSTANCE);
        public static final SlotDisplay.Type<SlotDisplay.AnyFuel> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

        private AnyFuel() {
        }

        @Override
        public SlotDisplay.Type<SlotDisplay.AnyFuel> type() {
            return TYPE;
        }

        @Override
        public String toString() {
            return "<any fuel>";
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> output) {
            if (output instanceof DisplayContentsFactory.ForStacks<T> forStacks) {
                FuelValues fuelValues = context.getOptional(SlotDisplayContext.FUEL_VALUES);
                if (fuelValues != null) {
                    return fuelValues.fuelItems().stream().map(forStacks::forStack);
                }
            }

            return Stream.empty();
        }
    }

    public record Composite(List<SlotDisplay> contents) implements SlotDisplay {
        public static final MapCodec<SlotDisplay.Composite> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(SlotDisplay.CODEC.listOf().fieldOf("contents").forGetter(SlotDisplay.Composite::contents))
                .apply(instance, SlotDisplay.Composite::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.Composite> STREAM_CODEC = StreamCodec.composite(
            SlotDisplay.STREAM_CODEC.apply(ByteBufCodecs.list()), SlotDisplay.Composite::contents, SlotDisplay.Composite::new
        );
        public static final SlotDisplay.Type<SlotDisplay.Composite> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

        @Override
        public SlotDisplay.Type<SlotDisplay.Composite> type() {
            return TYPE;
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> output) {
            return this.contents.stream().flatMap(slotDisplay -> slotDisplay.resolve(context, output));
        }

        @Override
        public boolean isEnabled(FeatureFlagSet enabledFeatures) {
            return this.contents.stream().allMatch(display -> display.isEnabled(enabledFeatures));
        }
    }

    public static class Empty implements SlotDisplay {
        public static final SlotDisplay.Empty INSTANCE = new SlotDisplay.Empty();
        public static final MapCodec<SlotDisplay.Empty> MAP_CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.Empty> STREAM_CODEC = StreamCodec.unit(INSTANCE);
        public static final SlotDisplay.Type<SlotDisplay.Empty> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

        private Empty() {
        }

        @Override
        public SlotDisplay.Type<SlotDisplay.Empty> type() {
            return TYPE;
        }

        @Override
        public String toString() {
            return "<empty>";
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> output) {
            return Stream.empty();
        }
    }

    public record ItemSlotDisplay(Holder<Item> item) implements SlotDisplay {
        public static final MapCodec<SlotDisplay.ItemSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(Item.CODEC.fieldOf("item").forGetter(SlotDisplay.ItemSlotDisplay::item))
                .apply(instance, SlotDisplay.ItemSlotDisplay::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.ItemSlotDisplay> STREAM_CODEC = StreamCodec.composite(
            Item.STREAM_CODEC, SlotDisplay.ItemSlotDisplay::item, SlotDisplay.ItemSlotDisplay::new
        );
        public static final SlotDisplay.Type<SlotDisplay.ItemSlotDisplay> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

        public ItemSlotDisplay(Item item) {
            this(item.builtInRegistryHolder());
        }

        @Override
        public SlotDisplay.Type<SlotDisplay.ItemSlotDisplay> type() {
            return TYPE;
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> output) {
            return output instanceof DisplayContentsFactory.ForStacks<T> forStacks ? Stream.of(forStacks.forStack(this.item)) : Stream.empty();
        }

        @Override
        public boolean isEnabled(FeatureFlagSet enabledFeatures) {
            return this.item.value().isEnabled(enabledFeatures);
        }
    }

    public static class ItemStackContentsFactory implements DisplayContentsFactory.ForStacks<ItemStack> {
        public static final SlotDisplay.ItemStackContentsFactory INSTANCE = new SlotDisplay.ItemStackContentsFactory();

        @Override
        public ItemStack forStack(ItemStack stack) {
            return stack;
        }
    }

    public record ItemStackSlotDisplay(ItemStack stack) implements SlotDisplay {
        public static final MapCodec<SlotDisplay.ItemStackSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(ItemStack.STRICT_CODEC.fieldOf("item").forGetter(SlotDisplay.ItemStackSlotDisplay::stack))
                .apply(instance, SlotDisplay.ItemStackSlotDisplay::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.ItemStackSlotDisplay> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, SlotDisplay.ItemStackSlotDisplay::stack, SlotDisplay.ItemStackSlotDisplay::new
        );
        public static final SlotDisplay.Type<SlotDisplay.ItemStackSlotDisplay> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

        @Override
        public SlotDisplay.Type<SlotDisplay.ItemStackSlotDisplay> type() {
            return TYPE;
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> output) {
            return output instanceof DisplayContentsFactory.ForStacks<T> forStacks ? Stream.of(forStacks.forStack(this.stack)) : Stream.empty();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                || other instanceof SlotDisplay.ItemStackSlotDisplay itemStackSlotDisplay && ItemStack.matches(this.stack, itemStackSlotDisplay.stack);
        }

        @Override
        public boolean isEnabled(FeatureFlagSet enabledFeatures) {
            return this.stack.getItem().isEnabled(enabledFeatures);
        }
    }

    public record SmithingTrimDemoSlotDisplay(SlotDisplay base, SlotDisplay material, Holder<TrimPattern> pattern) implements SlotDisplay {
        public static final MapCodec<SlotDisplay.SmithingTrimDemoSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    SlotDisplay.CODEC.fieldOf("base").forGetter(SlotDisplay.SmithingTrimDemoSlotDisplay::base),
                    SlotDisplay.CODEC.fieldOf("material").forGetter(SlotDisplay.SmithingTrimDemoSlotDisplay::material),
                    TrimPattern.CODEC.fieldOf("pattern").forGetter(SlotDisplay.SmithingTrimDemoSlotDisplay::pattern)
                )
                .apply(instance, SlotDisplay.SmithingTrimDemoSlotDisplay::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.SmithingTrimDemoSlotDisplay> STREAM_CODEC = StreamCodec.composite(
            SlotDisplay.STREAM_CODEC,
            SlotDisplay.SmithingTrimDemoSlotDisplay::base,
            SlotDisplay.STREAM_CODEC,
            SlotDisplay.SmithingTrimDemoSlotDisplay::material,
            TrimPattern.STREAM_CODEC,
            SlotDisplay.SmithingTrimDemoSlotDisplay::pattern,
            SlotDisplay.SmithingTrimDemoSlotDisplay::new
        );
        public static final SlotDisplay.Type<SlotDisplay.SmithingTrimDemoSlotDisplay> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

        @Override
        public SlotDisplay.Type<SlotDisplay.SmithingTrimDemoSlotDisplay> type() {
            return TYPE;
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> output) {
            if (output instanceof DisplayContentsFactory.ForStacks<T> forStacks) {
                HolderLookup.Provider provider = context.getOptional(SlotDisplayContext.REGISTRIES);
                if (provider != null) {
                    RandomSource randomSource = RandomSource.create(System.identityHashCode(this));
                    List<ItemStack> list = this.base.resolveForStacks(context);
                    if (list.isEmpty()) {
                        return Stream.empty();
                    }

                    List<ItemStack> list1 = this.material.resolveForStacks(context);
                    if (list1.isEmpty()) {
                        return Stream.empty();
                    }

                    return Stream.<ItemStack>generate(() -> {
                        ItemStack itemStack = Util.getRandom(list, randomSource);
                        ItemStack itemStack1 = Util.getRandom(list1, randomSource);
                        return SmithingTrimRecipe.applyTrim(provider, itemStack, itemStack1, this.pattern);
                    }).limit(256L).filter(stack -> !stack.isEmpty()).limit(16L).map(forStacks::forStack);
                }
            }

            return Stream.empty();
        }
    }

    public record TagSlotDisplay(TagKey<Item> tag) implements SlotDisplay {
        public static final MapCodec<SlotDisplay.TagSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(SlotDisplay.TagSlotDisplay::tag))
                .apply(instance, SlotDisplay.TagSlotDisplay::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.TagSlotDisplay> STREAM_CODEC = StreamCodec.composite(
            TagKey.streamCodec(Registries.ITEM), SlotDisplay.TagSlotDisplay::tag, SlotDisplay.TagSlotDisplay::new
        );
        public static final SlotDisplay.Type<SlotDisplay.TagSlotDisplay> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

        @Override
        public SlotDisplay.Type<SlotDisplay.TagSlotDisplay> type() {
            return TYPE;
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> output) {
            if (output instanceof DisplayContentsFactory.ForStacks<T> forStacks) {
                HolderLookup.Provider provider = context.getOptional(SlotDisplayContext.REGISTRIES);
                if (provider != null) {
                    return provider.lookupOrThrow(Registries.ITEM)
                        .get(this.tag)
                        .map(named -> named.stream().map(forStacks::forStack))
                        .stream()
                        .flatMap(stream -> stream);
                }
            }

            return Stream.empty();
        }
    }

    public record Type<T extends SlotDisplay>(MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) {
    }

    public record WithRemainder(SlotDisplay input, SlotDisplay remainder) implements SlotDisplay {
        public static final MapCodec<SlotDisplay.WithRemainder> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    SlotDisplay.CODEC.fieldOf("input").forGetter(SlotDisplay.WithRemainder::input),
                    SlotDisplay.CODEC.fieldOf("remainder").forGetter(SlotDisplay.WithRemainder::remainder)
                )
                .apply(instance, SlotDisplay.WithRemainder::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.WithRemainder> STREAM_CODEC = StreamCodec.composite(
            SlotDisplay.STREAM_CODEC,
            SlotDisplay.WithRemainder::input,
            SlotDisplay.STREAM_CODEC,
            SlotDisplay.WithRemainder::remainder,
            SlotDisplay.WithRemainder::new
        );
        public static final SlotDisplay.Type<SlotDisplay.WithRemainder> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

        @Override
        public SlotDisplay.Type<SlotDisplay.WithRemainder> type() {
            return TYPE;
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> output) {
            if (output instanceof DisplayContentsFactory.ForRemainders<T> forRemainders) {
                List<T> list = this.remainder.resolve(context, output).toList();
                return this.input.resolve(context, output).map(object -> forRemainders.addRemainder((T)object, list));
            } else {
                return this.input.resolve(context, output);
            }
        }

        @Override
        public boolean isEnabled(FeatureFlagSet enabledFeatures) {
            return this.input.isEnabled(enabledFeatures) && this.remainder.isEnabled(enabledFeatures);
        }
    }
}
