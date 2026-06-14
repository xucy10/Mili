package fun.bm.mili.perf;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * mili - Bootstrap hook for the perf monitoring stack.
 *
 * <p>Wires the {@link MiliRegionLoadMonitor}, {@link MiliTickProfiler}
 * and {@link MiliRegionSafetyGuard} into Folia's tick path. The
 * actual integration is done through:
 * <ul>
 *   <li>A patch on Folia's {@code RegionizedServer.tickServer} that
 *       calls {@link #onGlobalTick()} at the start of every global
 *       region tick.</li>
 *   <li>A patch on {@code ServerLevel.tick} that calls
 *       {@link #onRegionTick()} when each region begins its tick.</li>
 * </ul>
 *
 * <p>This class is a thin facade - the heavy lifting lives in the
 * patched code. The methods here are guarded so that the patches
 * always call into safe, no-throw code paths.
 */
public final class MiliTickSchedulerHook {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MiliTickSchedulerHook() {
    }

    /**
     * Called once per global region tick (default 50ms). Samples the
     * region load, drains the profiler summary queue, and applies the
     * resolved CPU affinity to the global region thread on its first
     * invocation.
     */
    public static void onGlobalTick() {
        try {
            MiliAffinityAutoTuner.applyToCurrentThread();
            MiliRegionLoadMonitor.onSampleTick();
            MiliRegionLoadMonitor.maybeLogSummary();
            MiliTickProfiler.maybeLogSummary();
            MiliMemoryOptimizer.onGlobalTick();
        } catch (Throwable t) {
            // never crash a global tick
            LOGGER.debug("mili perf hook (global): {}", t.getMessage());
        }
    }

    /**
     * Called at the start of each region tick. Returns a token that
     * MUST be passed to {@link #onRegionTickEnd(long, String)} so the
     * safety guard can measure how long the tick took.
     */
    public static long onRegionTickStart(String caller) {
        return org.leavesmc.leaves.util.MiliRegionSafetyGuard.beginRegionTick(caller);
    }

    /**
     * Called at the end of each region tick. See
     * {@link org.leavesmc.leaves.util.MiliRegionSafetyGuard#endRegionTick(long, String)}.
     */
    public static void onRegionTickEnd(long token, String caller) {
        try {
            org.leavesmc.leaves.util.MiliRegionSafetyGuard.endRegionTick(token, caller);
        } catch (Throwable t) {
            LOGGER.debug("mili perf hook (region end): {}", t.getMessage());
        }
    }
}
