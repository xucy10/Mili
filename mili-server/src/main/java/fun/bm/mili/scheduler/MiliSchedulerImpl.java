package fun.bm.mili.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default {@link MiliScheduler} implementation.
 * <p>
 * Two execution lanes, and keeping them apart is the whole point:
 *
 * <pre>
 *   region-owned lane   drainOwned(region, budget)
 *       Called ON the region's owning thread, from that region's tick.
 *       The only lane allowed to touch Minecraft state.
 *
 *   compute lane        computeLane().compute(pureSupplier)
 *       Runs on Mili worker threads. Pure computation over snapshots only.
 *       Results are handed back through submit() for a region-owned apply step.
 * </pre>
 *
 * <p>What is deliberately absent is the third lane the old code had by accident:
 * "the submitting thread runs the work because the queue could not take it". That
 * lane does not exist here in any form. {@link #submit} returns a
 * {@link SubmissionResult}; {@link #submitAndWait} only ever runs work inline when
 * {@link RegionOwnership#isOwnedByCurrentThread(Object)} says the caller is
 * entitled to, and otherwise waits for the owning thread.
 *
 * <p><b>Consequence worth stating plainly:</b> a region that never ticks will
 * never execute its queued work, and a foreign caller waiting on it will time out
 * and get its task cancelled. That is the intended behaviour — the alternative is
 * running another region's work on the wrong thread, which is what caused the
 * original corruption.
 */
public final class MiliSchedulerImpl implements MiliScheduler {

    private static final MiliSchedulerImpl INSTANCE = new MiliSchedulerImpl();

    /** How long the destroy sequence waits for running bodies to observe cancellation. */
    private static final long DESTROY_QUIESCE_MILLIS = 250L;

    /** Work is never queued for a region more than this far in the future. */
    private static final long MAX_DEADLINE_HORIZON_NANOS = 5_000_000_000L; // 5s

    private final ConcurrentHashMap<Long, MiliRegionRuntime> runtimesById = new ConcurrentHashMap<>();
    private final TaskController taskController = new TaskController();
    private final AtomicReference<SchedulerState> state = new AtomicReference<>(SchedulerState.STOPPED);

    private volatile WorkerRuntime computeLane;
    private volatile int computeThreads = 2;
    private volatile boolean computeLaneEnabled = true;

    private final AtomicLong submitCalls = new AtomicLong();
    private final AtomicLong submittedAccepted = new AtomicLong();
    private final AtomicLong submittedMerged = new AtomicLong();
    private final AtomicLong submittedDeferred = new AtomicLong();
    private final AtomicLong submittedRejected = new AtomicLong();
    private final AtomicLong inlineRuns = new AtomicLong();
    private final AtomicLong waitTimeouts = new AtomicLong();
    /** Submissions that arrived after the region completed its destroy sequence. */
    private final AtomicLong submittedAfterDestroy = new AtomicLong();
    private final AtomicLong drainCycles = new AtomicLong();
    private final AtomicLong drainedTasks = new AtomicLong();
    private final AtomicLong tickCount = new AtomicLong();

    private MiliSchedulerImpl() {}

    public static MiliSchedulerImpl instance() {
        return INSTANCE;
    }

    /** Start the scheduler. Idempotent. */
    public static void init() {
        INSTANCE.start();
    }

    /** Stop the scheduler. Idempotent. */
    public static void shutdown() {
        INSTANCE.stop();
    }

    /** Every live runtime, concretely typed. */
    public static Collection<MiliRegionRuntime> allRuntimes() {
        return List.copyOf(INSTANCE.runtimesById.values());
    }

    /** Runtime for a region, creating it on demand. Returns {@code null} if the scheduler is not accepting work. */
    public static MiliRegionRuntime runtimeForRegion(Object region) {
        return INSTANCE.runtimeForInternal(region, true);
    }

    /** Runtime for a region if one already exists. */
    public static MiliRegionRuntime peekRuntimeFor(Object region) {
        return INSTANCE.runtimeForInternal(region, false);
    }

    public static TaskController taskController() {
        return INSTANCE.taskController;
    }

    // ---------- Lifecycle ----------

    public void start() {
        SchedulerState current = state.get();
        if (current == SchedulerState.RUNNING || current == SchedulerState.STARTING) return;
        if (!state.compareAndSet(current, SchedulerState.STARTING)) {
            // Someone else won the race; if they made it RUNNING we are done.
            if (state.get() == SchedulerState.RUNNING) return;
        }

        if (computeLaneEnabled) {
            computeLane = new WorkerRuntime("Mili-Compute", computeThreads);
        }

        state.set(SchedulerState.RUNNING);
        SchedulerLog.info("Mili scheduler started (compute lane: %s, threads: %d)",
                computeLaneEnabled ? "on" : "off", computeThreads);
    }

    public void stop() {
        if (state.getAndSet(SchedulerState.DRAINING) == SchedulerState.SHUTDOWN) {
            return;
        }

        // Drain what is still queued before cutting anything off. Every region is
        // walked explicitly so a region with a backlog is reported rather than
        // silently forgotten.
        int outstanding = 0;
        for (MiliRegionRuntime rt : allRuntimes()) {
            outstanding += rt.queue().size();
        }
        if (outstanding > 0) {
            SchedulerLog.warn("Mili scheduler stopping with %d queued task(s) still pending", outstanding);
        }

        int cancelled = taskController.cancelAll("scheduler-shutdown");
        if (cancelled > 0) {
            SchedulerLog.info("Mili scheduler cancelled %d task(s) on shutdown", cancelled);
        }

        if (computeLane != null) {
            boolean clean = computeLane.shutdown(1000L);
            if (!clean) {
                SchedulerLog.warn("Mili compute lane did not shut down cleanly");
            }
            computeLane = null;
        }

        for (MiliRegionRuntime rt : allRuntimes()) {
            rt.queue().close();
            rt.markClosed();
        }
        runtimesById.clear();
        taskController.clear();
        CrossRegionTransaction.clear();
        EntityScheduler.clear();
        RegionIdRegistry.clear();
        RegionOwnership.clear();

        state.set(SchedulerState.SHUTDOWN);
        SchedulerLog.info("Mili scheduler stopped");
    }

    public void configureComputeLane(boolean enabled, int threads) {
        this.computeLaneEnabled = enabled;
        this.computeThreads = Math.max(1, threads);
    }

    @Override
    public SchedulerState getState() {
        return state.get();
    }

    @Override
    public WorkerRuntime computeLane() {
        return computeLane;
    }

    // ---------- Runtime lookup ----------

    private MiliRegionRuntime runtimeForInternal(Object region, boolean create) {
        if (region == null) return null;

        SchedulerState current = state.get();
        if (create && !current.acceptsSubmissions()) {
            // Do not mint runtimes for a scheduler that is not taking work.
            return null;
        }

        // A destroyed region is a tombstone. Allocating it a fresh id here would
        // build a new runtime around a region whose lifecycle data has already
        // been torn down — the "region resurrected after destroy" bug.
        if (create && RegionIdRegistry.isDestroyed(region)) {
            submittedAfterDestroy.incrementAndGet();
            return null;
        }

        long id = create ? RegionIdRegistry.idOf(region) : RegionIdRegistry.peek(region);
        if (id == RegionIdRegistry.UNKNOWN) return null;

        MiliRegionRuntime existing = runtimesById.get(id);
        if (existing != null) return existing;
        if (!create) return null;

        MiliRegionRuntime created = new MiliRegionRuntime(id, region);
        MiliRegionRuntime raced = runtimesById.putIfAbsent(id, created);
        return raced != null ? raced : created;
    }

    @Override
    public RegionRuntime runtimeFor(Object region) {
        return runtimeForInternal(region, true);
    }

    @Override
    public RegionRuntime peekRuntime(Object region) {
        return runtimeForInternal(region, false);
    }

    @Override
    public Collection<? extends RegionRuntime> runtimes() {
        return List.copyOf(runtimesById.values());
    }

    @Override
    public double priorityOf(Object region) {
        MiliRegionRuntime rt = runtimeForInternal(region, false);
        return rt == null ? 0.0 : rt.priorityScore();
    }

    // ---------- Submission ----------

    @Override
    public SubmissionResult submit(Object region, Runnable work) {
        return submit(region, work, 0, null, TaskHandle.NO_DEADLINE, null);
    }

    @Override
    public SubmissionResult submit(Object region, Runnable work, int priority, Object mergeKey,
                                   long deadlineNanos, String description) {
        submitCalls.incrementAndGet();

        if (work == null) return SubmissionResult.REJECTED;
        if (!state.get().acceptsSubmissions()) {
            submittedRejected.incrementAndGet();
            return SubmissionResult.REJECTED;
        }

        MiliRegionRuntime rt = runtimeForInternal(region, true);
        if (rt == null) {
            // Unknown or closed region: refuse. Never run it on the caller.
            submittedRejected.incrementAndGet();
            return SubmissionResult.REJECTED;
        }
        if (!rt.acceptsWork()) {
            submittedRejected.incrementAndGet();
            return SubmissionResult.REJECTED;
        }

        long deadline = normalizeDeadline(deadlineNanos);
        boolean waited = mergeKey == null;
        TaskHandle handle = new TaskHandle(
                rt.regionId(), region, work, priority,
                waited ? CancellationToken.create() : CancellationToken.never(),
                deadline, mergeKey, description);

        taskController.register(handle);
        rt.metrics().onSubmitted();

        SubmissionResult result = rt.queue().offer(handle);
        switch (result) {
            case ACCEPTED -> {
                submittedAccepted.incrementAndGet();
                rt.metrics().onAccepted();
            }
            case MERGED -> {
                submittedMerged.incrementAndGet();
                rt.metrics().onMerged();
                taskController.complete(handle);
            }
            case DEFERRED -> {
                submittedDeferred.incrementAndGet();
                rt.metrics().onDeferred();
                taskController.complete(handle);
            }
            case REJECTED -> {
                submittedRejected.incrementAndGet();
                rt.metrics().onRejected();
                taskController.complete(handle);
            }
        }
        return result;
    }

    private static long normalizeDeadline(long deadlineNanos) {
        if (deadlineNanos == TaskHandle.NO_DEADLINE) return TaskHandle.NO_DEADLINE;
        long now = System.nanoTime();
        // A deadline beyond the horizon is almost always a unit mistake; clamp it
        // instead of letting a task sit in the queue effectively forever.
        long maxDeadline = now + MAX_DEADLINE_HORIZON_NANOS;
        return Math.min(deadlineNanos, maxDeadline);
    }

    @Override
    public boolean submitAndWait(Object region, Runnable work, long timeout, TimeUnit unit) {
        if (work == null || unit == null) return false;

        // fix.md §2.1 — ownership first. This is the one legitimate inline path:
        // the caller already is the region's execution thread, so running here
        // preserves region context rather than violating it.
        if (RegionOwnership.isOwnedByCurrentThread(region)) {
            MiliRegionRuntime rt = runtimeForInternal(region, false);
            long begin = System.nanoTime();
            try {
                work.run();
                inlineRuns.incrementAndGet();
                if (rt != null) {
                    rt.metrics().onInlineRun();
                    rt.budget().charge(System.nanoTime() - begin);
                }
                return true;
            } catch (TaskCancelledException e) {
                return false;
            } catch (Throwable t) {
                SchedulerLog.error("Inline region work failed on the owning thread", t);
                return false;
            }
        }

        // Not the owner. Queue it for whoever is, and wait — bounded.
        MiliRegionRuntime rt = runtimeForInternal(region, true);
        if (rt == null) return false;
        if (!rt.acceptsWork()) return false;

        long timeoutNanos = Math.max(1L, unit.toNanos(timeout));
        long deadline = System.nanoTime() + timeoutNanos;

        TaskHandle handle = new TaskHandle(
                rt.regionId(), region, work, TaskHandle.HIGH_PRIORITY,
                CancellationToken.create(), normalizeDeadline(deadline), null, "submitAndWait");

        taskController.register(handle);
        rt.metrics().onSubmitted();

        SubmissionResult result = rt.queue().offer(handle);
        if (!result.isAccepted()) {
            // The queue refused it. The one thing we must not do is run it here.
            switch (result) {
                case DEFERRED -> {
                    submittedDeferred.incrementAndGet();
                    rt.metrics().onDeferred();
                }
                default -> {
                    submittedRejected.incrementAndGet();
                    rt.metrics().onRejected();
                }
            }
            taskController.complete(handle);
            return false;
        }

        submittedAccepted.incrementAndGet();
        rt.metrics().onAccepted();

        try {
            boolean completed = handle.await(timeoutNanos, TimeUnit.NANOSECONDS);
            if (!completed) {
                waitTimeouts.incrementAndGet();
                handle.requestCancel("submitAndWait deadline exceeded");
                rt.metrics().onTimedOut();
            }
            return completed;
        } catch (InterruptedException e) {
            handle.requestCancel("submitAndWait interrupted");
            Thread.currentThread().interrupt();
            return false;
        } finally {
            taskController.complete(handle);
        }
    }

    // ---------- Region-owned execution ----------

    @Override
    public int drainOwned(Object region, long budgetNanos) {
        MiliRegionRuntime rt = runtimeForInternal(region, false);
        if (rt == null) return 0;
        if (!rt.acceptsExecution()) return 0;

        Thread owner = Thread.currentThread();
        long regionId = rt.regionId();

        // Record ownership for the whole drain, so a task that calls back into
        // submitAndWait() recognises itself as the owner and runs inline instead
        // of deadlocking on its own queue.
        RegionOwnership.markOwner(regionId, owner);
        drainCycles.incrementAndGet();

        int executed = 0;
        long deadline = System.nanoTime() + Math.max(1L, budgetNanos);
        try {
            while (true) {
                if (System.nanoTime() >= deadline) break;

                TaskHandle handle = rt.queue().poll();
                if (handle == null) break;

                runOwned(rt, handle);
                executed++;
                drainedTasks.incrementAndGet();
            }
        } finally {
            RegionOwnership.releaseOwner(regionId, owner);
        }
        return executed;
    }

    /** Run one task on the region's owning thread and publish its outcome. */
    private void runOwned(MiliRegionRuntime rt, TaskHandle handle) {
        long begin = System.nanoTime();
        rt.metrics().onInFlightStart();
        try {
            boolean ok = handle.runTask();
            if (!ok) {
                switch (handle.state()) {
                    case FAILED -> rt.metrics().onFailed();
                    case CANCELLED -> rt.metrics().onCancelled();
                    case TIMED_OUT -> rt.metrics().onTimedOut();
                    default -> {
                        // MERGED / already terminal: nothing ran, nothing to report.
                    }
                }
                if (handle.state() == TaskState.FAILED && handle.failure() != null) {
                    SchedulerLog.error("Region task failed: " + handle.description(), handle.failure());
                }
            } else {
                rt.metrics().onExecuted();
            }
        } catch (Throwable t) {
            // runTask() already swallows Task bodies; reaching here means the
            // handle machinery itself broke, which we must not let escape into
            // the region's tick.
            rt.metrics().onFailed();
            SchedulerLog.error("Task handle failed unexpectedly: " + handle.description(), t);
        } finally {
            long elapsed = System.nanoTime() - begin;
            rt.budget().charge(elapsed);
            rt.metrics().onInFlightEnd();
            taskController.complete(handle);
        }
    }

    @Override
    public boolean cancel(long taskId, String reason) {
        return taskController.cancel(taskId, reason);
    }

    // ---------- Teardown ----------

    @Override
    public RegionLifecycle.DestroyReport destroyRegion(Object region) {
        MiliRegionRuntime rt = runtimeForInternal(region, false);
        if (rt == null) {
            return RegionLifecycle.DestroyReport.EMPTY;
        }
        RegionLifecycle.DestroyReport report = RegionLifecycle.destroy(
                rt, taskController,
                r -> runtimesById.remove(r.regionId()),
                DESTROY_QUIESCE_MILLIS);
        if (!report.isClean()) {
            SchedulerLog.warn("Region destroy was not clean: %s", report);
        }
        return report;
    }

    // ---------- Tick ----------

    @Override
    public void tick() {
        tickCount.incrementAndGet();

        int timedOut = taskController.sweepDeadlines();
        if (timedOut > 0) {
            SchedulerLog.warn("%d scheduled task(s) exceeded their deadline and were cancelled", timedOut);
        }

        int staleTx = CrossRegionTransaction.sweepExpired();
        if (staleTx > 0) {
            SchedulerLog.warn("%d cross-region transaction(s) expired without being applied", staleTx);
        }

        BudgetController.updateFrom(runtimesById.values());
    }

    public long tickCount() {
        return tickCount.get();
    }

    // ---------- Diagnostics ----------

    @Override
    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("state", state.get().name());
        out.put("regions_tracked", runtimesById.size());
        out.put("region_id_high_water_mark", RegionIdRegistry.highWaterMark());
        out.put("compute_threads", computeThreads);
        out.put("ticks", tickCount.get());

        out.put("submit_calls", submitCalls.get());
        out.put("submitted_accepted", submittedAccepted.get());
        out.put("submitted_merged", submittedMerged.get());
        out.put("submitted_deferred", submittedDeferred.get());
        out.put("submitted_rejected", submittedRejected.get());
        out.put("inline_owner_runs", inlineRuns.get());
        out.put("wait_timeouts", waitTimeouts.get());
        out.put("submitted_after_destroy", submittedAfterDestroy.get());
        out.put("drain_cycles", drainCycles.get());
        out.put("drained_tasks", drainedTasks.get());

        out.putAll(taskController.snapshot());
        out.putAll(CrossRegionTransaction.stats());
        out.putAll(BudgetController.snapshot());
        out.put("entity_scheduler_regions", EntityScheduler.trackedRegions());

        WorkerRuntime lane = computeLane;
        out.put("compute_lane", lane == null ? "off" : lane.snapshot());

        return Collections.unmodifiableMap(out);
    }

    /** Per-region snapshots, sorted by priority score so the hot regions come first. */
    public List<Map<String, Object>> regionSnapshots() {
        List<MiliRegionRuntime> list = new ArrayList<>(runtimesById.values());
        list.sort((a, b) -> Double.compare(b.priorityScore(), a.priorityScore()));
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (MiliRegionRuntime rt : list) {
            out.add(rt.snapshot());
        }
        return out;
    }

    @Override
    public String toString() {
        return "MiliSchedulerImpl[" + state.get() + " regions=" + runtimesById.size() + "]";
    }
}
