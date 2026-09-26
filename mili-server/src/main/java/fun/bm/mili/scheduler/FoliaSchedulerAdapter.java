package fun.bm.mili.scheduler;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Bridge between the Minecraft/Folia side and the Mili scheduler (fix.md §19).
 * <p>
 * This is the only class in the package that the server patches talk to. It
 * exposes the handful of entry points the server actually has:
 *
 * <pre>
 *   region tick      -> onRegionTick(region)          execute queued region work
 *   region unload    -> onRegionUnload(region)        run the destroy sequence
 *   server tick      -> onServerTick()                deadlines, rebalancing
 *   foreign thread   -> executeOnOwningThread(...)    hand work to the owner
 * </pre>
 *
 * <p>Everything here takes an opaque {@code Object region}, so the adapter can be
 * compiled and exercised without the game on the classpath, and so the scheduler
 * core never acquires a dependency on Folia internals that may move between
 * versions.
 *
 * <p>The adapter is intentionally thin. It contains no policy: budget sizes, queue
 * capacities, priorities and deadlines all live in the scheduler. If this class
 * starts growing conditionals, that is a sign policy is leaking out of the core.
 */
public final class FoliaSchedulerAdapter {

    /** Default bounded wait when a caller does not specify one. */
    private static final long DEFAULT_WAIT_MILLIS = 50L;

    private FoliaSchedulerAdapter() {}

    // ---------- Lifecycle ----------

    /** Start the Mili scheduler. Called once during server bootstrap. */
    public static void init() {
        MiliSchedulerImpl.init();
    }

    /** Stop the Mili scheduler. Called once during server shutdown. */
    public static void shutdown() {
        MiliSchedulerImpl.shutdown();
    }

    public static boolean isRunning() {
        return MiliSchedulerImpl.instance().isRunning();
    }

    public static SchedulerState state() {
        return MiliSchedulerImpl.instance().getState();
    }

    // ---------- Region lifecycle ----------

    /**
     * Run one region tick's worth of queued work.
     * <p>
     * Must be called from the region's own tick, on the region's owning thread.
     * This is the region-owned lane: work executed here may touch world state.
     *
     * @return how many tasks ran
     */
    public static int onRegionTick(Object region) {
        if (region == null) return 0;
        MiliSchedulerImpl scheduler = MiliSchedulerImpl.instance();
        if (!scheduler.isRunning()) return 0;

        MiliRegionRuntime runtime = MiliSchedulerImpl.peekRuntimeFor(region);
        if (runtime == null || !runtime.acceptsExecution()) return 0;

        long begin = System.nanoTime();
        runtime.budget().beginTick();

        int executed = scheduler.drainOwned(region, runtime.budget().remainingNanos());

        long elapsed = System.nanoTime() - begin;
        runtime.budget().charge(elapsed);
        runtime.markTicked();
        runtime.debt().setTargetNanos(runtime.budget().budgetNanos());

        if (runtime.budget().exhausted() && !runtime.queue().isEmpty()) {
            runtime.metrics().onBudgetOvershoot();
        }

        // Entity budget for the upcoming entity pass, sized from what is left.
        EntityScheduler entityScheduler = EntityScheduler.peek(runtime.regionId());
        if (entityScheduler != null) {
            entityScheduler.beginTick(runtime.budget().optionalNanos());
        }
        return executed;
    }

    /**
     * Region teardown. Runs the full fix.md §13 order, including cancelling
     * cross-region transactions aimed at this region so nothing is left pointing
     * at a dead region.
     */
    public static RegionLifecycle.DestroyReport onRegionUnload(Object region) {
        if (region == null) return RegionLifecycle.DestroyReport.EMPTY;
        return MiliSchedulerImpl.instance().destroyRegion(region);
    }

    /** One scheduler round: expire deadlines, rebalance budgets. */
    public static void onServerTick() {
        MiliSchedulerImpl scheduler = MiliSchedulerImpl.instance();
        if (!scheduler.isRunning()) return;
        scheduler.tick();
    }

    // ---------- Submission ----------

    /**
     * Queue work that must happen on a region's owning thread.
     * <p>
     * Never runs the work on the caller.
     */
    public static SubmissionResult scheduleOnOwningThread(Object region, Runnable work) {
        return MiliSchedulerImpl.instance().submit(region, work);
    }

    /**
     * Queue work with explicit scheduling hints.
     *
     * @param deadlineNanos absolute {@link System#nanoTime()} deadline, or
     *                      {@link TaskHandle#NO_DEADLINE}
     */
    public static SubmissionResult scheduleOnOwningThread(Object region, Runnable work,
                                                          int priority, Object mergeKey,
                                                          long deadlineNanos, String description) {
        return MiliSchedulerImpl.instance()
                .submit(region, work, priority, mergeKey, deadlineNanos, description);
    }

    /**
     * Run work on a region's owning thread, waiting for it.
     * <p>
     * If the caller already owns the region the work runs inline (fix.md §2.1).
     * Otherwise it is queued and waited on with a bound; on timeout it is
     * cancelled rather than executed on the wrong thread.
     *
     * @return {@code true} if the work ran to completion
     */
    public static boolean executeOnOwningThread(Object region, Runnable work) {
        return executeOnOwningThread(region, work, DEFAULT_WAIT_MILLIS);
    }

    public static boolean executeOnOwningThread(Object region, Runnable work, long timeoutMillis) {
        if (region == null || work == null) return false;
        return MiliSchedulerImpl.instance()
                .submitAndWait(region, work, Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS);
    }

    public static boolean cancelTask(long taskId, String reason) {
        return MiliSchedulerImpl.instance().cancel(taskId, reason);
    }

    // ---------- Region-affine state ----------

    /** Entity budget scheduler for a region, creating it on demand. */
    public static EntityScheduler entitySchedulerFor(Object region) {
        if (region == null) return null;
        long regionId = RegionIdRegistry.peek(region);
        if (regionId == RegionIdRegistry.UNKNOWN) return null;
        return EntityScheduler.forRegion(regionId);
    }

    /** Resolve the region owning a block position. {@code null} when unresolvable. */
    public static Object regionAtBlock(Object level, int blockX, int blockZ) {
        return RegionResolver.regionAtBlock(level, blockX, blockZ);
    }

    /** Stable id of the region owning a block position, or {@link RegionIdRegistry#UNKNOWN}. */
    public static long regionIdAtBlock(Object level, int blockX, int blockZ) {
        return RegionResolver.regionIdAtBlock(level, blockX, blockZ);
    }

    /** Stable id of a region handle, allocating one on first use. */
    public static long regionIdOf(Object region) {
        return RegionIdRegistry.idOf(region);
    }

    // ---------- Hook installation ----------

    /**
     * Install the authoritative ownership probe.
     * <p>
     * Until this is installed, {@link RegionOwnership} falls back to the owner
     * thread it records itself and then to reflective probing — both of which are
     * strictly weaker. The hook should be installed as early as possible.
     */
    public static void installOwnershipResolver(RegionOwnership.Resolver resolver) {
        RegionOwnership.installResolver(resolver);
    }

    /**
     * Install the coordinate -> region lookup used by cross-region routing.
     * <p>
     * Without it, cross-region target resolution returns {@code null} and the
     * operation is skipped rather than being misrouted to the source region.
     */
    public static void installRegionLocator(RegionResolver.Locator locator) {
        RegionResolver.install(locator);
    }

    /**
     * Install the Minecraft-side hooks used by the region destroy sequence:
     * per-region table cleanup and region context closing.
     */
    public static void installLifecycleHooks(RegionLifecycle.RegistryHook registryHook,
                                             RegionLifecycle.ContextCloser contextCloser) {
        RegionLifecycle.installRegistryHook(registryHook);
        RegionLifecycle.installContextCloser(contextCloser);
    }

    /** Reconfigure the async compute lane. Takes effect on the next {@link #init()}. */
    public static void configureComputeLane(boolean enabled, int threads) {
        MiliSchedulerImpl.instance().configureComputeLane(enabled, threads);
    }

    // ---------- Diagnostics ----------

    /** Every live region runtime, concrete type, for the perf command and metrics. */
    public static Collection<MiliRegionRuntime> runtimes() {
        return MiliSchedulerImpl.allRuntimes();
    }

    public static Map<String, Object> snapshot() {
        return MiliSchedulerImpl.instance().snapshot();
    }

    /** Helper for callers that want to log a destroy outcome uniformly. */
    public static Consumer<RegionLifecycle.DestroyReport> destroyLogger() {
        return report -> {
            if (report.isClean()) {
                SchedulerLog.debug("Region destroy clean: %s", report);
            } else {
                SchedulerLog.warn("Region destroy incomplete: %s", report);
            }
        };
    }
}
