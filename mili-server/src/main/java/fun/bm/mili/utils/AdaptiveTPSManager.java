package fun.bm.mili.utils;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import fun.bm.mili.rust.RustCow;
import io.papermc.paper.threadedregions.TickRegionScheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class AdaptiveTPSManager {

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicLong CURRENT_INTERVAL = new AtomicLong(50_000_000L);

    private static final long MIN_INTERVAL_NS = 20_000_000L;
    private static final long MAX_INTERVAL_NS = 100_000_000L;
    private static final long BASE_INTERVAL_NS = 50_000_000L;

    private static final RustCow<Collection<RegionLoadMonitor.RegionLoadSnapshot>> SNAPSHOT_CACHE =
            RustCow.owned(new ArrayList<>());

    public static void start() {
        if (!RegionBalancerConfig.enabled) return;
        if (RUNNING.getAndSet(true)) return;

        Thread t = new Thread(AdaptiveTPSManager::runLoop, "AdaptiveTPS-Manager");
        t.setDaemon(true);
        t.start();

        LogUtils.getClassLogger().info("AdaptiveTPSManager started");
    }

    private static void runLoop() {
        while (RUNNING.get()) {
            try {
                Thread.sleep(1000);

                if (!RegionBalancerConfig.enabled) continue;

                double avgLoad = 0;
                int count = 0;
                for (RegionLoadMonitor.RegionLoadSnapshot snap : RegionLoadMonitor.getAllSnapshots()) {
                    avgLoad += snap.loadFactor();
                    count++;
                }

                if (count == 0) continue;

                avgLoad /= count;

                long adjusted = (long) (BASE_INTERVAL_NS * (1.0 + avgLoad * 0.5));
                adjusted = Math.max(MIN_INTERVAL_NS, Math.min(MAX_INTERVAL_NS, adjusted));

                CURRENT_INTERVAL.set(adjusted);
                TickRegionScheduler.TIME_BETWEEN_TICKS = adjusted;

                LogUtils.getClassLogger().debug(
                        "AdaptiveTPS: avgLoad={}%, interval={}ms",
                        (int) (avgLoad * 100), adjusted / 1_000_000L);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                LogUtils.getClassLogger().error("AdaptiveTPS error", ex);
            }
        }
    }

    static long getCurrentInterval() {
        return CURRENT_INTERVAL.get();
    }

    public static void shutdown() {
        RUNNING.set(false);
    }
}