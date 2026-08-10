package net.minecraft.util.thread;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.util.profiling.metrics.MetricsRegistry;

public class PriorityConsecutiveExecutor extends AbstractConsecutiveExecutor<StrictQueue.RunnableWithPriority> {
    public PriorityConsecutiveExecutor(int size, Executor executor, String name) {
        super(new StrictQueue.FixedPriorityQueue(size), executor, name);
        MetricsRegistry.INSTANCE.add(this);
    }

    @Override
    public StrictQueue.RunnableWithPriority wrapRunnable(Runnable runnable) {
        return new StrictQueue.RunnableWithPriority(0, runnable);
    }

    public <Source> CompletableFuture<Source> scheduleWithResult(int priority, Consumer<CompletableFuture<Source>> resultConsumer) {
        CompletableFuture<Source> completableFuture = new CompletableFuture<>();
        this.schedule(new StrictQueue.RunnableWithPriority(priority, () -> resultConsumer.accept(completableFuture)));
        return completableFuture;
    }
}
