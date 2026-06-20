package net.minecraft.util.debug;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public interface DebugValueAccess {
    <T> void forEachChunk(DebugSubscription<T> subscription, BiConsumer<ChunkPos, T> action);

    <T> @Nullable T getChunkValue(DebugSubscription<T> subscription, ChunkPos chunkPos);

    <T> void forEachBlock(DebugSubscription<T> subscription, BiConsumer<BlockPos, T> action);

    <T> @Nullable T getBlockValue(DebugSubscription<T> subscription, BlockPos pos);

    <T> void forEachEntity(DebugSubscription<T> subscription, BiConsumer<Entity, T> action);

    <T> @Nullable T getEntityValue(DebugSubscription<T> subscription, Entity entity);

    <T> void forEachEvent(DebugSubscription<T> subscription, DebugValueAccess.EventVisitor<T> visitor);

    @FunctionalInterface
    public interface EventVisitor<T> {
        void accept(T value, int expires, int subscriptionExpires);
    }
}
