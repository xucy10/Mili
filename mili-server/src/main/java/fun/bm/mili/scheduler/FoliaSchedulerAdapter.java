package fun.bm.mili.scheduler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private static final Logger LOGGER = Logger.getLogger("Mili");

    /** Default bounded wait used by {@link #executeOnOwningThread}. */
    private static final long DEFAULT_WAIT_MILLIS = 50L;

    /** Guards {@link #scheduleBootstrap()} so only one region thread ever submits it. */
    private static final AtomicBoolean BOOTSTRAP_SCHEDULED = new AtomicBoolean();

    /**
     * Called from the region's own tick, on the region's owning thread.
     * Drains the region queue within the current budget and records debt.
     * <p>
     * Mili start - fix: three things changed here.
     * <ul>
     *   <li>the tick is opened with the region's own epoch instead of a bare reset, so this
     *       is the <b>only</b> place that may clear the consumed counter;</li>
     *   <li>{@code drainOwnedTrusted} is used — this thread owns the region by
     *       construction, so the ownership probe is skipped rather than guessed;</li>
     *   <li>the trailing {@code consume(elapsed)} was removed.  {@code drainOwned} already
     *       charges every task it runs, and the window {@code elapsed} contains exactly
     *       those task costs, so charging it again roughly doubled reported utilisation
     *       and made the PI controller shrink the budget for work that was never done.</li>
     * </ul>
     *
     * @return number of tasks executed
     */
    public static int onRegionTick(Object region) {
        if (region == null) return 0;
        // Mili start - fix: the hook is invoked with a RegionizedWorldData, but Mili's
        // region identity is the ThreadedRegion (see RegionResolver).  RegionizedWorldData
        // and TickRegions.TickRegionData are unrelated final classes, so keying the runtime
        // registry by the world data would hand the same region two different Mili ids
        // depending on which path looked it up.  Resolve the authoritative identity first.
        Object identity = RegionResolver.currentTickRegion();
        if (identity == null) identity = region;

        MiliRegionRuntime runtime = MiliSchedulerImpl.runtimeFor(identity);
        if (runtime == null) return 0;

        runtime.budget().beginTick(runtime.nextTickEpoch());
        long begin = System.nanoTime();
        int executed = MiliSchedulerImpl.instance()
                .drainOwnedTrusted(identity, runtime.budget().budgetNanos());
        long elapsed = System.nanoTime() - begin;

        runtime.debt().recordTick(elapsed);
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
        installFoliaResolver();
        MiliSchedulerImpl.init();
    }

    /**
     * Mili start - fix: install the authoritative region ownership probe before the
     * scheduler can accept any work.
     * <p>
     * {@link RegionOwnership} documents three strategies (resolver, recorded owner thread,
     * reflective probe) but the resolver was never installed anywhere in the repository, so
     * in practice ownership was decided by the reflective probe — and, when that failed, by
     * {@code drainOwned} simply claiming the calling thread as the owner.  Asking Folia is
     * the only answer that cannot be wrong: "is this the region the current thread is
     * ticking?" is exactly {@code TickRegionScheduler.getCurrentRegion()}.
     * <p>
     * The probe accepts every identity shape the Mili codebase passes around (the
     * {@code ThreadedRegion}, its {@code TickRegionData}, or the region's
     * {@code RegionizedWorldData}) so callers do not have to agree on one.  On failure it
     * returns {@code false} — the safe direction.
     */
    private static void installFoliaResolver() {
        RegionOwnership.setResolver(region -> {
            try {
                io.papermc.paper.threadedregions.ThreadedRegionizer.ThreadedRegion<?, ?> current =
                        io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegion();
                if (current == null) return false;
                if (region == current) return true;
                if (region == current.getData()) return true;
                return region == io.papermc.paper.threadedregions.TickRegionScheduler
                        .getCurrentRegionizedWorldData();
            } catch (Throwable t) {
                // Authoritative source unavailable: never fall back to a guess.
                return false;
            }
        });
    }

    /**
     * Whether the Mili runtime is accepting work. Cheap enough to call every region tick.
     * <p>
     * Mili start - fix: <b>this is also the runtime's bootstrap hook.</b>  It is invoked
     * once per region tick by the Mili patch in {@code ServerLevel.tick}, and region ticks
     * only begin after the worlds are loaded — so it is both the earliest point at which
     * {@code MiliOptimizations.init()} can run safely and a point that cannot be skipped.
     * The previous (and only) entry point was an external bootstrap patch injected into
     * {@code DedicatedServer} before world load, which left world-scanning subsystems
     * staring at an empty world list.
     * <p>
     * While the runtime is {@code NEW}, the first call schedules initialisation onto the
     * <b>global</b> region thread and this tick still reports "not running"; later ticks
     * take the fast path.  Initialisation deliberately does not run inline: the caller is
     * a region thread and {@code init()} walks every world, which must not happen on a
     * thread that owns only one region.
     */
    public static boolean isRunning() {
        SchedulerState state = MiliSchedulerImpl.instance().getState();
        if (state == SchedulerState.RUNNING) return true;
        // Mili start - fix: only a pristine runtime bootstraps; PAUSED / SHUTDOWN must not
        // be resurrected by a tick that happens to arrive afterwards.
        if (state == SchedulerState.NEW) scheduleBootstrap();
        // Mili end
        return false;
    }

    /**
     * Submit {@code MiliOptimizations.init()} to the global region thread, once.
     * <p>
     * A submission failure (scheduler not ready yet) clears the guard so a later tick can
     * retry.  A failure <i>inside</i> {@code init()} does not: {@code init()} is itself
     * idempotent, so a repeat would be a silent no-op that only spams the log — the
     * {@code SEVERE} record below is the signal to look at instead.
     */
    private static void scheduleBootstrap() {
        if (!BOOTSTRAP_SCHEDULED.compareAndSet(false, true)) return;
        try {
            // Mili is a server core, not a Bukkit plugin: the internal dummy plugin is the
            // only Plugin instance available, and it reports isEnabled() == true.
            org.bukkit.plugin.Plugin plugin = org.leavesmc.leaves.plugin.MinecraftInternalPlugin.INSTANCE;
            org.bukkit.Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                try {
                    fun.bm.mili.MiliOptimizations.init(plugin);
                } catch (Throwable t) {
                    LOGGER.log(Level.SEVERE, "[Mili] Self-bootstrap failed", t);
                }
            });
        } catch (Throwable t) {
            BOOTSTRAP_SCHEDULED.set(false);
            LOGGER.log(Level.WARNING, "[Mili] Could not schedule self-bootstrap; will retry", t);
        }
    }

    public static void shutdown() {
        MiliSchedulerImpl.shutdown();
    }
}
