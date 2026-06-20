package net.minecraft.core.component;

import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public interface DataComponentHolder extends DataComponentGetter {
    DataComponentMap getComponents();

    @Override
    default <T> @Nullable T get(DataComponentType<? extends T> component) {
        return this.getComponents().get(component);
    }

    default <T> Stream<T> getAllOfType(Class<? extends T> type) {
        return this.getComponents().stream().map(TypedDataComponent::value).filter(object -> type.isAssignableFrom(object.getClass())).map(object -> (T)object);
    }

    @Override
    default <T> T getOrDefault(DataComponentType<? extends T> component, T defaultValue) {
        return this.getComponents().getOrDefault(component, defaultValue);
    }

    default boolean has(DataComponentType<?> component) {
        return this.getComponents().has(component);
    }
}
