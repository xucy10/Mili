package net.minecraft.world.attribute;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import org.jspecify.annotations.Nullable;

public final class EnvironmentAttributeMap {
    public static final EnvironmentAttributeMap EMPTY = new EnvironmentAttributeMap(Map.of());
    public static final Codec<EnvironmentAttributeMap> CODEC = Codec.lazyInitialized(
        () -> Codec.dispatchedMap(EnvironmentAttributes.CODEC, Util.memoize((java.util.function.Function<EnvironmentAttribute<?>, Codec<? extends Entry<?, ?>>>) EnvironmentAttributeMap.Entry::createCodec))
            .xmap(EnvironmentAttributeMap::new, environmentAttributeMap -> environmentAttributeMap.entries)
    );
    public static final Codec<EnvironmentAttributeMap> NETWORK_CODEC = CODEC.xmap(
        EnvironmentAttributeMap::filterSyncable, EnvironmentAttributeMap::filterSyncable
    );
    public static final Codec<EnvironmentAttributeMap> CODEC_ONLY_POSITIONAL = CODEC.validate(
        environmentAttributeMap -> {
            List<EnvironmentAttribute<?>> list = environmentAttributeMap.keySet()
                .stream()
                .filter(environmentAttribute -> !environmentAttribute.isPositional())
                .toList();
            return !list.isEmpty()
                ? DataResult.error(() -> "The following attributes cannot be positional: " + list)
                : DataResult.success(environmentAttributeMap);
        }
    );
    final Map<EnvironmentAttribute<?>, EnvironmentAttributeMap.Entry<?, ?>> entries;

    private static EnvironmentAttributeMap filterSyncable(EnvironmentAttributeMap attributes) {
        return new EnvironmentAttributeMap(Map.copyOf(Maps.filterKeys(attributes.entries, EnvironmentAttribute::isSyncable)));
    }

    EnvironmentAttributeMap(Map<EnvironmentAttribute<?>, EnvironmentAttributeMap.Entry<?, ?>> entries) {
        this.entries = entries;
    }

    public static EnvironmentAttributeMap.Builder builder() {
        return new EnvironmentAttributeMap.Builder();
    }

    public <Value> EnvironmentAttributeMap.@Nullable Entry<Value, ?> get(EnvironmentAttribute<Value> attribute) {
        return (EnvironmentAttributeMap.Entry<Value, ?>)this.entries.get(attribute);
    }

    public <Value> Value applyModifier(EnvironmentAttribute<Value> attribute, Value value) {
        EnvironmentAttributeMap.Entry<Value, ?> entry = this.get(attribute);
        return entry != null ? entry.applyModifier(value) : value;
    }

    public boolean contains(EnvironmentAttribute<?> attribute) {
        return this.entries.containsKey(attribute);
    }

    public Set<EnvironmentAttribute<?>> keySet() {
        return this.entries.keySet();
    }

    @Override
    public boolean equals(Object other) {
        return other == this || other instanceof EnvironmentAttributeMap environmentAttributeMap && this.entries.equals(environmentAttributeMap.entries);
    }

    @Override
    public int hashCode() {
        return this.entries.hashCode();
    }

    @Override
    public String toString() {
        return this.entries.toString();
    }

    public static class Builder {
        private final Map<EnvironmentAttribute<?>, EnvironmentAttributeMap.Entry<?, ?>> entries = new HashMap<>();

        Builder() {
        }

        public EnvironmentAttributeMap.Builder putAll(EnvironmentAttributeMap attributes) {
            this.entries.putAll(attributes.entries);
            return this;
        }

        public <Value, Parameter> EnvironmentAttributeMap.Builder modify(
            EnvironmentAttribute<Value> attribute, AttributeModifier<Value, Parameter> modifier, Parameter argument
        ) {
            attribute.type().checkAllowedModifier(modifier);
            this.entries.put(attribute, new EnvironmentAttributeMap.Entry<>(argument, modifier));
            return this;
        }

        public <Value> EnvironmentAttributeMap.Builder set(EnvironmentAttribute<Value> attribute, Value value) {
            return this.modify(attribute, AttributeModifier.override(), value);
        }

        public EnvironmentAttributeMap build() {
            return this.entries.isEmpty() ? EnvironmentAttributeMap.EMPTY : new EnvironmentAttributeMap(Map.copyOf(this.entries));
        }
    }

    public record Entry<Value, Argument>(Argument argument, AttributeModifier<Value, Argument> modifier) {
        private static <Value> Codec<EnvironmentAttributeMap.Entry<Value, ?>> createCodec(EnvironmentAttribute<Value> attribute) {
            Codec<EnvironmentAttributeMap.Entry<Value, ?>> codec = attribute.type()
                .modifierCodec()
                .dispatch(
                    "modifier",
                    EnvironmentAttributeMap.Entry::modifier,
                    Util.memoize(attributeModifier -> createFullCodec(attribute, (AttributeModifier<Value, ?>)attributeModifier))
                );
            return Codec.either(attribute.valueCodec(), codec)
                .xmap(
                    either -> either.map(object -> new EnvironmentAttributeMap.Entry<>(object, AttributeModifier.override()), entry -> entry),
                    entry -> entry.modifier == AttributeModifier.override()
                        ? Either.left((Value)entry.argument())
                        : Either.right((EnvironmentAttributeMap.Entry<Value, ?>)entry)
                );
        }

        private static <Value, Argument> MapCodec<EnvironmentAttributeMap.Entry<Value, Argument>> createFullCodec(
            EnvironmentAttribute<Value> attribute, AttributeModifier<Value, Argument> modifier
        ) {
            return RecordCodecBuilder.mapCodec(
                instance -> instance.group(modifier.argumentCodec(attribute).fieldOf("argument").forGetter(EnvironmentAttributeMap.Entry::argument))
                    .apply(instance, object -> new EnvironmentAttributeMap.Entry<>(object, modifier))
            );
        }

        public Value applyModifier(Value value) {
            return this.modifier.apply(value, this.argument);
        }
    }
}
