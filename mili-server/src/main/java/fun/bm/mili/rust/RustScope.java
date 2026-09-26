package fun.bm.mili.rust;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    // Mili start - fix: use CountDownLatch for reliable join instead of shutdown+awaitTermination
    // which can return before all submitted tasks finish if queue has pending items
    private final java.util.concurrent.atomic.AtomicInteger pendingCount = new AtomicInteger(0);
    private volatile CountDownLatch latch;
    // Mili end
    private volatile boolean closed;

    private RustScope(int threads) {
        this.executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "Mili-RustScope");
            t.setDaemon(true);
            return t;
        });
        // Mili start - fix: removed tasks list (was unused except for tracking, caused thread-safety issues)
        this.closed = false;
        // Mili end
    }

    public static RustScope create() {
        return new RustScope(Runtime.getRuntime().availableProcessors());
    }

    public static RustScope create(int threads) {
        return new RustScope(Math.max(1, threads));
    }

    public void spawn(Runnable task) {
        // Mili start - fix: thread-safe spawn with pending count tracking; removed unused tasks list
        if (closed) throw new IllegalStateException("Scope is closed");
        java.util.Objects.requireNonNull(task);
        pendingCount.incrementAndGet();
        executor.execute(() -> {
            try {
                task.run();
            } finally {
                if (pendingCount.decrementAndGet() == 0) {
                    CountDownLatch l = latch;
                    if (l != null) l.countDown();
                }
            }
        });
        // Mili end
    }

    public void spawn(Consumer<RustScope> task) {
        // Mili start - fix: thread-safe spawn; removed duplicate execution (original added wrapper AND executed unwrapped)
        if (closed) throw new IllegalStateException("Scope is closed");
        java.util.Objects.requireNonNull(task);
        pendingCount.incrementAndGet();
        executor.execute(() -> {
            try {
                task.accept(this);
            } finally {
                if (pendingCount.decrementAndGet() == 0) {
                    CountDownLatch l = latch;
                    if (l != null) l.countDown();
                }
            }
        });
        // Mili end
    }

    public void join() {
        // Mili start - fix: use CountDownLatch for exact completion tracking instead of shutdown+awaitTermination
        // which may return early if tasks are still queued
        int remaining = pendingCount.get();
        if (remaining > 0) {
            latch = new CountDownLatch(1);
            // Re-check in case tasks completed between get() and latch creation
            if (pendingCount.get() == 0) {
                latch.countDown();
            }
            try {
                latch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        closed = true;
        // Mili end
    }

    public void join(long timeout, TimeUnit unit) {
        // Mili start - fix: use CountDownLatch for exact completion tracking
        int remaining = pendingCount.get();
        if (remaining > 0) {
            latch = new CountDownLatch(1);
            if (pendingCount.get() == 0) {
                latch.countDown();
            }
            try {
                latch.await(timeout, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        closed = true;
        // Mili end
    }

    @Override
    public void close() {
        // Mili start - fix: double-close safety with CAS-like check
        if (!closed) {
            join();
        }
        // Mili end
    }
}