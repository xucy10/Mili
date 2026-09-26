package fun.bm.mili.scheduler;

import fun.bm.mili.utils.RegionLoadMonitor;

/**
 * Enforces one destruction order for every region (fix.md §9):
 * <pre>
 *     1. deactivate()
 *     2. stop accepting new tasks
 *     3. drain pending tasks
 *     4. cancel async tasks
 *     5. remove scheduler registrations
 *     6. remove metrics
 *     7. remove load monitor
 *     8. close region context
 *     9. release region
 * </pre>
 * The forbidden outcome is "registry entry removed while tasks are still outstanding".
 * This class therefore never removes registrations before the queue is empty and every
 * live task has been cancelled.
 */
public final class RegionLifecycle {

    private RegionLifecycle() {}

    /** Result of a teardown, useful for diagnostics and tests. */
    public record DestroyReport(
            long regionId,
            int drainedExecuted,
            int drainedCancelled,
            int asyncCancelled,
            boolean registrationsRemoved
    ) {}

    /**
     * Run the full teardown.
     * <p>
     * Step 3 (drain) only <i>executes</i> tasks when the calling thread owns the region;
     * otherwise queued work is cancelled, because running a dying region's work on a
     * foreign thread is exactly the ownership violation fix.md forbids.
     */
    public static DestroyReport destroy(RegionRuntime runtime) {
        if (runtime == null) return null;

        long regionId = runtime.regionId();
        Object region = runtime.region();

        // 1 + 2: deactivate and stop accepting new work.
        if (runtime instanceof MiliRegionRuntime rt) {
            rt.tryDeactivate();
            rt.tryEnterDraining();
        }

        // 3: drain pending tasks.
        int executed = 0;
        boolean owned = RegionOwnership.isOwnedByCurrentThread(region);
        RegionWorkQueue queue = runtime.queue();
        TaskHandle handle;
        if (owned) {
            long deadline = System.nanoTime() + runtime.budget().remainingNanos();
            while ((handle = queue.poll()) != null) {
                if (System.nanoTime() > deadline) {
                    handle.cancel("destroy-budget-exhausted");
                    runtime.metrics().onCancelled();
                    continue;
                }
                try {
                    handle.runBody();
                    executed++;
                    runtime.metrics().onExecuted(handle.elapsedNanos());
                } catch (Throwable t) {
                    runtime.metrics().onCancelled();
                }
                TaskController.release(handle);
            }
        }
        int drainedCancelled = queue.clearAndCancel("region-destroy");

        // 4: cancel async tasks still registered for this region.
        int asyncCancelled = TaskController.cancelAllForRegion(regionId);

        // 5: remove scheduler registrations.
        MiliSchedulerImpl.unregister(runtime);

        // 6: metrics are owned by the runtime; drop the recorded ownership too.
        RegionOwnership.releaseOwner(region, null);

        // 7: remove load-monitor state.
        RegionLoadMonitor.remove(region);

        // 8 + 9: close and release.
        if (runtime instanceof MiliRegionRuntime rt) {
            rt.tryClose();
        }
        RegionIdRegistry.remove(region);
        RegionIdRegistry.purgeStale();

        return new DestroyReport(regionId, executed, drainedCancelled, asyncCancelled, true);
    }
}
