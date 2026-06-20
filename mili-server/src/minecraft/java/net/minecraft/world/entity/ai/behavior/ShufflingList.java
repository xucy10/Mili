package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.util.RandomSource;

public class ShufflingList<U> implements Iterable<U> {
    protected final List<ShufflingList.WeightedEntry<U>> entries;
    private final RandomSource random = RandomSource.create();
    private final boolean isUnsafe; // Paper - Fix Concurrency issue in ShufflingList during worldgen

    public ShufflingList() {
        // Paper start - Fix Concurrency issue in ShufflingList during worldgen
        this(true);
    }
    public ShufflingList(boolean isUnsafe) {
        this.isUnsafe = isUnsafe;
        // Paper end - Fix Concurrency issue in ShufflingList during worldgen
        this.entries = Lists.newArrayList();
    }

    private ShufflingList(List<ShufflingList.WeightedEntry<U>> entries) {
        // Paper start - Fix Concurrency issue in ShufflingList during worldgen
        this(entries, true);
    }
    private ShufflingList(List<ShufflingList.WeightedEntry<U>> entries, boolean isUnsafe) {
        this.isUnsafe = isUnsafe;
        // Paper end - Fix Concurrency issue in ShufflingList during worldgen
        this.entries = Lists.newArrayList(entries);
    }

    public static <U> Codec<ShufflingList<U>> codec(Codec<U> codec) {
        return ShufflingList.WeightedEntry.codec(codec).listOf().xmap(ShufflingList::new, list -> list.entries);
    }

    public ShufflingList<U> add(U data, int weight) {
        this.entries.add(new ShufflingList.WeightedEntry<>(data, weight));
        return this;
    }

    public ShufflingList<U> shuffle() {
        // Paper start - Fix Concurrency issue in ShufflingList during worldgen
        List<ShufflingList.WeightedEntry<U>> list = this.isUnsafe ? Lists.newArrayList(this.entries) : this.entries;
        list.forEach(entry -> entry.setRandom(this.random.nextFloat()));
        list.sort(Comparator.comparingDouble(ShufflingList.WeightedEntry::getRandWeight));
        return this.isUnsafe ? new ShufflingList<>(list, this.isUnsafe) : this;
        // Paper end - Fix Concurrency issue in ShufflingList during worldgen
    }

    public Stream<U> stream() {
        return this.entries.stream().map(ShufflingList.WeightedEntry::getData);
    }

    @Override
    public Iterator<U> iterator() {
        return Iterators.transform(this.entries.iterator(), ShufflingList.WeightedEntry::getData);
    }

    @Override
    public String toString() {
        return "ShufflingList[" + this.entries + "]";
    }

    public static class WeightedEntry<T> {
        final T data;
        final int weight;
        private double randWeight;

        WeightedEntry(T data, int weight) {
            this.weight = weight;
            this.data = data;
        }

        private double getRandWeight() {
            return this.randWeight;
        }

        void setRandom(float chance) {
            this.randWeight = -Math.pow(chance, 1.0F / this.weight);
        }

        public T getData() {
            return this.data;
        }

        public int getWeight() {
            return this.weight;
        }

        @Override
        public String toString() {
            return this.weight + ":" + this.data;
        }

        public static <E> Codec<ShufflingList.WeightedEntry<E>> codec(final Codec<E> codec) {
            return new Codec<ShufflingList.WeightedEntry<E>>() {
                @Override
                public <T> DataResult<Pair<ShufflingList.WeightedEntry<E>, T>> decode(DynamicOps<T> ops, T input) {
                    Dynamic<T> dynamic = new Dynamic<>(ops, input);
                    return dynamic.get("data")
                        .flatMap(codec::parse)
                        .map(object -> new ShufflingList.WeightedEntry<>(object, dynamic.get("weight").asInt(1)))
                        .map(weightedEntry -> Pair.of((ShufflingList.WeightedEntry<E>)weightedEntry, ops.empty()));
                }

                @Override
                public <T> DataResult<T> encode(ShufflingList.WeightedEntry<E> input, DynamicOps<T> ops, T prefix) {
                    return ops.mapBuilder().add("weight", ops.createInt(input.weight)).add("data", codec.encodeStart(ops, input.data)).build(prefix);
                }
            };
        }
    }
}
