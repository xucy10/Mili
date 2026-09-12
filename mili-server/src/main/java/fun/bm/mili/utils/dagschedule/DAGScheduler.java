package fun.bm.mili.utils.dagschedule;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * DAG-based scheduler that resolves task dependencies and executes them in a
 * wave (topological-level) pattern where each wave runs tasks concurrently.
 *
 * <p>Unlike RegionBalancer's single-level priority queue, DAGScheduler
 * uses a dependency-aware tick execution order that can represent the natural
 * dependencies between tick sub-steps (entity tick, block entity tick, etc.).</p>
 *
 * <p>Each wave is dispatched via JDK 25 Virtual Threads — no dedicated thread
 * pool is needed; the JVM manages scheduling onto carrier threads.</p>
 */
public final class DAGScheduler {

    private DAGScheduler() {}

    /**
     * Configuration for the DAG scheduler.
     */
    public static final class Config {
        /** Maximum number of tasks in a single DAG batch. */
        public static int MAX_BATCH_SIZE = 4096;
        /** Maximum number of task waves before forcing flat execution. */
        public static int MAX_WAVES = 16;
        /** Per-wave timeout in milliseconds. */
        public static long WAVE_TIMEOUT_MS = 50L;
        /** Use virtual threads if available (JDK 25+). */
        public static boolean USE_VIRTUAL_THREADS = true;

        private Config() {}
    }

    /**
     * Statistics snapshot.
     */
    public record Stats(
            long batchesDispatched,
            long tasksCompleted,
            long tasksFailed,
            long tasksCancelled,
            long wavesExecuted,
            long avgWaveMillis,
            int pendingBatches
    ) {}

    // ---------- State ----------

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean shutdown = new AtomicBoolean(false);
    private static final AtomicLong taskIdGen = new AtomicLong(0);
    private static volatile Map<Long, DAGTask> activeTasks = new ConcurrentHashMap<>();

    /** Statistics. */
    private static final AtomicLong statBatchesDispatched = new AtomicLong(0);
    private static final AtomicLong statTasksCompleted = new AtomicLong(0);
    private static final AtomicLong statTasksFailed = new AtomicLong(0);
    private static final AtomicLong statTasksCancelled = new AtomicLong(0);
    private static final AtomicLong statWavesExecuted = new AtomicLong(0);
    private static final AtomicLong statWaveNanosSum = new AtomicLong(0);

    /** Carrier thread pool for virtual threads. */
    private static volatile ExecutorService fallbackPool;

    // ---------- Lifecycle ----------

    public static void init() {
        if (!initialized.compareAndSet(false, true)) return;
        // Fallback pool for non-virtual-thread mode
        if (!Config.USE_VIRTUAL_THREADS) {
            int cores = Runtime.getRuntime().availableProcessors();
            fallbackPool = Executors.newFixedThreadPool(cores, r -> {
                Thread t = new Thread(r, "Mili-DAG-Worker");
                t.setDaemon(true);
                return t;
            });
        }
        LogUtils.getLogger().info("[Mili] DAGScheduler initialized (batchSize={}, maxWaves={}, vt={})",
                Config.MAX_BATCH_SIZE, Config.MAX_WAVES, Config.USE_VIRTUAL_THREADS);
    }

    public static void shutdown() {
        if (!shutdown.compareAndSet(false, true)) return;
        initialized.set(false);
        if (fallbackPool != null) {
            fallbackPool.shutdownNow();
        }
        activeTasks.clear();
        LogUtils.getLogger().info("[Mili] DAGScheduler shutdown");
    }

    // ---------- Public API ----------

    /**
     * Submit a task to the current batch.
     *
     * @param scheduleRef  opaque key (region ref, world, etc.)
     * @param work         work to execute
     * @param dependencies task IDs this task depends on
     * @param priority     priority hint (higher = more urgent)
     * @return the new task ID, or -1 if shut down
     */
    public static long submit(@NotNull Object scheduleRef, @NotNull Runnable work,
                              @NotNull Set<Long> dependencies, double priority) {
        if (shutdown.get()) return -1L;
        long id = taskIdGen.incrementAndGet();
        DAGTask task = new DAGTask(id, scheduleRef, work, dependencies, priority);
        BatchCollector.current().add(task);
        activeTasks.put(id, task);
        return id;
    }

    /**
     * Convenience overload with no dependencies.
     */
    public static long submit(@NotNull Object scheduleRef, @NotNull Runnable work, double priority) {
        return submit(scheduleRef, work, Set.of(), priority);
    }

    /**
     * Flush all accumulated tasks as one DAG batch, topologically sort and execute.
     *
     * @return result of this batch execution
     */
    @NotNull
    public static BatchResult flushBatch() {
        List<DAGTask> tasks = BatchCollector.current().drain();
        if (tasks.isEmpty()) return new BatchResult(0, 0, 0, 0, true);

        if (tasks.size() > Config.MAX_BATCH_SIZE) {
            LogUtils.getLogger().warn("[Mili] DAG batch too large ({} > {}), truncating",
                    tasks.size(), Config.MAX_BATCH_SIZE);
            tasks = tasks.subList(0, Config.MAX_BATCH_SIZE);
        }

        return executeDag(tasks);
    }

    // ---------- Execution ----------

    private static BatchResult executeDag(List<DAGTask> tasks) {
        long batchStart = System.nanoTime();

        try {
            TopologicalSorter sorter = new TopologicalSorter(tasks);
            List<List<DAGTask>> waves = sorter.sort();

            if (waves.size() > Config.MAX_WAVES) {
                LogUtils.getLogger().warn("[Mili] DAG depth {} exceeds maxWaves {}, flattening",
                        waves.size(), Config.MAX_WAVES);
                return executeFlatFallback(tasks);
            }

            int completed = 0, failed = 0, cancelled = 0;
            boolean success = true;

            for (List<DAGTask> wave : waves) {
                WaveResult result = executeWave(wave);
                completed += result.completed;
                failed += result.failed;
                cancelled += result.cancelled;
                if (!result.success) success = false;
            }

            long elapsed = System.nanoTime() - batchStart;
            statBatchesDispatched.incrementAndGet();
            statWavesExecuted.addAndGet(waves.size());
            statWaveNanosSum.addAndGet(elapsed);

            return new BatchResult(completed, failed, cancelled, waves.size(), success);

        } catch (Throwable ex) {
            LogUtils.getLogger().error("[Mili] DAG execution failed", ex);
            for (DAGTask t : tasks) {
                t.forceStatus(DAGTask.Status.FAILED);
                statTasksFailed.incrementAndGet();
            }
            return new BatchResult(0, tasks.size(), 0, 0, false);
        }
    }

    /**
     * Execute a single wave: all wave tasks are independent, run concurrently.
     */
    private static WaveResult executeWave(List<DAGTask> wave) {
        int count = wave.size();
        if (count == 0) return new WaveResult(0, 0, 0, true);

        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        // Use Phaser for flexible, reusable synchronization
        Phaser phaser = new Phaser(count);

        for (DAGTask task : wave) {
            if (!task.tryTransition(DAGTask.Status.PENDING, DAGTask.Status.READY)) {
                phaser.arrive();
                continue;
            }

            Runnable workWrapper = () -> {
                try {
                    if (task.tryTransition(DAGTask.Status.READY, DAGTask.Status.RUNNING)) {
                        task.work.run();
                        task.completionNanos = System.nanoTime();
                        task.forceStatus(DAGTask.Status.COMPLETED);
                        statTasksCompleted.incrementAndGet();
                        completed.incrementAndGet();
                        notifyDependents(task);
                    }
                } catch (Throwable ex) {
                    task.failureCause = ex;
                    task.forceStatus(DAGTask.Status.FAILED);
                    statTasksFailed.incrementAndGet();
                    failed.incrementAndGet();
                    LogUtils.getLogger().debug("[Mili] DAG task failed", ex);
                } finally {
                    phaser.arrive();
                }
            };

            if (Config.USE_VIRTUAL_THREADS) {
                try {
                    Thread.ofVirtual().name("dag-task-" + task.taskId).start(workWrapper);
                } catch (Throwable ex) {
                    // If virtual threads are unavailable (older JVM), fallback
                    if (fallbackPool != null) {
                        fallbackPool.execute(workWrapper);
                    } else {
                        new Thread(workWrapper).start();
                    }
                }
            } else if (fallbackPool != null) {
                fallbackPool.execute(workWrapper);
            } else {
                new Thread(workWrapper).start();
            }
        }

        // Wait with timeout
        try {
            phaser.awaitAdvanceInterruptibly(phaser.arrive(),
                    Config.WAVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LogUtils.getLogger().warn("[Mili] DAG wave interrupted, forcing completion");
        } catch (TimeoutException ex) {
            LogUtils.getLogger().warn("[Mili] DAG wave timed out after {}ms", Config.WAVE_TIMEOUT_MS);
        }

        return new WaveResult(completed.get(), failed.get(), 0, failed.get() == 0);
    }

    /**
     * Notify downstream tasks that a dependency completed.
     */
    private static void notifyDependents(DAGTask completed) {
        for (Long depId : completed.dependents) {
            DAGTask dependent = activeTasks.get(depId);
            if (dependent != null && dependent.onDependencyCompleted()) {
                dependent.forceStatus(DAGTask.Status.READY);
            }
        }
    }

    /**
     * Flatten fallback: execute all tasks sequentially in a single thread.
     */
    private static BatchResult executeFlatFallback(List<DAGTask> tasks) {
        int completed = 0, failed = 0;
        for (DAGTask task : tasks) {
            try {
                task.forceStatus(DAGTask.Status.RUNNING);
                task.work.run();
                task.completionNanos = System.nanoTime();
                task.forceStatus(DAGTask.Status.COMPLETED);
                statTasksCompleted.incrementAndGet();
                completed++;
            } catch (Throwable ex) {
                task.forceStatus(DAGTask.Status.FAILED);
                statTasksFailed.incrementAndGet();
                failed++;
            }
        }
        return new BatchResult(completed, failed, 0, 1, failed == 0);
    }

    // ---------- Stats ----------

    public static Stats getStats() {
        long batches = statBatchesDispatched.get();
        long avgWave = batches > 0 ? statWaveNanosSum.get() / batches : 0;
        return new Stats(batches, statTasksCompleted.get(), statTasksFailed.get(),
                statTasksCancelled.get(), statWavesExecuted.get(), (avgWave / 1_000_000L),
                activeTasks.size());
    }

    // ---------- Result Records ----------

    public record BatchResult(int completed, int failed, int cancelled, int wavesExecuted, boolean success) {}

    public record WaveResult(int completed, int failed, int cancelled, boolean success) {}

    // ---------- Thread-local Batch Collector ----------

    static final class BatchCollector {
        private static final ThreadLocal<BatchCollector> INSTANCE = ThreadLocal.withInitial(BatchCollector::new);
        private final List<DAGTask> tasks = new ArrayList<>();

        static BatchCollector current() {
            return INSTANCE.get();
        }

        void add(DAGTask task) {
            tasks.add(task);
        }

        List<DAGTask> drain() {
            List<DAGTask> result = new ArrayList<>(tasks);
            tasks.clear();
            return result;
        }
    }
}
