package fun.bm.mili.utils;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import io.papermc.paper.threadedregions.TickRegionScheduler;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adaptive TPS Manager.
 * Dynamically adjusts {@link TickRegionScheduler#TIME_BETWEEN_TICKS}
 * based on real-time region load.
 * <p>
 * <b>Logic:</b>
 * <ul>
 *   <li>High global load → increase tick interval (slow down slightly)</li>
 *   <li>Low global load → decrease tick interval (speed up)</li>
 * </ul>
 * Keeps TPS within a safe range (10 ~ 50 TPS).
 */
public class AdaptiveTPSManager {

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

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
                Thread.sleep(1000); // check every second

                if (!RegionBalancerConfig.enabled) continue;

                double avgLoad = 0;
                int count = 0;
                for (RegionLoadMonitor.RegionLoadSnapshot snap : RegionLoadMonitor.getAllSnapshots()) {
                    avgLoad += snap.loadFactor();
                    count++;
                }

                if (count == 0) continue;

                avgLoad /= count;

                // Base 50ms = 20 TPS
                long base = 50_000_000L;
                // Higher load → larger interval (slow down slightly)
                long adjusted = (long) (base * (1.0 + avgLoad * 0.5));
                // Clamp 20ms ~ 100ms (50 TPS ~ 10 TPS)
                adjusted = Math.max(20_000_000L, Math.min(100_000_000L, adjusted));

                TickRegionScheduler.TIME_BETWEEN_TICKS = adjusted;

                LogUtils.getClassLogger().debug(
                        "AdaptiveTPS: avgLoad={}%, interval={}ms",
                        (int)(avgLoad * 100), adjusted / 1_000_000L);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                LogUtils.getClassLogger().error("AdaptiveTPS error", ex);
            }
        }
    }

    public static void shutdown() {
        RUNNING.set(false);
    }
}
