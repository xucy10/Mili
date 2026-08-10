package net.minecraft.core.component;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import org.jspecify.annotations.Nullable;

public final class DataComponentPatch {
    public static final DataComponentPatch EMPTY = new DataComponentPatch(Reference2ObjectMaps.emptyMap());
    public static final Codec<DataComponentPatch> CODEC = Codec.<PatchKey, Object>dispatchedMap(DataComponentPatch.PatchKey.CODEC, DataComponentPatch.PatchKey::valueCodec)
        .xmap(map -> {
            if (map.isEmpty()) {
                return EMPTY;
            } else {
                Reference2ObjectMap<DataComponentType<?>, Optional<?>> map1 = new Reference2ObjectArrayMap<>(map.size());

                for (Entry<DataComponentPatch.PatchKey, ?> entry : map.entrySet()) {
                    DataComponentPatch.PatchKey patchKey = entry.getKey();
                    if (patchKey.removed()) {
                        map1.put(patchKey.type(), Optional.empty());
                    } else {
                        map1.put(patchKey.type(), Optional.of(entry.getValue()));
                    }
                }

                return new DataComponentPatch(map1);
            }
        }, dataComponentPatch -> {
            Reference2ObjectMap<DataComponentPatch.PatchKey, Object> map = new Reference2ObjectArrayMap<>(dataComponentPatch.map.size());

            for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(dataComponentPatch.map)) {
                DataComponentType<?> dataComponentType = entry.getKey();
                if (!dataComponentType.isTransient()) {
                    Optional<?> optional = entry.getValue();
                    if (optional.isPresent()) {
                        map.put(new DataComponentPatch.PatchKey(dataComponentType, false), optional.get());
                    } else {
                        map.put(new DataComponentPatch.PatchKey(dataComponentType, true), Unit.INSTANCE);
                    }
                }
            }

            return map;
        });
    public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> STREAM_CODEC = createStreamCodec(new DataComponentPatch.CodecGetter() {
        @Override
        public <T> StreamCodec<RegistryFriendlyByteBuf, T> apply(DataComponentType<T> component) {
            return component.streamCodec().cast();
        }
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> DELIMITED_STREAM_CODEC = createStreamCodec(
        new DataComponentPatch.CodecGetter() {
            @Override
            public <T> StreamCodec<RegistryFriendlyByteBuf, T> apply(DataComponentType<T> component) {
                StreamCodec<RegistryFriendlyByteBuf, T> streamCodec = component.streamCodec().cast();
                return streamCodec.apply(ByteBufCodecs.registryFriendlyLengthPrefixed(Integer.MAX_VALUE));
            }
        }
    );
    private static final String REMOVED_PREFIX = "!";
    final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map;

    private static StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> createStreamCodec(final DataComponentPatch.CodecGetter codecGetter) {
        return new StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch>() {
            @Override
            public DataComponentPatch decode(RegistryFriendlyByteBuf buffer) {
                int varInt = buffer.readVarInt();
                int varInt1 = buffer.readVarInt();
                if (varInt == 0 && varInt1 == 0) {
                    return DataComponentPatch.EMPTY;
                } else {
                    int i = varInt + varInt1;
                    Reference2ObjectMap<DataComponentType<?>, Optional<?>> map = new Reference2ObjectArrayMap<>(Math.min(i, 65536));

                    for (int i1 = 0; i1 < varInt; i1++) {
                        DataComponentType<?> dataComponentType = DataComponentType.STREAM_CODEC.decode(buffer);
                        Object object = codecGetter.apply(dataComponentType).decode(buffer);
                        map.put(dataComponentType, Optional.of(object));
                    }

                    for (int i1 = 0; i1 < varInt1; i1++) {
                        DataComponentType<?> dataComponentType = DataComponentType.STREAM_CODEC.decode(buffer);
                        map.put(dataComponentType, Optional.empty());
                    }

                    return new DataComponentPatch(map);
                }
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, DataComponentPatch value) {
                if (value.isEmpty()) {
                    buffer.writeVarInt(0);
                    buffer.writeVarInt(0);
                } else {
                    // Paper start - data sanitization for items
                    final io.papermc.paper.util.sanitizer.ItemObfuscationSession itemObfuscationSession = value.map.isEmpty()
                        ? null // Avoid thread local lookup of current session if it won't be needed anyway.
                        : io.papermc.paper.util.sanitizer.ItemObfuscationSession.currentSession();
                    // Paper end - data sanitization for items
                    int i = 0;
                    int i1 = 0;

                    for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(
                        value.map
                    )) {
                        if (entry.getValue().isPresent()) {
                            if (!io.papermc.paper.util.sanitizer.ItemComponentSanitizer.shouldDrop(itemObfuscationSession, entry.getKey())) i++; // Paper - data sanitization for items
                        } else {
                            i1++;
                        }
                    }

                    buffer.writeVarInt(i);
                    buffer.writeVarInt(i1);

                    for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entryx : Reference2ObjectMaps.fastIterable(
                        value.map
                    )) {
                        Optional<?> optional = entryx.getValue();
                        optional = io.papermc.paper.util.sanitizer.ItemComponentSanitizer.override(itemObfuscationSession, entryx.getKey(), entryx.getValue()); // Paper - data sanitization for items
                        if (optional.isPresent()) {
                            DataComponentType<?> dataComponentType = entryx.getKey();
                            DataComponentType.STREAM_CODEC.encode(buffer, dataComponentType);
                            this.encodeComponent(buffer, dataComponentType, optional.get());
                        }
                    }

                    for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entryxx : Reference2ObjectMaps.fastIterable(
                        value.map
                    )) {
                        if (entryxx.getValue().isEmpty()) {
                            DataComponentType<?> dataComponentType1 = entryxx.getKey();
                            DataComponentType.STREAM_CODEC.encode(buffer, dataComponentType1);
                        }
                    }
                }
            }

            private <T> void encodeComponent(RegistryFriendlyByteBuf buffer, DataComponentType<T> component, Object value) {
                // Paper start - codec errors of random anonymous classes are useless
                try {
                    codecGetter.apply(component).encode(buffer, (T)value);
                } catch (final Exception e) {
                    throw new RuntimeException("Error encoding component " + component, e);
                }
                // Paper end - codec errors of random anonymous classes are useless
            }
        };
    }

    DataComponentPatch(Reference2ObjectMap<DataComponentType<?>, Optional<?>> map) {
        this.map = map;
    }

    public static DataComponentPatch.Builder builder() {
        return new DataComponentPatch.Builder();
    }

    public <T> @Nullable Optional<? extends T> get(DataComponentType<? extends T> component) {
        return (Optional<? extends T>)this.map.get(component);
    }

    public Set<Entry<DataComponentType<?>, Optional<?>>> entrySet() {
        return this.map.entrySet();
    }

    public int size() {
        return this.map.size();
    }

    public DataComponentPatch forget(Predicate<DataComponentType<?>> predicate) {
        if (this.isEmpty()) {
            return EMPTY;
        } else {
            Reference2ObjectMap<DataComponentType<?>, Optional<?>> map = new Reference2ObjectArrayMap<>(this.map);
            map.keySet().removeIf(predicate);
            return map.isEmpty() ? EMPTY : new DataComponentPatch(map);
        }
    }

    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    public DataComponentPatch.SplitResult split() {
        if (this.isEmpty()) {
            return DataComponentPatch.SplitResult.EMPTY;
        } else {
            DataComponentMap.Builder builder = DataComponentMap.builder();
            Set<DataComponentType<?>> set = Sets.newIdentityHashSet();
            this.map.forEach((component, value) -> {
                if (value.isPresent()) {
                    builder.setUnchecked((DataComponentType<?>)component, value.get());
                } else {
                    set.add((DataComponentType<?>)component);
                }
            });
            return new DataComponentPatch.SplitResult(builder.build(), set);
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof DataComponentPatch dataComponentPatch && this.map.equals(dataComponentPatch.map);
    }

    @Override
    public int hashCode() {
        return this.map.hashCode();
    }

    @Override
    public String toString() {
        return toString(this.map);
    }

    static String toString(Reference2ObjectMap<DataComponentType<?>, Optional<?>> map) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append('{');
        boolean flag = true;

        for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(map)) {
            if (flag) {
                flag = false;
            } else {
                stringBuilder.append(", ");
            }

            Optional<?> optional = entry.getValue();
            if (optional.isPresent()) {
                stringBuilder.append(entry.getKey());
                stringBuilder.append("=>");
                stringBuilder.append(optional.get());
            } else {
                stringBuilder.append("!");
                stringBuilder.append(entry.getKey());
            }
        }

        stringBuilder.append('}');
        return stringBuilder.toString();
    }

    public static class Builder {
        private final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map = new Reference2ObjectArrayMap<>();

        Builder() {
        }

        // CraftBukkit start
        public void copy(DataComponentPatch orig) {
            this.map.putAll(orig.map);
        }

        public void clear(DataComponentType<?> type) {
            this.map.remove(type);
        }

        public boolean isSet(DataComponentType<?> type) {
            return this.map.containsKey(type);
        }

        public boolean isEmpty() {
            return this.map.isEmpty();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (object instanceof DataComponentPatch.Builder patch) {
                return this.map.equals(patch.map);
            }

            return false;
        }

        @Override
        public int hashCode() {
            return this.map.hashCode();
        }
        // CraftBukkit end

        public <T> DataComponentPatch.Builder set(DataComponentType<T> component, T value) {
            this.map.put(component, Optional.of(value));
            return this;
        }

        public <T> DataComponentPatch.Builder remove(DataComponentType<T> component) {
            this.map.put(component, Optional.empty());
            return this;
        }

        public <T> DataComponentPatch.Builder set(TypedDataComponent<T> component) {
            return this.set(component.type(), component.value());
        }

        public DataComponentPatch build() {
            return this.map.isEmpty() ? DataComponentPatch.EMPTY : new DataComponentPatch(this.map);
        }
    }

    @FunctionalInterface
    interface CodecGetter {
        <T> StreamCodec<? super RegistryFriendlyByteBuf, T> apply(DataComponentType<T> component);
    }

    record PatchKey(DataComponentType<?> type, boolean removed) {
        public static final Codec<DataComponentPatch.PatchKey> CODEC = Codec.STRING
            .flatXmap(
                str -> {
                    boolean flag = str.startsWith("!");
                    if (flag) {
                        str = str.substring("!".length());
                    }

                    Identifier identifier = Identifier.tryParse(str);
                    DataComponentType<?> dataComponentType = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(identifier);
                    if (dataComponentType == null) {
                        return DataResult.error(() -> "No component with type: '" + identifier + "'");
                    } else {
                        return dataComponentType.isTransient()
                            ? DataResult.error(() -> "'" + identifier + "' is not a persistent component")
                            : DataResult.success(new DataComponentPatch.PatchKey(dataComponentType, flag));
                    }
                },
                key -> {
                    DataComponentType<?> dataComponentType = key.type();
                    Identifier key1 = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(dataComponentType);
                    return key1 == null
                        ? DataResult.error(() -> "Unregistered component: " + dataComponentType)
                        : DataResult.success(key.removed() ? "!" + key1 : key1.toString());
                }
            );

        public Codec<?> valueCodec() {
            return this.removed ? Codec.EMPTY.codec() : this.type.codecOrThrow();
        }
    }

    public record SplitResult(DataComponentMap added, Set<DataComponentType<?>> removed) {
        public static final DataComponentPatch.SplitResult EMPTY = new DataComponentPatch.SplitResult(DataComponentMap.EMPTY, Set.of());
    }
}
