package net.minecraft.core.component;

import org.jspecify.annotations.Nullable;

public interface DataComponentGetter {
    <T> @Nullable T get(DataComponentType<? extends T> component);

    default <T> T getOrDefault(DataComponentType<? extends T> component, T defaultValue) {
        T object = this.get(component);
        return object != null ? object : defaultValue;
    }

    default <T> @Nullable TypedDataComponent<T> getTyped(DataComponentType<T> component) {
        T object = this.get(component);
        return object != null ? new TypedDataComponent<>(component, object) : null;
    }
}
