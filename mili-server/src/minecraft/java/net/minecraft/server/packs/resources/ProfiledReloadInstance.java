package net.minecraft.server.packs.resources;

import com.google.common.base.Stopwatch;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class ProfiledReloadInstance extends SimpleReloadInstance<ProfiledReloadInstance.State> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Stopwatch total = Stopwatch.createUnstarted();

    public static ReloadInstance of(
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        Executor backgroundExecutor,
        Executor gameExecutor,
        CompletableFuture<Unit> alsoWaitedFor
    ) {
        ProfiledReloadInstance profiledReloadInstance = new ProfiledReloadInstance(listeners);
        profiledReloadInstance.startTasks(
            backgroundExecutor,
            gameExecutor,
            resourceManager,
            listeners,
            (state, preparationBarrier, listener, backgroundExecutor1, gameExecutor1) -> {
                AtomicLong atomicLong = new AtomicLong();
                AtomicLong atomicLong1 = new AtomicLong();
                AtomicLong atomicLong2 = new AtomicLong();
                AtomicLong atomicLong3 = new AtomicLong();
                CompletableFuture<Void> completableFuture = listener.reload(
                    state,
                    profiledExecutor(backgroundExecutor1, atomicLong, atomicLong1, listener.getName()),
                    preparationBarrier,
                    profiledExecutor(gameExecutor1, atomicLong2, atomicLong3, listener.getName())
                );
                return completableFuture.thenApplyAsync(_void -> {
                    LOGGER.debug("Finished reloading {}", listener.getName());
                    return new ProfiledReloadInstance.State(listener.getName(), atomicLong, atomicLong1, atomicLong2, atomicLong3);
                }, gameExecutor);
            },
            alsoWaitedFor
        );
        return profiledReloadInstance;
    }

    private ProfiledReloadInstance(List<PreparableReloadListener> preparingListeners) {
        super(preparingListeners);
        this.total.start();
    }

    @Override
    protected CompletableFuture<List<ProfiledReloadInstance.State>> prepareTasks(
        Executor backgroundExecutor,
        Executor gameExecutor,
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        SimpleReloadInstance.StateFactory<ProfiledReloadInstance.State> stateFactory,
        CompletableFuture<?> alsoWaitedFor
    ) {
        return super.prepareTasks(backgroundExecutor, gameExecutor, resourceManager, listeners, stateFactory, alsoWaitedFor)
            .thenApplyAsync(this::finish, gameExecutor);
    }

    private static Executor profiledExecutor(Executor executor, AtomicLong timeTaken, AtomicLong timesRun, String name) {
        return runnable -> executor.execute(() -> {
            ProfilerFiller profilerFiller = Profiler.get();
            profilerFiller.push(name);
            long nanos = Util.getNanos();
            runnable.run();
            timeTaken.addAndGet(Util.getNanos() - nanos);
            timesRun.incrementAndGet();
            profilerFiller.pop();
        });
    }

    private List<ProfiledReloadInstance.State> finish(List<ProfiledReloadInstance.State> states) {
        this.total.stop();
        long l = 0L;
        LOGGER.info("Resource reload finished after {} ms", this.total.elapsed(TimeUnit.MILLISECONDS));

        for (ProfiledReloadInstance.State state : states) {
            long l1 = TimeUnit.NANOSECONDS.toMillis(state.preparationNanos.get());
            long l2 = state.preparationCount.get();
            long l3 = TimeUnit.NANOSECONDS.toMillis(state.reloadNanos.get());
            long l4 = state.reloadCount.get();
            long l5 = l1 + l3;
            long l6 = l2 + l4;
            String string = state.name;
            LOGGER.info("{} took approximately {} tasks/{} ms ({} tasks/{} ms preparing, {} tasks/{} ms applying)", string, l6, l5, l2, l1, l4, l3);
            l += l3;
        }

        LOGGER.info("Total blocking time: {} ms", l);
        return states;
    }

    public record State(String name, AtomicLong preparationNanos, AtomicLong preparationCount, AtomicLong reloadNanos, AtomicLong reloadCount) {
    }
}
