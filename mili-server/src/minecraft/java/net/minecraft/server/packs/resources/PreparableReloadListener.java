package net.minecraft.server.packs.resources;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@FunctionalInterface
public interface PreparableReloadListener {
    CompletableFuture<Void> reload(
        PreparableReloadListener.SharedState state, Executor backgroundExecutor, PreparableReloadListener.PreparationBarrier preparation, Executor gameExecutor
    );

    default void prepareSharedState(PreparableReloadListener.SharedState state) {
    }

    default String getName() {
        return this.getClass().getSimpleName();
    }

    @FunctionalInterface
    public interface PreparationBarrier {
        <T> CompletableFuture<T> wait(T backgroundResult);
    }

    public static final class SharedState {
        private final ResourceManager manager;
        private final Map<PreparableReloadListener.StateKey<?>, Object> state = new IdentityHashMap<>();

        public SharedState(ResourceManager manager) {
            this.manager = manager;
        }

        public ResourceManager resourceManager() {
            return this.manager;
        }

        public <T> void set(PreparableReloadListener.StateKey<T> key, T value) {
            this.state.put(key, value);
        }

        public <T> T get(PreparableReloadListener.StateKey<T> key) {
            return Objects.requireNonNull((T)this.state.get(key));
        }
    }

    public static final class StateKey<T> {
    }
}
