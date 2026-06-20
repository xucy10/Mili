package net.minecraft.server.packs.resources;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public interface ResourceManagerReloadListener extends PreparableReloadListener {
    @Override
    default CompletableFuture<Void> reload(
        PreparableReloadListener.SharedState state, Executor backgroundExecutor, PreparableReloadListener.PreparationBarrier preparation, Executor gameExecutor
    ) {
        ResourceManager resourceManager = state.resourceManager();
        return preparation.wait(Unit.INSTANCE).thenRunAsync(() -> {
            ProfilerFiller profilerFiller = Profiler.get();
            profilerFiller.push("listener");
            this.onResourceManagerReload(resourceManager);
            profilerFiller.pop();
        }, gameExecutor);
    }

    void onResourceManagerReload(ResourceManager resourceManager);
}
