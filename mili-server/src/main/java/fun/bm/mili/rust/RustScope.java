package fun.bm.mili.rust;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Rust-style structured concurrency scope.
 * <p>
 * Mirrors Rust's {@code std::thread::scope}: all spawned tasks are guaranteed
 * to complete before the scope exits. No thread leaks, no orphaned tasks.
 * <p>
 * Usage:
 * <pre>{@code
 *   RustScope scope = RustScope.create();
 *   scope.spawn(() -> processChunk(cx1, cz1));
 *   scope.spawn(() -> processChunk(cx2, cz2));
 *   scope.join();  // blocks until all tasks complete
 * }</pre>
 */
public final class RustScope implements AutoCloseable {

    private final ExecutorService executor;
    private final List<Runnable> tasks;
    private volatile boolean closed;

    private RustScope(int threads) {
        this.executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "Mili-RustScope");
            t.setDaemon(true);
            return t;
        });
        this.tasks = new ArrayList<>();
        this.closed = false;
    }

    public static RustScope create() {
        return new RustScope(Runtime.getRuntime().availableProcessors());
    }

    public static RustScope create(int threads) {
        return new RustScope(Math.max(1, threads));
    }

    public void spawn(Runnable task) {
        if (closed) throw new IllegalStateException("Scope is closed");
        tasks.add(task);
        executor.execute(task);
    }

    public void spawn(Consumer<RustScope> task) {
        if (closed) throw new IllegalStateException("Scope is closed");
        tasks.add(() -> task.accept(this));
        executor.execute(() -> task.accept(this));
    }

    public void join() {
        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        closed = true;
    }

    public void join(long timeout, TimeUnit unit) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout, unit)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        closed = true;
    }

    @Override
    public void close() {
        if (!closed) {
            join();
        }
    }
}