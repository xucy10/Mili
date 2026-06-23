package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Region load monitor.
 * Tracks per-region tick duration using a sliding window to compute average load.
 * Thread-safe: all operations are lock-free (atomic arrays).
 */
public class RegionLoadMonitor {

    /**
     * Immutable snapshot of a region's load statistics.
     */
    public record RegionLoadSnapshot(
            long avgTickNanos,
            long maxTickNanos,
            long minTickNanos,
            double loadFactor, // 0.0 ~ 1.0, higher = heavier
            boolean isHighLoad,
            boolean isLowLoad
    ) {}

    private static final class RegionStats {
        final AtomicLongArray history;
        final AtomicInteger writeIndex = new AtomicInteger(0);
        final AtomicInteger filledCount = new AtomicInteger(0);

        RegionStats(int windowSize) {
            this.history = new AtomicLongArray(windowSize);
        }

        void record(long tickNanos) {
            int idx = writeIndex.getAndIncrement() % history.length();
            history.set(idx, tickNanos);
            if (filledCount.get() < history.length()) {
                filledCount.incrementAndGet();
            }
        }

        RegionLoadSnapshot snapshot() {
            int count = filledCount.get();
            if (count == 0) {
                return new RegionLoadSnapshot(0, 0, 0, 0.0, false, true);
            }

            long sum = 0;
            long max = 0;
            long min = Long.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                long v = history.get(i);
                if (v <= 0) continue;
                sum += v;
                if (v > max) max = v;
                if (v < min) min = v;
            }
            if (sum == 0) {
                return new RegionLoadSnapshot(0, 0, 0, 0.0, false, true);
            }

            long avg = sum / count;
            double thresholdHigh = RegionBalancerConfig.highLoadThresholdMs * 1_000_000.0;
            double thresholdLow = RegionBalancerConfig.lowLoadThresholdMs * 1_000_000.0;
            // loadFactor: ratio of avg to thresholdHigh, capped at 1.0
            double loadFactor = Math.min(1.0, avg / thresholdHigh);
            return new RegionLoadSnapshot(
                    avg, max, min, loadFactor,
                    avg > thresholdHigh, avg < thresholdLow
            );
        }
    }

    // Key: RegionSchedule hashCode (each region schedule is a unique instance)
    private static final java.util.concurrent.ConcurrentHashMap<Integer, RegionStats> STATS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static int keyOf(TickRegionScheduler.RegionSchedule schedule) {
        return System.identityHashCode(schedule);
    }

    /**
     * Called before a region tick starts.
     */
    public static void beforeTick(TickRegionScheduler.RegionSchedule schedule) {
        if (!RegionBalancerConfig.enabled) return;
        // Nothing to record here; timestamp is captured in afterTick
    }

    /**
     * Called after a region tick completes.
     *
     * @param schedule the region schedule
     * @param elapsedNanos total time spent in this tick
     */
    public static void afterTick(TickRegionScheduler.RegionSchedule schedule, long elapsedNanos) {
        if (!RegionBalancerConfig.enabled) return;
        if (schedule == null) return;

        RegionStats stats = STATS.computeIfAbsent(keyOf(schedule), k ->
                new RegionStats(RegionBalancerConfig.historyWindowSize));
        stats.record(elapsedNanos);
    }

    /**
     * Get the current load snapshot for a region.
     */
    @NotNull
    public static RegionLoadSnapshot getSnapshot(TickRegionScheduler.RegionSchedule schedule) {
        if (schedule == null) {
            return new RegionLoadSnapshot(0, 0, 0, 0.0, false, true);
        }
        RegionStats stats = STATS.get(keyOf(schedule));
        return stats != null ? stats.snapshot() : new RegionLoadSnapshot(0, 0, 0, 0.0, false, true);
    }

    /**
     * Compute priority score for scheduling.  Higher = more urgent.
     * Based on load factor + starvation prevention.
     */
    public static double computePriority(TickRegionScheduler.RegionSchedule schedule, long lastTickTime) {
        RegionLoadSnapshot snap = getSnapshot(schedule);
        double loadFactor = snap.loadFactor();
        long overdue = System.nanoTime() - lastTickTime;
        // overdue bonus: if a region hasn't ticked for a while, boost priority
        double overdueFactor = Math.min(1.0, overdue / 50_000_000.0); // 50ms cap
        return loadFactor + overdueFactor * 0.5;
    }

    /**
     * Cleanup stats for a removed region schedule.
     */
    public static void remove(TickRegionScheduler.RegionSchedule schedule) {
        if (schedule == null) return;
        STATS.remove(keyOf(schedule));
    }
}
