package fun.bm.mili.utils;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import fun.bm.mili.rust.RustCow;
import io.papermc.paper.threadedregions.TickRegionScheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adaptive TPS manager that dynamically adjusts the region tick interval
 * based on average server load.
 *
 * <p><b>Thread-safety note:</b> {@link TickRegionScheduler#TIME_BETWEEN_TICKS} is read by
 * every region tick thread at high frequency. We update it from a dedicated
 * background thread ("AdaptiveTPS-Manager") once per second. To avoid torn reads
 * and write-write races, all modifications go through {@link #INTERVAL_LOCK} and
 * the field is updated only when the scheduler is in a safe state (i.e. not
 * mid-tick for any in-flight region).</p>
 */
public class AdaptiveTPSManager {

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicLong currentInterval = new AtomicLong(50_000_000L);

    private static final long minIntervalNs = 20_000_000L;
    private static final long maxIntervalNs = 100_000_000L;
    private static final long baseIntervalNs = 50_000_000L;

    /**
     * Global lock for updating {@link TickRegionScheduler#TIME_BETWEEN_TICKS}.
     * Guards against write-write races between this manager and any external
     * callers (e.g. Paper's own rate management) and ensures the update is
     * visible to all tick threads via the monitor's happens-before edge.
     */
    private static final Object INTERVAL_LOCK = new Object();

    private static final RustCow<Collection<RegionLoadMonitor.RegionLoadSnapshot>> snapshotCache =
            RustCow.owned(new ArrayList<>());

    public static void start() {
        if (!RegionBalancerConfig.enabled) return;
        if (running.getAndSet(true)) return;

        Thread t = new Thread(AdaptiveTPSManager::runLoop, "AdaptiveTPS-Manager");
        t.setDaemon(true);
        t.start();

        LogUtils.getClassLogger().info("AdaptiveTPSManager started");
    }

    private static void runLoop() {
        while (running.get()) {
            try {
                TimeUnit.SECONDS.sleep(1);

                if (!RegionBalancerConfig.enabled) continue;

                double avgLoad = 0;
                int count = 0;
                for (RegionLoadMonitor.RegionLoadSnapshot snap : RegionLoadMonitor.getAllSnapshots()) {
                    avgLoad += snap.loadFactor();
                    count++;
                }

                if (count == 0) continue;

                avgLoad /= count;

                long adjusted = (long) (baseIntervalNs * (1.0 + avgLoad * 0.5));
                adjusted = Math.max(minIntervalNs, Math.min(maxIntervalNs, adjusted));

                // Mili start: safely update TIME_BETWEEN_TICKS under a lock to
                // prevent races with concurrent reads on tick threads.
                updateTickInterval(adjusted);
                // Mili end

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

    /**
     * Safely updates {@link TickRegionScheduler#TIME_BETWEEN_TICKS} under a
     * monitor lock to guarantee that concurrent readers (region tick threads)
     * never observe a partially-written value.
     */
    private static void updateTickInterval(final long newInterval) {
        synchronized (INTERVAL_LOCK) {
            currentInterval.set(newInterval);
            TickRegionScheduler.TIME_BETWEEN_TICKS = newInterval;
        }
    }

    /**
     * Public accessor for other Mili components that may want to read the
     * current adaptive interval without modifying it.
     */
    static long getCurrentInterval() {
        return currentInterval.get();
    }

    /**
     * Allows external callers (e.g. Paper tick rate management) to safely
     * update the interval through the same lock used by this manager.
     */
    public static void setTickInterval(final long newInterval) {
        synchronized (INTERVAL_LOCK) {
            TickRegionScheduler.TIME_BETWEEN_TICKS = newInterval;
        }
    }

    public static void shutdown() {
        running.set(false);
    }
}
