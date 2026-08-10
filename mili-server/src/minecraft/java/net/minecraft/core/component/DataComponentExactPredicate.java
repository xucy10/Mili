package net.minecraft.core.component;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public final class DataComponentExactPredicate implements Predicate<DataComponentGetter> {
    public static final Codec<DataComponentExactPredicate> CODEC = DataComponentType.VALUE_MAP_CODEC
        .xmap(
            map -> new DataComponentExactPredicate(map.entrySet().stream().map(TypedDataComponent::fromEntryUnchecked).collect(Collectors.toList())),
            dataComponentExactPredicate -> dataComponentExactPredicate.expectedComponents
                .stream()
                .filter(typedDataComponent -> !typedDataComponent.type().isTransient())
                .collect(Collectors.toMap(TypedDataComponent::type, TypedDataComponent::value))
        );
    public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentExactPredicate> STREAM_CODEC = TypedDataComponent.STREAM_CODEC
        .apply(ByteBufCodecs.list())
        .map(DataComponentExactPredicate::new, dataComponentExactPredicate -> dataComponentExactPredicate.expectedComponents);
    public static final DataComponentExactPredicate EMPTY = new DataComponentExactPredicate(List.of());
    private final List<TypedDataComponent<?>> expectedComponents;

    DataComponentExactPredicate(List<TypedDataComponent<?>> expectedComponents) {
        this.expectedComponents = expectedComponents;
    }

    public static DataComponentExactPredicate.Builder builder() {
        return new DataComponentExactPredicate.Builder();
    }

    public static <T> DataComponentExactPredicate expect(DataComponentType<T> component, T value) {
        return new DataComponentExactPredicate(List.of(new TypedDataComponent<>(component, value)));
    }

    public static DataComponentExactPredicate allOf(DataComponentMap map) {
        return new DataComponentExactPredicate(ImmutableList.copyOf(map));
    }

    public static DataComponentExactPredicate someOf(DataComponentMap map, DataComponentType<?>... types) {
        DataComponentExactPredicate.Builder builder = new DataComponentExactPredicate.Builder();

        for (DataComponentType<?> dataComponentType : types) {
            TypedDataComponent<?> typed = map.getTyped(dataComponentType);
            if (typed != null) {
                builder.expect(typed);
            }
        }

        return builder.build();
    }

    public boolean isEmpty() {
        return this.expectedComponents.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DataComponentExactPredicate dataComponentExactPredicate
            && this.expectedComponents.equals(dataComponentExactPredicate.expectedComponents);
    }

    @Override
    public int hashCode() {
        return this.expectedComponents.hashCode();
    }

    @Override
    public String toString() {
        return this.expectedComponents.toString();
    }

    @Override
    public boolean test(DataComponentGetter componentGetter) {
        for (TypedDataComponent<?> typedDataComponent : this.expectedComponents) {
            Object object = componentGetter.get(typedDataComponent.type());
            if (!Objects.equals(typedDataComponent.value(), object)) {
                return false;
            }
        }

        return true;
    }

    public boolean alwaysMatches() {
        return this.expectedComponents.isEmpty();
    }

    public DataComponentPatch asPatch() {
        DataComponentPatch.Builder builder = DataComponentPatch.builder();

        for (TypedDataComponent<?> typedDataComponent : this.expectedComponents) {
            builder.set(typedDataComponent);
        }

        return builder.build();
    }

    public static class Builder {
        private final List<TypedDataComponent<?>> expectedComponents = new ArrayList<>();

        Builder() {
        }

        public <T> DataComponentExactPredicate.Builder expect(TypedDataComponent<T> component) {
            return this.expect(component.type(), component.value());
        }

        public <T> DataComponentExactPredicate.Builder expect(DataComponentType<? super T> component, T value) {
            for (TypedDataComponent<?> typedDataComponent : this.expectedComponents) {
                if (typedDataComponent.type() == component) {
                    throw new IllegalArgumentException("Predicate already has component of type: '" + component + "'");
                }
            }

            this.expectedComponents.add(new TypedDataComponent<>(component, value));
            return this;
        }

        public DataComponentExactPredicate build() {
            return new DataComponentExactPredicate(List.copyOf(this.expectedComponents));
        }
    }
}
