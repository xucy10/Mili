package net.minecraft.core.component;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

public interface DataComponentMap extends Iterable<TypedDataComponent<?>>, DataComponentGetter {
    DataComponentMap EMPTY = new DataComponentMap() {
        @Override
        public <T> @Nullable T get(DataComponentType<? extends T> component) {
            return null;
        }

        @Override
        public Set<DataComponentType<?>> keySet() {
            return Set.of();
        }

        @Override
        public Iterator<TypedDataComponent<?>> iterator() {
            return Collections.emptyIterator();
        }
    };
    Codec<DataComponentMap> CODEC = makeCodecFromMap(DataComponentType.VALUE_MAP_CODEC);

    static Codec<DataComponentMap> makeCodec(Codec<DataComponentType<?>> codec) {
        return makeCodecFromMap(Codec.dispatchedMap(codec, DataComponentType::codecOrThrow));
    }

    static Codec<DataComponentMap> makeCodecFromMap(Codec<Map<DataComponentType<?>, Object>> codec) {
        return codec.flatComapMap(DataComponentMap.Builder::buildFromMapTrusted, map -> {
            int size = map.size();
            if (size == 0) {
                return DataResult.success(Reference2ObjectMaps.emptyMap());
            } else {
                Reference2ObjectMap<DataComponentType<?>, Object> map1 = new Reference2ObjectArrayMap<>(size);

                for (TypedDataComponent<?> typedDataComponent : map) {
                    if (!typedDataComponent.type().isTransient()) {
                        map1.put(typedDataComponent.type(), typedDataComponent.value());
                    }
                }

                return DataResult.success(map1);
            }
        });
    }

    static DataComponentMap composite(final DataComponentMap map1, final DataComponentMap map2) {
        return new DataComponentMap() {
            @Override
            public <T> @Nullable T get(DataComponentType<? extends T> component) {
                T object = map2.get(component);
                return object != null ? object : map1.get(component);
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                return Sets.union(map1.keySet(), map2.keySet());
            }
        };
    }

    static DataComponentMap.Builder builder() {
        return new DataComponentMap.Builder();
    }

    Set<DataComponentType<?>> keySet();

    default boolean has(DataComponentType<?> component) {
        return this.get(component) != null;
    }

    @Override
    default Iterator<TypedDataComponent<?>> iterator() {
        return Iterators.transform(
            this.keySet().iterator(), dataComponentType -> Objects.requireNonNull(this.getTyped((DataComponentType<?>)dataComponentType))
        );
    }

    default Stream<TypedDataComponent<?>> stream() {
        return StreamSupport.stream(Spliterators.spliterator(this.iterator(), (long)this.size(), 1345), false);
    }

    default int size() {
        return this.keySet().size();
    }

    default boolean isEmpty() {
        return this.size() == 0;
    }

    default DataComponentMap filter(final Predicate<DataComponentType<?>> predicate) {
        return new DataComponentMap() {
            @Override
            public <T> @Nullable T get(DataComponentType<? extends T> component) {
                return predicate.test(component) ? DataComponentMap.this.get(component) : null;
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                return Sets.filter(DataComponentMap.this.keySet(), predicate::test);
            }
        };
    }

    public static class Builder {
        private final Reference2ObjectMap<DataComponentType<?>, Object> map = new Reference2ObjectArrayMap<>();

        Builder() {
        }

        public <T> DataComponentMap.Builder set(DataComponentType<T> component, @Nullable T value) {
            this.setUnchecked(component, value);
            return this;
        }

        <T> void setUnchecked(DataComponentType<T> component, @Nullable Object value) {
            if (value != null) {
                this.map.put(component, value);
            } else {
                this.map.remove(component);
            }
        }

        public DataComponentMap.Builder addAll(DataComponentMap components) {
            for (TypedDataComponent<?> typedDataComponent : components) {
                this.map.put(typedDataComponent.type(), typedDataComponent.value());
            }

            return this;
        }

        public DataComponentMap build() {
            return buildFromMapTrusted(this.map);
        }

        private static DataComponentMap buildFromMapTrusted(Map<DataComponentType<?>, Object> map) {
            if (map.isEmpty()) {
                return DataComponentMap.EMPTY;
            } else {
                return map.size() < 8
                    ? new DataComponentMap.Builder.SimpleMap(new Reference2ObjectArrayMap<>(map))
                    : new DataComponentMap.Builder.SimpleMap(new Reference2ObjectOpenHashMap<>(map));
            }
        }

        record SimpleMap(Reference2ObjectMap<DataComponentType<?>, Object> map) implements DataComponentMap {
            @Override
            public <T> @Nullable T get(DataComponentType<? extends T> component) {
                return (T)this.map.get(component);
            }

            @Override
            public boolean has(DataComponentType<?> component) {
                return this.map.containsKey(component);
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                return this.map.keySet();
            }

            @Override
            public Iterator<TypedDataComponent<?>> iterator() {
                return Iterators.transform(Reference2ObjectMaps.fastIterator(this.map), TypedDataComponent::fromEntryUnchecked);
            }

            @Override
            public int size() {
                return this.map.size();
            }

            @Override
            public String toString() {
                return this.map.toString();
            }
        }
    }
}
