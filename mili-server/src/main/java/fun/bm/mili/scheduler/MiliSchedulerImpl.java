package fun.bm.mili.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default {@link MiliScheduler}.
 * <p>
 * Regions are modelled as {@link MiliRegionRuntime} entries keyed by the stable id from
 * {@link RegionIdRegistry}. Every admission decision ends in a {@link SubmissionResult};
 * there is no "queue full, run it yourself" path anywhere in this class.
 */
public final class MiliSchedulerImpl implements MiliScheduler {

    private static final MiliSchedulerImpl INSTANCE = new MiliSchedulerImpl();

    private static final ConcurrentHashMap<Long, MiliRegionRuntime> REGIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, ConcurrentLinkedQueue<TaskHandle>> DEFERRED =
            new ConcurrentHashMap<>();

    private static final AtomicLong ACCEPTED = new AtomicLong();
    private static final AtomicLong MERGED = new AtomicLong();
    private static final AtomicLong DEFERRED_COUNT = new AtomicLong();
    private static final AtomicLong REJECTED = new AtomicLong();
    private static final AtomicLong TIMED_OUT = new AtomicLong();

    private static volatile int queueCapacity = 1024;
    private static volatile long defaultBudgetNanos = RegionBudget.NORMAL_BUDGET_NANOS;
    private static volatile SchedulerState state = SchedulerState.NEW;

    private MiliSchedulerImpl() {}

    public static MiliSchedulerImpl instance() {
        return INSTANCE;
    }

    // ---------- lifecycle ----------

    public static void init() {
        init(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    }

    /**
     * Interval of the background scheduler round: sweeping timeouts, retrying deferred
     * submissions and refreshing budgets.
     */
    private static final long SCHEDULER_ROUND_MILLIS = 50L;

    private static volatile Thread driverThread;

    public static void init(int workerThreads) {
        if (state == SchedulerState.RUNNING) return;
        state = SchedulerState.RUNNING;
        WorkerRuntime.init(workerThreads);
        startDriver();
    }

    /**
     * Drive one scheduler round per tick-cadence. Keeps the runtime self-driving without
     * needing a hook inside Minecraft's server loop.
     */
    private static void startDriver() {
        if (driverThread != null && driverThread.isAlive()) return;
        driverThread = new Thread(() -> {
            while (state == SchedulerState.RUNNING) {
                try {
                    Thread.sleep(SCHEDULER_ROUND_MILLIS);
                    if (state != SchedulerState.RUNNING) break;
                    tickRound();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Throwable t) {
                    // A scheduler round must never kill the driver thread.
                    com.mojang.logging.LogUtils.getClassLogger()
                            .error("[Mili] scheduler round failed", t);
                }
            }
        }, "Mili-Scheduler-Driver");
        driverThread.setDaemon(true);
        driverThread.start();
    }

    private static void tickRound() {
        instance().tick();
        BudgetController.updateFrom(runtimes());
    }

    public static void shutdown() {
        state = SchedulerState.SHUTDOWN;
        Thread driver = driverThread;
        driverThread = null;
        if (driver != null) {
            driver.interrupt();
            try {
                driver.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        for (MiliRegionRuntime runtime : REGIONS.values()) {
            RegionLifecycle.destroy(runtime);
        }
        REGIONS.clear();
        DEFERRED.clear();
        TaskController.cancelAll();
        WorkerRuntime.shutdown();
        RegionOwnership.clear();
    }

    @Override
    public SchedulerState getState() {
        return state;
    }

    public static void setQueueCapacity(int capacity) {
        queueCapacity = Math.max(1, capacity);
    }

    public static void setDefaultBudgetNanos(long nanos) {
        defaultBudgetNanos = Math.max(1L, nanos);
    }

    // ---------- region registry ----------

    public static MiliRegionRuntime runtimeFor(Object region) {
        if (region == null) return null;
        long id = RegionIdRegistry.idOf(region);
        MiliRegionRuntime existing = REGIONS.get(id);
        if (existing != null) return existing;
        MiliRegionRuntime created = new MiliRegionRuntime(id, region, queueCapacity, defaultBudgetNanos);
        MiliRegionRuntime prev = REGIONS.putIfAbsent(id, created);
        return prev != null ? prev : created;
    }

    public static RegionRuntime runtimeById(long regionId) {
        return REGIONS.get(regionId);
    }

    public static void unregister(RegionRuntime runtime) {
        if (runtime == null) return;
        REGIONS.remove(runtime.regionId(), runtime);
        DEFERRED.remove(runtime.regionId());
    }

    public static Collection<MiliRegionRuntime> runtimes() {
        return REGIONS.values();
    }

    // ---------- submission ----------

    @Override
    public TaskHandle submit(Object region, Runnable task) {
        TaskHandle handle = TaskController.create(RegionIdRegistry.idOf(region), region, task, 0L);
        SubmissionResult result = admit(region, handle, null);
        if (result == SubmissionResult.ACCEPTED || result == SubmissionResult.MERGED) {
            return handle;
        }
        handle.cancel("rejected");
        clearMergeKey(handle);
        TaskController.release(handle);
        return null;
    }

    @Override
    public SubmissionResult trySubmit(Object region, Runnable task) {
        return trySubmit(region, task, null);
    }

    @Override
    public SubmissionResult trySubmit(Object region, Runnable task, Object mergeKey) {
        if (region == null || task == null) return SubmissionResult.REJECTED;
        long deadlineNanos = defaultDeadlineNanos(region);
        TaskHandle handle = TaskController.create(RegionIdRegistry.idOf(region), region, task, deadlineNanos);
        recordMergeKey(handle, mergeKey);
        SubmissionResult result = admit(region, handle, mergeKey);
        if (result == SubmissionResult.DEFERRED) {
            DEFERRED.computeIfAbsent(handle.regionId(), k -> new ConcurrentLinkedQueue<>()).add(handle);
        } else {
            if (result != SubmissionResult.ACCEPTED && result != SubmissionResult.MERGED) {
                handle.cancel("rejected");
            }
            if (result != SubmissionResult.ACCEPTED) {
                clearMergeKey(handle);
                TaskController.release(handle);
            }
        }
        return result;
    }

    /**
     * Admission control. This is the only place that decides what happens to incoming work.
     */
    private SubmissionResult admit(Object region, TaskHandle handle, Object mergeKey) {
        if (state != SchedulerState.RUNNING) {
            REJECTED.incrementAndGet();
            return SubmissionResult.REJECTED;
        }

        MiliRegionRuntime runtime = runtimeFor(region);
        if (runtime == null) {
            REJECTED.incrementAndGet();
            return SubmissionResult.REJECTED;
        }
        if (!runtime.state().acceptsNewWork()) {
            REJECTED.incrementAndGet();
            runtime.metrics().onRejected();
            return SubmissionResult.REJECTED;
        }

        runtime.metrics().onSubmitted();

        SubmissionResult result = runtime.queue().offer(handle);
        switch (result) {
            case ACCEPTED -> {
                ACCEPTED.incrementAndGet();
                return SubmissionResult.ACCEPTED;
            }
            case REJECTED -> {
                // Past deadline: no point queueing something we would cancel immediately.
                REJECTED.incrementAndGet();
                runtime.metrics().onRejected();
                return SubmissionResult.REJECTED;
            }
            case DEFERRED -> {
                // Queue full. Try to fold the work into an equivalent queued task first.
                if (mergeKey != null && tryMergeInto(runtime, handle, mergeKey)) {
                    MERGED.incrementAndGet();
                    runtime.metrics().onMerged();
                    TaskController.release(handle);
                    return SubmissionResult.MERGED;
                }
                // Deferrable: keep the handle alive and retry on the next scheduler round.
                DEFERRED_COUNT.incrementAndGet();
                runtime.metrics().onDeferred();
                return SubmissionResult.DEFERRED;
            }
            default -> {
                REJECTED.incrementAndGet();
                return SubmissionResult.REJECTED;
            }
        }
    }

    private boolean tryMergeInto(MiliRegionRuntime runtime, TaskHandle handle, Object mergeKey) {
        if (mergeKey == null) return false;
        for (TaskHandle queued : runtime.queue().snapshotForMerge()) {
            if (queued != handle && mergeKey.equals(mergeKeyOf(queued))) {
                if (queued.tryMerge(handle.work())) {
                    handle.markMerged();
                    return true;
                }
            }
        }
        return false;
    }

    private static final ConcurrentHashMap<Long, Object> MERGE_KEYS = new ConcurrentHashMap<>();

    private static void recordMergeKey(TaskHandle handle, Object key) {
        if (key != null) MERGE_KEYS.put(handle.taskId(), key);
    }

    private static Object mergeKeyOf(TaskHandle handle) {
        return MERGE_KEYS.get(handle.taskId());
    }

    private static void clearMergeKey(TaskHandle handle) {
        MERGE_KEYS.remove(handle.taskId());
    }

    // ---------- submitAndWait ----------

    @Override
    public boolean submitAndWait(Object region, Runnable task, long timeout, TimeUnit unit) {
        if (region == null || task == null) return false;

        // fix.md §2.1: the caller is the region's legal execution thread -> run inline.
        if (RegionOwnership.isOwnedByCurrentThread(region)) {
            MiliRegionRuntime runtime = runtimeFor(region);
            long begin = System.nanoTime();
            try {
                task.run();
                if (runtime != null) {
                    runtime.metrics().onExecuted(System.nanoTime() - begin);
                    runtime.debt().recordTick(System.nanoTime() - begin);
                }
                return true;
            } catch (Throwable t) {
                if (runtime != null) runtime.metrics().onCancelled();
                throw t;
            }
        }

        long deadlineNanos = defaultDeadlineNanos(region);
        TaskHandle handle = TaskController.create(RegionIdRegistry.idOf(region), region, task, deadlineNanos);
        SubmissionResult result = admit(region, handle, null);

        if (result != SubmissionResult.ACCEPTED) {
            if (result == SubmissionResult.DEFERRED) {
                DEFERRED.computeIfAbsent(handle.regionId(), k -> new ConcurrentLinkedQueue<>()).add(handle);
            } else if (result == SubmissionResult.REJECTED) {
                handle.cancel("rejected");
                TaskController.release(handle);
                return false;
            }
        }

        boolean finished;
        try {
            finished = handle.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handle.cancel("interrupted-while-waiting");
            TaskController.release(handle);
            return false;
        }

        if (!finished) {
            // Bounded wait expired: cancel for real (fix.md §3 "超过 Deadline -> REJECTED").
            TIMED_OUT.incrementAndGet();
            handle.cancel("submit-and-wait-timeout");
            TaskController.release(handle);
            return false;
        }

        TaskController.release(handle);
        return handle.state() == TaskState.COMPLETED;
    }

    // ---------- cancellation ----------

    @Override
    public boolean cancel(TaskHandle task) {
        if (task == null) return false;
        boolean cancelled = task.cancel("cancelled-by-scheduler");
        if (cancelled) TaskController.release(task);
        return cancelled;
    }

    // ---------- execution ----------

    @Override
    public int drainOwned(Object region, long budgetNanos) {
        if (region == null) return 0;
        MiliRegionRuntime runtime = runtimeFor(region);
        if (runtime == null) return 0;
        if (!runtime.state().allowsExecution()) return 0;
        if (!RegionOwnership.isOwnedByCurrentThread(region)) {
            // Learn the owner the first time we see this region tick, so later
            // isOwnedByCurrentThread() calls resolve without the reflective probe.
            RegionOwnership.markOwner(region, Thread.currentThread());
        }

        long deadline = System.nanoTime() + Math.max(1L, budgetNanos);
        int executed = 0;
        TaskHandle handle;
        while ((handle = runtime.queue().poll()) != null) {
            if (System.nanoTime() > deadline) {
                // Out of budget: hand it back for the next round (fix.md §17 yield).
                // If the queue cannot take it back, cancel instead of dropping it silently —
                // a dropped handle would leave submitAndWait callers blocked until timeout.
                if (runtime.queue().offer(handle) != SubmissionResult.ACCEPTED) {
                    handle.cancel("budget-exhausted-and-queue-full");
                }
                clearMergeKey(handle);
                TaskController.release(handle);
                break;
            }
            long begin = System.nanoTime();
            try {
                handle.runBody();
                executed++;
                long elapsed = System.nanoTime() - begin;
                runtime.metrics().onExecuted(elapsed);
                runtime.budget().consume(elapsed);
            } catch (Throwable t) {
                runtime.metrics().onCancelled();
            } finally {
                clearMergeKey(handle);
                TaskController.release(handle);
            }
        }
        return executed;
    }

    /**
     * Execute the entire pending queue for a region on the owning thread, ignoring budget.
     * Used during teardown and by explicit flush points.
     */
    public int drainAllOwned(Object region) {
        MiliRegionRuntime runtime = runtimeFor(region);
        if (runtime == null) return 0;
        int executed = 0;
        TaskHandle handle;
        while ((handle = runtime.queue().poll()) != null) {
            try {
                handle.runBody();
                executed++;
            } catch (Throwable ignored) {
                // logged by the caller's watchdog if needed
            } finally {
                clearMergeKey(handle);
                TaskController.release(handle);
            }
        }
        return executed;
    }

    // ---------- scheduler round ----------

    @Override
    public void tick() {
        if (state != SchedulerState.RUNNING) return;

        // 1. Timeouts must be real cancellations (fix.md §4).
        TaskController.sweepTimedOutTasks();

        // 2. Retry deferred submissions (fix.md §3 backpressure).
        retryDeferred();

        // 3. Refresh per-region budgets from the controller (fix.md §15).
        BudgetController.apply(REGIONS.values());
    }

    private void retryDeferred() {
        for (Map.Entry<Long, ConcurrentLinkedQueue<TaskHandle>> entry : DEFERRED.entrySet()) {
            MiliRegionRuntime runtime = REGIONS.get(entry.getKey());
            ConcurrentLinkedQueue<TaskHandle> deferred = entry.getValue();
            if (runtime == null) {
                TaskHandle handle;
                while ((handle = deferred.poll()) != null) handle.cancel("region-gone");
                continue;
            }
            int attempts = Math.min(deferred.size(), 64);
            for (int i = 0; i < attempts; i++) {
                TaskHandle handle = deferred.poll();
                if (handle == null) break;
                if (handle.isPastDeadline(System.nanoTime())) {
                    handle.cancel("deferred-past-deadline");
                    REJECTED.incrementAndGet();
                    continue;
                }
                SubmissionResult result = runtime.queue().offer(handle);
                if (result == SubmissionResult.ACCEPTED) {
                    ACCEPTED.incrementAndGet();
                } else if (result == SubmissionResult.REJECTED) {
                    handle.cancel("deferred-rejected");
                    REJECTED.incrementAndGet();
                } else {
                    deferred.add(handle); // still full; try again next round
                    break;
                }
            }
        }
    }

    private long defaultDeadlineNanos(Object region) {
        MiliRegionRuntime runtime = runtimeFor(region);
        long target = runtime == null ? SchedulerDebt.DEFAULT_TARGET_NANOS : runtime.debt().targetTickNanos();
        return System.nanoTime() + target * 4L;
    }

    // ---------- stats ----------

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("state", state.name());
        stats.put("regions", REGIONS.size());
        stats.put("accepted", ACCEPTED.get());
        stats.put("merged", MERGED.get());
        stats.put("deferred", DEFERRED_COUNT.get());
        stats.put("rejected", REJECTED.get());
        stats.put("timed_out", TIMED_OUT.get());
        stats.put("live_tasks", TaskController.liveCount());
        stats.put("worker_completed", WorkerRuntime.completed());
        stats.put("worker_failed", WorkerRuntime.failed());
        stats.put("deferred_queues", DEFERRED.size());
        return stats;
    }

    public static List<Map<String, Object>> getRegionStats() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MiliRegionRuntime runtime : REGIONS.values()) {
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("region_id", runtime.regionId());
            entry.put("state", runtime.state().name());
            entry.put("queue_depth", runtime.queue().depth());
            entry.put("budget_ms", runtime.budget().budgetNanos() / 1_000_000.0);
            entry.put("used_ms", runtime.budget().usedNanos() / 1_000_000.0);
            entry.put("debt_ms", runtime.debt().accumulatedDebtNanos() / 1_000_000.0);
            entry.put("submitted", runtime.metrics().submitted());
            entry.put("executed", runtime.metrics().executed());
            entry.put("cancelled", runtime.metrics().cancelled());
            entry.put("rejected", runtime.metrics().rejected());
            result.add(entry);
        }
        return result;
    }
}
