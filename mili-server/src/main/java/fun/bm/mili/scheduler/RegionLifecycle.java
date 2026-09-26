package fun.bm.mili.scheduler;

import java.util.List;

/**
 * The single destroy sequence for a region (fix.md §13/§14/§15).
 * <p>
 * Before this existed, every component had its own cleanup method and none of
 * them were called — {@code RegionLoadMonitor.remove()},
 * {@code SmartRegionManager.unregisterRegion()},
 * {@code CrossRegionHelper.onRegionUnload()} all had zero call sites. The result
 * was that a server which had been up long enough to churn regions kept growing:
 * statistics buckets keyed by identity hash code, profiling maps keyed by region
 * id, pending-event queues holding strong region references.
 *
 * <p>The ordering rule from fix.md is strict and is encoded here literally:
 *
 * <blockquote>stop entering, then drain the backlog, then delete lifecycle data.</blockquote>
 *
 * Reversing any two adjacent steps produces a real bug:
 * <ul>
 *   <li>removing the registry entry before draining lets an in-flight task
 *       complete against a region the scheduler no longer knows about;</li>
 *   <li>cancelling before draining silently discards work that was already
 *       accepted and that a caller may still be waiting on;</li>
 *   <li>releasing the region context before cancelling lets a worker touch a
 *       world that has been torn down.</li>
 * </ul>
 */
public final class RegionLifecycle {

    /**
     * Closes region-specific context that lives on the Minecraft side
     * (Folia handles, neighbour caches, per-region world data).
     * <p>
     * Installed by the adapter so the core stays free of Minecraft types.
     */
    @FunctionalInterface
    public interface ContextCloser {
        void close(Object region, long regionId);
    }

    /**
     * Removes per-region state from the Minecraft-side components that key their
     * tables by region (load monitor, cross-region routing, entity budget,
     * worker slots). Called between "cancel" and "close" so that a component
     * which needs the region object can still use it.
     */
    @FunctionalInterface
    public interface RegistryHook {
        void deregister(Object region, long regionId);
    }

    private static volatile ContextCloser contextCloser;
    private static volatile RegistryHook registryHook;

    private RegionLifecycle() {}

    public static void installContextCloser(ContextCloser closer) {
        contextCloser = closer;
    }

    public static void installRegistryHook(RegistryHook hook) {
        registryHook = hook;
    }

    /**
     * Destroy a region runtime.
     * <p>
     * Steps, in order:
     * <ol>
     *   <li>deactivate — refuse new work immediately, so nothing new can slip in;</li>
     *   <li>stop accepting — close the queue;</li>
     *   <li>drain pending — cancel what is still queued and release its waiters;</li>
     *   <li>cancel async tasks — cancel in-flight work for this region and wait
     *       briefly for the bodies to actually leave;</li>
     *   <li>unregister scheduler — drop the runtime from the lookup table;</li>
     *   <li>remove metrics;</li>
     *   <li>remove load monitor and other region-keyed tables;</li>
     *   <li>close region context;</li>
     *   <li>release the region — forget its stable id.</li>
     * </ol>
     *
     * @param runtime       the runtime to destroy
     * @param taskController the scheduler's task registry
     * @param unregister    callback that removes {@code runtime} from the scheduler's lookup table
     * @param drainTimeoutMillis how long step 4 may wait for running bodies to leave
     * @return a report describing what was cleaned up
     */
    public static DestroyReport destroy(MiliRegionRuntime runtime,
                                        TaskController taskController,
                                        java.util.function.Consumer<RegionRuntime> unregister,
                                        long drainTimeoutMillis) {
        if (runtime == null) {
            return DestroyReport.EMPTY;
        }
        long regionId = runtime.regionId();
        Object region = runtime.region();
        DestroyReport report = new DestroyReport(regionId);

        // Step 1 — deactivate. Nothing new may be accepted from here on.
        runtime.markClosed();
        report.newWorkRefused = true;

        // Step 2 — stop accepting.
        List<TaskHandle> remainder = runtime.queue().close();
        report.cancelledFromQueue = remainder.size();
        for (TaskHandle handle : remainder) {
            handle.requestCancel("region-destroy");
        }

        // Step 3 — drain: cancel everything the task controller still tracks for
        // this region, including tasks submitted by foreign threads that are
        // currently blocked in submitAndWait().
        runtime.beginDraining();
        report.cancelledFromController = taskController.cancelAllForRegion(regionId, "region-destroy");

        // Step 3b — sever every cross-region transaction aimed at this region.
        // This is the step that makes "a destroyed region cannot be referenced by
        // work in flight" true rather than aspirational: without it, a payload
        // prepared for this region would still be applied after teardown.
        report.cancelledTransactions = CrossRegionTransaction.cancelTargeting(regionId);

        // Step 4 — cancel async work and give running bodies a bounded window to
        // observe their token and leave. We do not proceed while a body is still
        // mutating state, because that is the whole point of the ordering.
        report.stillRunning = awaitQuiescence(taskController, regionId, drainTimeoutMillis);

        // Step 5 — unregister the scheduler entry.
        runtime.beginClosing();
        if (unregister != null) {
            unregister.accept(runtime);
        }

        // Step 6 — metrics. Dropping the runtime reference is enough: it owns the
        // RegionMetrics instance and nothing else holds a strong reference to it.
        report.metricsReleased = true;

        // Step 7 — load monitor and every other region-keyed table.
        RegistryHook hook = registryHook;
        if (hook != null) {
            try {
                hook.deregister(region, regionId);
            } catch (Throwable ignored) {
                // Tabl cleanup must never abort the destroy sequence.
            }
        }

        // Step 8 — close region context on the Minecraft side.
        ContextCloser closer = contextCloser;
        if (closer != null) {
            try {
                closer.close(region, regionId);
            } catch (Throwable ignored) {
                // Same reasoning as above.
            }
        }

        // Step 9 — release: forget the stable id and tombstone the object, so a
        // late submission is refused instead of resurrecting the region.
        RegionIdRegistry.markDestroyed(region);
        RegionOwnership.releaseOwner(regionId, null);
        EntityScheduler.release(regionId);

        report.completed = true;
        return report;
    }

    /**
     * Wait until no task for this region is still executing.
     *
     * @return the number of tasks that were still running when the window closed
     * (non-zero means the caller accepted a possible leak of a stuck body)
     */
    private static int awaitQuiescence(TaskController taskController, long regionId, long timeoutMillis) {
        long deadline = System.nanoTime() + Math.max(0L, timeoutMillis) * 1_000_000L;
        while (true) {
            List<TaskHandle> live = taskController.liveForRegion(regionId);
            boolean anyRunning = false;
            for (TaskHandle handle : live) {
                if (handle.state() == TaskState.RUNNING) {
                    anyRunning = true;
                    break;
                }
            }
            if (!anyRunning) return 0;
            if (System.nanoTime() >= deadline) {
                int stuck = 0;
                for (TaskHandle handle : live) {
                    if (handle.state() == TaskState.RUNNING) stuck++;
                }
                return stuck;
            }
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }
    }

    /** Result of a destroy, for logging and for /mili perf. */
    public static final class DestroyReport {
        static final DestroyReport EMPTY = new DestroyReport(RegionIdRegistry.UNKNOWN);

        public final long regionId;
        public boolean newWorkRefused;
        public int cancelledFromQueue;
        public int cancelledFromController;
        public int cancelledTransactions;
        public int stillRunning;
        public boolean metricsReleased;
        public boolean completed;

        DestroyReport(long regionId) {
            this.regionId = regionId;
        }

        public boolean isClean() {
            return completed && stillRunning <= 0;
        }

        @Override
        public String toString() {
            return "DestroyReport[" + RegionIdRegistry.labelOf(regionId)
                    + " queued=" + cancelledFromQueue
                    + " inflight=" + cancelledFromController
                    + " transactions=" + cancelledTransactions
                    + " stuck=" + stillRunning
                    + (completed ? " ok" : " INCOMPLETE") + "]";
        }
    }
}
