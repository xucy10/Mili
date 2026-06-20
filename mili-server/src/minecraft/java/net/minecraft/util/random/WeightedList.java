package net.minecraft.util.random;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

public class WeightedList<E> { // Paper - non-final
    private static final int FLAT_THRESHOLD = 64;
    private final int totalWeight;
    private final List<Weighted<E>> items;
    private final WeightedList.@Nullable Selector<E> selector;

    protected WeightedList(List<? extends Weighted<E>> items) { // Paper - protected
        this.items = List.copyOf(items);
        this.totalWeight = WeightedRandom.getTotalWeight(items, Weighted::weight);
        if (this.totalWeight == 0) {
            this.selector = null;
        } else if (this.totalWeight < 64) {
            this.selector = new WeightedList.Flat<>(this.items, this.totalWeight);
        } else {
            this.selector = new WeightedList.Compact<>(this.items);
        }
    }

    public static <E> WeightedList<E> of() {
        return new WeightedList<>(List.of());
    }

    public static <E> WeightedList<E> of(E element) {
        return new WeightedList<>(List.of(new Weighted<>(element, 1)));
    }

    @SafeVarargs
    public static <E> WeightedList<E> of(Weighted<E>... items) {
        return new WeightedList<>(List.of(items));
    }

    public static <E> WeightedList<E> of(List<Weighted<E>> items) {
        return new WeightedList<>(items);
    }

    public static <E> WeightedList.Builder<E> builder() {
        return new WeightedList.Builder<>();
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    public <T> WeightedList<T> map(Function<E, T> mapper) {
        return new WeightedList(Lists.transform(this.items, weighted -> weighted.map((Function<E, E>)mapper)));
    }

    public Optional<E> getRandom(RandomSource random) {
        if (this.selector == null) {
            return Optional.empty();
        } else {
            int randomInt = random.nextInt(this.totalWeight);
            return Optional.of(this.selector.get(randomInt));
        }
    }

    public E getRandomOrThrow(RandomSource random) {
        if (this.selector == null) {
            throw new IllegalStateException("Weighted list has no elements");
        } else {
            int randomInt = random.nextInt(this.totalWeight);
            return this.selector.get(randomInt);
        }
    }

    public List<Weighted<E>> unwrap() {
        return this.items;
    }

    public static <E> Codec<WeightedList<E>> codec(Codec<E> elementCodec) {
        return Weighted.codec(elementCodec).listOf().xmap(WeightedList::of, WeightedList::unwrap);
    }

    public static <E> Codec<WeightedList<E>> codec(MapCodec<E> elementCodec) {
        return Weighted.codec(elementCodec).listOf().xmap(WeightedList::of, WeightedList::unwrap);
    }

    public static <E> Codec<WeightedList<E>> nonEmptyCodec(Codec<E> elementCodec) {
        return ExtraCodecs.nonEmptyList(Weighted.codec(elementCodec).listOf()).xmap(WeightedList::of, WeightedList::unwrap);
    }

    public static <E> Codec<WeightedList<E>> nonEmptyCodec(MapCodec<E> elementCodec) {
        return ExtraCodecs.nonEmptyList(Weighted.codec(elementCodec).listOf()).xmap(WeightedList::of, WeightedList::unwrap);
    }

    public static <E, B extends ByteBuf> StreamCodec<B, WeightedList<E>> streamCodec(StreamCodec<B, E> elementCodec) {
        return Weighted.streamCodec(elementCodec).apply(ByteBufCodecs.list()).map(WeightedList::of, WeightedList::unwrap);
    }

    public boolean contains(E element) {
        for (Weighted<E> weighted : this.items) {
            if (weighted.value().equals(element)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other
            || other instanceof WeightedList<?> weightedList && this.totalWeight == weightedList.totalWeight && Objects.equals(this.items, weightedList.items);
    }

    @Override
    public int hashCode() {
        int i = this.totalWeight;
        return 31 * i + this.items.hashCode();
    }

    public static class Builder<E> {
        protected final ImmutableList.Builder<Weighted<E>> result = ImmutableList.builder();

        public WeightedList.Builder<E> add(E element) {
            return this.add(element, 1);
        }

        public WeightedList.Builder<E> add(E element, int weight) {
            this.result.add(new Weighted<>(element, weight));
            return this;
        }

        public WeightedList<E> build() {
            return new WeightedList<>(this.result.build());
        }
    }

    static class Compact<E> implements WeightedList.Selector<E> {
        private final Weighted<?>[] entries;

        Compact(List<Weighted<E>> entries) {
            this.entries = entries.toArray(Weighted[]::new);
        }

        @Override
        public E get(int index) {
            for (Weighted<?> weighted : this.entries) {
                index -= weighted.weight();
                if (index < 0) {
                    return (E)weighted.value();
                }
            }

            throw new IllegalStateException(index + " exceeded total weight");
        }
    }

    static class Flat<E> implements WeightedList.Selector<E> {
        private final Object[] entries;

        Flat(List<Weighted<E>> entries, int size) {
            this.entries = new Object[size];
            int i = 0;

            for (Weighted<E> weighted : entries) {
                int weight = weighted.weight();
                Arrays.fill(this.entries, i, i + weight, weighted.value());
                i += weight;
            }
        }

        @Override
        public E get(int index) {
            return (E)this.entries[index];
        }
    }

    interface Selector<E> {
        E get(int index);
    }
}
