package net.minecraft.server.packs.resources;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public abstract class SimplePreparableReloadListener<T> implements PreparableReloadListener {
    @Override
    public final CompletableFuture<Void> reload(
        PreparableReloadListener.SharedState state, Executor backgroundExecutor, PreparableReloadListener.PreparationBarrier preparation, Executor gameExecutor
    ) {
        ResourceManager resourceManager = state.resourceManager();
        return CompletableFuture.<T>supplyAsync(() -> this.prepare(resourceManager, Profiler.get()), backgroundExecutor)
            .thenCompose(preparation::wait)
            .thenAcceptAsync(object -> this.apply((T)object, resourceManager, Profiler.get()), gameExecutor);
    }

    protected abstract T prepare(ResourceManager resourceManager, ProfilerFiller profiler);

    protected abstract void apply(T object, ResourceManager resourceManager, ProfilerFiller profiler);
}
