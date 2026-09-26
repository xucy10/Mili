package fun.bm.mili.scheduler;

import java.util.concurrent.TimeUnit;

/**
 * Phase-1 bridge between Minecraft/Folia and the Mili scheduler (fix.md §19).
 * <p>
 * Folia is not torn out in one go:
 * <pre>
 *     Minecraft -> MiliScheduler -> FoliaSchedulerAdapter -> Folia Scheduler
 * </pre>
 * Later the adapter is swapped for a full {@code MiliRegionRuntime} + worker pool.
 * Everything the adapter does is safe under Folia's model because it only ever runs
 * region work on the thread that already owns the region.
 */
public final class FoliaSchedulerAdapter {

    private FoliaSchedulerAdapter() {}

    /** Default bounded wait used by {@link #executeOnOwningThread}. */
    private static final long DEFAULT_WAIT_MILLIS = 50L;

    /**
     * Called from the region's own tick, on the region's owning thread.
     * Drains the region queue within the current budget and records debt.
     *
     * @return number of tasks executed
     */
    public static int onRegionTick(Object region) {
        if (region == null) return 0;
        MiliRegionRuntime runtime = MiliSchedulerImpl.runtimeFor(region);
        if (runtime == null) return 0;

        runtime.budget().beginTick();
        long begin = System.nanoTime();
        int executed = MiliSchedulerImpl.instance().drainOwned(region, runtime.budget().budgetNanos());
        long elapsed = System.nanoTime() - begin;

        runtime.debt().recordTick(elapsed);
        runtime.budget().consume(elapsed);
        runtime.markTicked();
        return executed;
    }

    /**
     * Run work that must happen on a region's owning thread.
     * <p>
     * If the caller already owns the region the work runs inline (fix.md §2.1). Otherwise
     * it is queued and the caller waits a bounded amount; on timeout the task is cancelled
     * rather than executed on the wrong thread.
     *
     * @return {@code true} if the work ran to completion
     */
    public static boolean executeOnOwningThread(Object region, Runnable work) {
        return executeOnOwningThread(region, work, DEFAULT_WAIT_MILLIS);
    }

    public static boolean executeOnOwningThread(Object region, Runnable work, long timeoutMillis) {
        if (region == null || work == null) return false;
        return MiliSchedulerImpl.instance().submitAndWait(region, work, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** Fire-and-forget: queue for the region, never run inline on a foreign thread. */
    public static SubmissionResult scheduleOnOwningThread(Object region, Runnable work) {
        if (region == null || work == null) return SubmissionResult.REJECTED;
        return MiliSchedulerImpl.instance().trySubmit(region, work);
    }

    /**
     * Region teardown. Runs the full fix.md §9 order, including cancelling transactions
     * that target this region so nothing is left pointing at a dead region.
     */
    public static void onRegionUnload(Object region) {
        if (region == null) return;
        long regionId = RegionIdRegistry.peek(region);
        if (regionId != 0L) {
            CrossRegionTransaction.cancelTargeting(regionId);
            EntityScheduler.release(regionId);
        }
        MiliRegionRuntime runtime = MiliSchedulerImpl.runtimeFor(region);
        if (runtime != null) {
            RegionLifecycle.destroy(runtime);
        }
        RegionIdRegistry.remove(region);
    }

    /** Called once per server tick: one scheduler round. */
    public static void onServerTick() {
        MiliSchedulerImpl.instance().tick();
        BudgetController.updateFrom(MiliSchedulerImpl.runtimes());
    }

    public static void init() {
        MiliSchedulerImpl.init();
    }

    /** Whether the Mili runtime is accepting work. Cheap enough to call every region tick. */
    public static boolean isRunning() {
        return MiliSchedulerImpl.instance().getState() == SchedulerState.RUNNING;
    }

    public static void shutdown() {
        MiliSchedulerImpl.shutdown();
    }
}
