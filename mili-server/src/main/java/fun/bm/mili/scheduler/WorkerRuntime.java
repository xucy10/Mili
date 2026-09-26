package fun.bm.mili.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Shared worker pool.
 * <p>
 * fix.md §17: workers belong to the scheduler, not to a region.  They pull the highest
 * priority work available, execute within a budget, then yield and take the next item.
 * <p>
 * <b>Hard rule (fix.md §6 and §7):</b> workers may only run <i>pure / isolated</i>
 * computation over snapshots.  They must never touch
 * {@code Mob}, {@code Level}, {@code Chunk}, {@code Entity} or {@code Region} state —
 * that would bypass region ownership.  Minecraft state mutation is always committed
 * back on the region's owning thread.
 * <p>
 * Mili start - fix: <b>ownership.</b> There is exactly one pool, and exactly one size
 * authority for it: {@code MiliSchedulerImpl.init()} reads
 * {@code RegionBalancerConfig.getThreadPoolSize()}.  No other subsystem may create it —
 * {@link #init(int)} exists for the scheduler and for tests.  Once the pool exists it
 * cannot be resized, so a later {@code init()} with a different size is discarded, and that
 * discard is logged rather than silent, because a size mismatch means the caller has the
 * wrong mental model of who owns this pool.
 */
public final class WorkerRuntime {

    private WorkerRuntime() {}

    private static final org.slf4j.Logger LOG =
            com.mojang.logging.LogUtils.getClassLogger();

    private static volatile ExecutorService pool;
    private static volatile Semaphore inFlight;
    private static final AtomicInteger WORKER_COUNT = new AtomicInteger(0);
    private static final AtomicInteger THREAD_NAMER = new AtomicInteger(0);
    private static final AtomicInteger COMPLETED = new AtomicInteger(0);
    private static final AtomicInteger FAILED = new AtomicInteger(0);
    private static volatile boolean shutdown = false;

    /** Submit pure computation. Rejected when the runtime is not running. */
    public static <T> CompletableFuture<T> submitPure(Supplier<T> computation) {
        ExecutorService p = pool;
        if (p == null || shutdown) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Mili WorkerRuntime is not running"));
        }
        Semaphore permit = inFlight;
        try {
            if (permit != null && !permit.tryAcquire()) {
                // Backpressure: reject rather than grow unbounded (fix.md §3).
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Mili WorkerRuntime saturated"));
            }
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                T result = computation.get();
                COMPLETED.incrementAndGet();
                return result;
            } catch (Throwable t) {
                FAILED.incrementAndGet();
                throw t;
            } finally {
                if (permit != null) permit.release();
            }
        }, p);
    }

    /** Fire-and-forget pure computation (e.g. snapshot post-processing). */
    public static void executePure(Runnable computation) {
        submitPure(() -> {
            computation.run();
            return null;
        });
    }

    public static synchronized void init(int threads) {
        int size = Math.max(1, threads);
        if (pool != null) {
            // Mili start - fix: this used to be a silent no-op.  A second initializer asking
            // for a different size is a real defect — it means some other subsystem believes
            // it owns the pool — so the discarded request is reported instead of hidden.
            // (The original instance of that was AsyncPathfinder sizing the pool from
            // `async-pathfinding.thread-count`; it no longer creates the pool at all.)
            if (size != WORKER_COUNT.get()) {
                LOG.warn("[Mili] WorkerRuntime is already running with {} worker(s); the request "
                                + "for {} was ignored — the pool is owned by the scheduler and cannot "
                                + "be resized after creation.",
                        WORKER_COUNT.get(), size);
            }
            return;
        }
        shutdown = false;
        WORKER_COUNT.set(size);
        THREAD_NAMER.set(0);
        inFlight = new Semaphore(Math.max(1, size * 8));
        pool = Executors.newFixedThreadPool(size, r -> {
            Thread t = new Thread(r, "Mili-Worker-" + THREAD_NAMER.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public static boolean isRunning() {
        return pool != null && !shutdown;
    }

    public static synchronized void shutdown() {
        shutdown = true;
        ExecutorService p = pool;
        if (p == null) return;
        pool = null;
        p.shutdown();
        try {
            if (!p.awaitTermination(5, TimeUnit.SECONDS)) {
                p.shutdownNow();
            }
        } catch (InterruptedException e) {
            p.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static int completed() { return COMPLETED.get(); }
    public static int failed() { return FAILED.get(); }
    public static int poolSize() { return WORKER_COUNT.get(); }
}
