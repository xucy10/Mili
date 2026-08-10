package net.minecraft.util;

import com.mojang.logging.LogUtils;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.slf4j.Logger;

public class FutureChain implements TaskChainer, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private CompletableFuture<?> head = CompletableFuture.completedFuture(null);
    private final Executor executor;
    private volatile boolean closed;

    public FutureChain(Executor executor) {
        this.executor = executor;
    }

    @Override
    public <T> void append(CompletableFuture<T> future, Consumer<T> consumer) {
        this.head = this.head.<T, Object>thenCombine(future, (object, object1) -> object1).thenAcceptAsync(object -> {
            if (!this.closed) {
                consumer.accept((T)object);
            }
        }, this.executor).exceptionally(cause -> {
            if (cause instanceof CompletionException completionException) {
                cause = completionException.getCause();
            }

            if (cause instanceof CancellationException cancellationException) {
                throw cancellationException;
            } else {
                LOGGER.error("Chain link failed, continuing to next one", cause);
                return null;
            }
        });
    }

    @Override
    public void close() {
        this.closed = true;
    }
}
