package net.minecraft.world.level.entity;

import org.jspecify.annotations.Nullable;

public interface EntityTypeTest<B, T extends B> {
    static <B, T extends B> EntityTypeTest<B, T> forClass(final Class<T> clazz) {
        return new EntityTypeTest<B, T>() {
            @Override
            public @Nullable T tryCast(B entity) {
                return (T)(clazz.isInstance(entity) ? entity : null);
            }

            @Override
            public Class<? extends B> getBaseClass() {
                return clazz;
            }
        };
    }

    static <B, T extends B> EntityTypeTest<B, T> forExactClass(final Class<T> clazz) {
        return new EntityTypeTest<B, T>() {
            @Override
            public @Nullable T tryCast(B entity) {
                return (T)(clazz.equals(entity.getClass()) ? entity : null);
            }

            @Override
            public Class<? extends B> getBaseClass() {
                return clazz;
            }
        };
    }

    @Nullable T tryCast(B entity);

    Class<? extends B> getBaseClass();
}
