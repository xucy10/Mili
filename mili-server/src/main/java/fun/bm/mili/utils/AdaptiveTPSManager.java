package fun.bm.mili.utils;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import io.papermc.paper.threadedregions.TickRegionScheduler;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class AdaptiveTPSManager {

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicLong lastAdjustment = new AtomicLong(System.nanoTime());
    private static final AtomicReference<Double> currentLoadFactor = new AtomicReference<>(0.0);
    private static final AtomicLong adjustmentCount = new AtomicLong(0);

    private static final long MIN_TICK_INTERVAL_NS = 10_000_000L;  // 100 TPS
    private static final long MAX_TICK_INTERVAL_NS = 200_000_000L; // 5 TPS
    private static final long DEFAULT_INTERVAL_NS = 50_000_000L;   // 20 TPS
    private static final long ADJUSTMENT_COOLDOWN_NS = 500_000_000L; // 500ms
    private static final double LOAD_SMOOTHING_FACTOR = 0.3;
    private static final double AGGRESSIVE_LOAD_THRESHOLD = 0.8;
    private static final double LOW_LOAD_THRESHOLD = 0.2;

    public static void start() {
        if (!RegionBalancerConfig.enabled) return;
        if (RUNNING.getAndSet(true)) return;

        Thread t = new Thread(AdaptiveTPSManager::runLoop, "Mili-AdaptiveTPS");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();

        LogUtils.getClassLogger().info("[Mili] AdaptiveTPSManager v2.0 started");
    }

    private static void runLoop() {
        while (RUNNING.get()) {
            try {
                Thread.sleep(250);

                if (!RegionBalancerConfig.enabled) continue;

                long now = System.nanoTime();
                if (now - lastAdjustment.get() < ADJUSTMENT_COOLDOWN_NS) continue;

                performAdjustment();
                lastAdjustment.set(now);
                adjustmentCount.incrementAndGet();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                LogUtils.getClassLogger().error("[Mili] AdaptiveTPS error", ex);
            }
        }
    }

    private static void performAdjustment() {
        double rawLoad = computeGlobalLoad();
        double smoothedLoad = smoothLoad(rawLoad);
        currentLoadFactor.set(smoothedLoad);

        long interval = computeOptimalInterval(smoothedLoad);

        try {
            TickRegionScheduler.TIME_BETWEEN_TICKS = interval;
        } catch (Exception e) {
            LogUtils.getClassLogger().warn("[Mili] Failed to set tick interval", e);
        }

        if (adjustmentCount.get() % 20 == 0) {
            LogUtils.getClassLogger().debug(
                    "[Mili] AdaptiveTPS: load={}%, interval={}ms, tps={}",
                    (int)(smoothedLoad * 100),
                    interval / 1_000_000L,
                    String.format("%.1f", 1_000_000_000.0 / interval)
            );
        }
    }

    private static double computeGlobalLoad() {
        var snapshots = RegionLoadMonitor.getAllSnapshots();
        if (snapshots.isEmpty()) return 0.0;

        double weightedSum = 0;
        double totalWeight = 0;

        for (var snap : snapshots) {
            double load = snap.loadFactor();
            double weight = Math.max(0.1, load);
            weightedSum += load * weight;
            totalWeight += weight;
        }

        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    private static double smoothLoad(double newLoad) {
        double previous = currentLoadFactor.get();
        return previous + LOAD_SMOOTHING_FACTOR * (newLoad - previous);
    }

    private static long computeOptimalInterval(double load) {
        double baseMultiplier = 1.0;

        if (load > AGGRESSIVE_LOAD_THRESHOLD) {
            double excess = load - AGGRESSIVE_LOAD_THRESHOLD;
            baseMultiplier += excess * 2.0;
        } else if (load < LOW_LOAD_THRESHOLD) {
            double deficit = LOW_LOAD_THRESHOLD - load;
            baseMultiplier -= deficit * 0.8;
        } else {
            baseMultiplier += (load - 0.5) * 0.6;
        }

        long interval = (long)(DEFAULT_INTERVAL_NS * Math.max(0.4, Math.min(3.0, baseMultiplier)));

        return clamp(interval, MIN_TICK_INTERVAL_NS, MAX_TICK_INTERVAL_NS);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double getCurrentLoadFactor() {
        return currentLoadFactor.get();
    }

    public static long getCurrentIntervalNs() {
        return TickRegionScheduler.TIME_BETWEEN_TICKS;
    }

    public static double getCurrentTPS() {
        long interval = getCurrentIntervalNs();
        return interval > 0 ? 1_000_000_000.0 / interval : 20.0;
    }

    public static long getAdjustmentCount() {
        return adjustmentCount.get();
    }

    public static boolean isRunning() {
        return RUNNING.get();
    }

    public static void shutdown() {
        RUNNING.set(false);
        LogUtils.getClassLogger().info("[Mili] AdaptiveTPSManager stopped");
    }
}