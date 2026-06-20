package net.minecraft.server.packs.resources;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class SimpleReloadInstance<S> implements ReloadInstance {
    private static final int PREPARATION_PROGRESS_WEIGHT = 2;
    private static final int EXTRA_RELOAD_PROGRESS_WEIGHT = 2;
    private static final int LISTENER_PROGRESS_WEIGHT = 1;
    final CompletableFuture<Unit> allPreparations = new CompletableFuture<>();
    private @Nullable CompletableFuture<List<S>> allDone;
    final Set<PreparableReloadListener> preparingListeners;
    private final int listenerCount;
    private final AtomicInteger startedTasks = new AtomicInteger();
    private final AtomicInteger finishedTasks = new AtomicInteger();
    private final AtomicInteger startedReloads = new AtomicInteger();
    private final AtomicInteger finishedReloads = new AtomicInteger();

    public static ReloadInstance of(
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        Executor backgroundExecutor,
        Executor gameExecutor,
        CompletableFuture<Unit> alsoWaitedFor
    ) {
        SimpleReloadInstance<Void> simpleReloadInstance = new SimpleReloadInstance<>(listeners);
        simpleReloadInstance.startTasks(backgroundExecutor, gameExecutor, resourceManager, listeners, SimpleReloadInstance.StateFactory.SIMPLE, alsoWaitedFor);
        return simpleReloadInstance;
    }

    protected SimpleReloadInstance(List<PreparableReloadListener> preparingListeners) {
        this.listenerCount = preparingListeners.size();
        this.preparingListeners = new HashSet<>(preparingListeners);
    }

    protected void startTasks(
        Executor backgroundExecutor,
        Executor gameExecutor,
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        SimpleReloadInstance.StateFactory<S> stateFactory,
        CompletableFuture<?> alsoWaitedFor
    ) {
        this.allDone = this.prepareTasks(backgroundExecutor, gameExecutor, resourceManager, listeners, stateFactory, alsoWaitedFor);
    }

    protected CompletableFuture<List<S>> prepareTasks(
        Executor backgroundExecutor,
        Executor gameExecutor,
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        SimpleReloadInstance.StateFactory<S> stateFactory,
        CompletableFuture<?> alsoWaitedFor
    ) {
        Executor executor = runnable -> {
            this.startedTasks.incrementAndGet();
            backgroundExecutor.execute(() -> {
                runnable.run();
                this.finishedTasks.incrementAndGet();
            });
        };
        Executor executor1 = runnable -> {
            this.startedReloads.incrementAndGet();
            gameExecutor.execute(() -> {
                runnable.run();
                this.finishedReloads.incrementAndGet();
            });
        };
        this.startedTasks.incrementAndGet();
        alsoWaitedFor.thenRun(this.finishedTasks::incrementAndGet);
        PreparableReloadListener.SharedState sharedState = new PreparableReloadListener.SharedState(resourceManager);
        listeners.forEach(preparableReloadListener1 -> preparableReloadListener1.prepareSharedState(sharedState));
        CompletableFuture<?> completableFuture = alsoWaitedFor;
        List<CompletableFuture<S>> list = new ArrayList<>();

        for (PreparableReloadListener preparableReloadListener : listeners) {
            PreparableReloadListener.PreparationBarrier preparationBarrier = this.createBarrierForListener(
                preparableReloadListener, completableFuture, gameExecutor
            );
            CompletableFuture<S> completableFuture1 = stateFactory.create(sharedState, preparationBarrier, preparableReloadListener, executor, executor1);
            list.add(completableFuture1);
            completableFuture = completableFuture1;
        }

        return Util.sequenceFailFast(list);
    }

    private PreparableReloadListener.PreparationBarrier createBarrierForListener(
        final PreparableReloadListener listener, final CompletableFuture<?> alsoWaitedFor, final Executor executor
    ) {
        return new PreparableReloadListener.PreparationBarrier() {
            @Override
            public <T> CompletableFuture<T> wait(T backgroundResult) {
                executor.execute(() -> {
                    SimpleReloadInstance.this.preparingListeners.remove(listener);
                    if (SimpleReloadInstance.this.preparingListeners.isEmpty()) {
                        SimpleReloadInstance.this.allPreparations.complete(Unit.INSTANCE);
                    }
                });
                return SimpleReloadInstance.this.allPreparations.thenCombine((CompletionStage<? extends T>)alsoWaitedFor, (unit, object) -> backgroundResult);
            }
        };
    }

    @Override
    public CompletableFuture<?> done() {
        return Objects.requireNonNull(this.allDone, "not started");
    }

    @Override
    public float getActualProgress() {
        int i = this.listenerCount - this.preparingListeners.size();
        float f = weightProgress(this.finishedTasks.get(), this.finishedReloads.get(), i);
        float f1 = weightProgress(this.startedTasks.get(), this.startedReloads.get(), this.listenerCount);
        return f / f1;
    }

    private static int weightProgress(int tasks, int reloads, int listeners) {
        return tasks * 2 + reloads * 2 + listeners * 1;
    }

    public static ReloadInstance create(
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        Executor backgroundExecutor,
        Executor gameExecutor,
        CompletableFuture<Unit> alsoWaitedFor,
        boolean profiled
    ) {
        return profiled
            ? ProfiledReloadInstance.of(resourceManager, listeners, backgroundExecutor, gameExecutor, alsoWaitedFor)
            : of(resourceManager, listeners, backgroundExecutor, gameExecutor, alsoWaitedFor);
    }

    @FunctionalInterface
    protected interface StateFactory<S> {
        SimpleReloadInstance.StateFactory<Void> SIMPLE = (state, preparationBarrier, listener, backgroundExecutor, gameExecutor) -> listener.reload(
            state, backgroundExecutor, preparationBarrier, gameExecutor
        );

        CompletableFuture<S> create(
            PreparableReloadListener.SharedState state,
            PreparableReloadListener.PreparationBarrier preparationBarrier,
            PreparableReloadListener listener,
            Executor backgroundExecutor,
            Executor gameExecutor
        );
    }
}
