package fun.bm.mili.perf;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class MiliTickSchedulerHook {

    private static final Logger LOGGER = LogUtils.getLogger();

    private MiliTickSchedulerHook() {}

    public static void onGlobalTick() {
        try {
            MiliAffinityAutoTuner.applyToCurrentThread();
            MiliRegionLoadMonitor.onSampleTick();
            MiliRegionLoadMonitor.maybeLogSummary();
            MiliTickProfiler.maybeLogSummary();
            MiliMemoryOptimizer.onGlobalTick();
            MiliRegionBalancer.onGlobalTick();
            EntitySafetyGuard.onGlobalTick();
        } catch (Throwable t) {
            LOGGER.debug("mili perf hook (global): {}", t.getMessage());
        }
    }

    public static long onRegionTickStart(String caller) {
        return org.leavesmc.leaves.util.MiliRegionSafetyGuard.beginRegionTick(caller);
    }

    public static void onRegionTickEnd(long token, String caller) {
        try {
            org.leavesmc.leaves.util.MiliRegionSafetyGuard.endRegionTick(token, caller);
        } catch (Throwable t) {
            LOGGER.debug("mili perf hook (region end): {}", t.getMessage());
        }
    }
}
