package net.minecraft.world.level.entity;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public interface LevelEntityGetter<T extends EntityAccess> {
    @Nullable T get(int id);

    @Nullable T get(UUID uuid);

    Iterable<T> getAll();

    <U extends T> void get(EntityTypeTest<T, U> test, AbortableIterationConsumer<U> consumer);

    void get(AABB boundingBox, Consumer<T> consumer);

    <U extends T> void get(EntityTypeTest<T, U> test, AABB bounds, AbortableIterationConsumer<U> consumer);
}
