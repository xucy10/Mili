package net.minecraft.world.entity;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface EntityProcessor {
    EntityProcessor NOP = entity -> entity;

    @Nullable Entity process(Entity entity);
}
