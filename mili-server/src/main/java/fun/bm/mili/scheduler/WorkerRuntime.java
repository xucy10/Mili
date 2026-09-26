package fun.bm.mili.scheduler;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Async compute lane (fix.md §6).
 * <p>
 * This is the <b>only</b> place in the scheduler where work runs off the region's
 * owning thread, and it is deliberately narrow: it exists for pure computation
 * over immutable input — snapshots, copied task parameters, pathfinding over a
 * captured block volume.
 *
 * <p>It is <b>not</b> a place to run region ticks. The previous design executed
 * region tick bodies on a shared {@code ExecutorService}, which is a category
 * error under Folia: a pool thread is not the region's owning thread, so every
 * world mutation inside that body was a cross-region write. Region-owned work
 * arrives through {@link MiliScheduler#drainOwned(Object, long)} on the region's
 * own thread instead.
 *
 * <p>The contract, stated as a rule for callers:
 *
 * <pre>
 *     OK on a worker:   read a snapshot, compute, return a new immutable value
 *     NOT OK:           touch a Level, Entity, Chunk, BlockState or Region
 *
 *     Capture (region thread) -> Compute (worker) -> Apply (region thread)
 * </pre>
 *
 * <p>Workers are daemon threads named after the lane, so a stuck compute can
 * never keep the JVM alive after shutdown.
 */
public final class WorkerRuntime {

    private final String name;
    private final ExecutorService pool;

    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong rejectedAfterShutdown = new AtomicLong();

    private volatile boolean shutdown;

    public WorkerRuntime(String name, int threads) {
        this.name = name;
        final int size = Math.max(1, threads);
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            // Slightly below normal: compute must never outrank the region threads.
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
        this.pool = Executors.newFixedThreadPool(size, factory);
    }

    public String name() {
        return name;
    }

    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * Run a pure computation on this lane.
     *
     * @param pureWork must not touch Minecraft state; see the class contract
     * @return a future completing with the computed value, or {@code null} if the
     *         lane is already shut down
     */
    public <T> CompletableFuture<T> compute(Supplier<T> pureWork) {
        if (shutdown || pureWork == null) {
            rejectedAfterShutdown.incrementAndGet();
            return null;
        }
        submitted.incrementAndGet();
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    T value = pureWork.get();
                    completed.incrementAndGet();
                    return value;
                } catch (Throwable t) {
                    failed.incrementAndGet();
                    throw t;
                }
            }, pool);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            rejectedAfterShutdown.incrementAndGet();
            submitted.decrementAndGet();
            return null;
        }
    }

    /** Submit a pure computation for its side-effect-free result shape, ignoring the value. */
    public Future<?> execute(Runnable pureWork) {
        if (shutdown || pureWork == null) {
            rejectedAfterShutdown.incrementAndGet();
            return null;
        }
        submitted.incrementAndGet();
        try {
            return pool.submit(() -> {
                try {
                    pureWork.run();
                    completed.incrementAndGet();
                } catch (Throwable t) {
                    failed.incrementAndGet();
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            rejectedAfterShutdown.incrementAndGet();
            submitted.decrementAndGet();
            return null;
        }
    }

    /** Approximate number of queued + running compute tasks. */
    public int pendingTasks() {
        if (pool instanceof java.util.concurrent.ThreadPoolExecutor tpe) {
            return tpe.getQueue().size() + tpe.getActiveCount();
        }
        return -1;
    }

    public int activeWorkers() {
        if (pool instanceof java.util.concurrent.ThreadPoolExecutor tpe) {
            return tpe.getActiveCount();
        }
        return -1;
    }

    public long submittedCount() {
        return submitted.get();
    }

    public long completedCount() {
        return completed.get();
    }

    public long failedCount() {
        return failed.get();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("lane", name);
        out.put("shutdown", shutdown);
        out.put("pending", pendingTasks());
        out.put("active", activeWorkers());
        out.put("submitted", submitted.get());
        out.put("completed", completed.get());
        out.put("failed", failed.get());
        out.put("rejected_after_shutdown", rejectedAfterShutdown.get());
        return out;
    }

    /**
     * Stop the lane.
     *
     * @return {@code true} if every task finished within the grace period
     */
    public boolean shutdown(long graceMillis) {
        shutdown = true;
        pool.shutdown();
        try {
            if (pool.awaitTermination(Math.max(0L, graceMillis), TimeUnit.MILLISECONDS)) {
                return true;
            }
            pool.shutdownNow();
            return pool.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public String toString() {
        return "WorkerRuntime[" + name + " pending=" + pendingTasks() + " active=" + activeWorkers() + "]";
    }
}
