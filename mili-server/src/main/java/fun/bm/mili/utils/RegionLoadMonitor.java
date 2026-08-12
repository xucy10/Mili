package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

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
        final AtomicLong runningSum = new AtomicLong(0);
        final AtomicInteger writeIndex = new AtomicInteger(0);
        final AtomicInteger filledCount = new AtomicInteger(0);

        RegionStats(int windowSize) {
            this.history = new AtomicLongArray(windowSize);
        }

        void record(long tickNanos) {
            // Mili start - fix: use floorMod to handle negative writeIndex after AtomicInteger overflow
            // getAndIncrement() wraps to Integer.MIN_VALUE at overflow, and Java's % operator
            // returns a negative result for negative dividends, causing ArrayIndexOutOfBoundsException.
            int idx = Math.floorMod(writeIndex.getAndIncrement(), history.length());
            // Mili end
            long old = history.getAndSet(idx, tickNanos);
            if (old > 0) {
                runningSum.addAndGet(-old);
            }
            runningSum.addAndGet(tickNanos);
            // Mili start - fix: use CAS to avoid filledCount exceeding history.length() due to race
            int currentFilled;
            do {
                currentFilled = filledCount.get();
                if (currentFilled >= history.length()) break;
            } while (!filledCount.compareAndSet(currentFilled, currentFilled + 1));
            // Mili end
        }

        RegionLoadSnapshot snapshot() {
            int count = Math.min(filledCount.get(), history.length());
            if (count == 0) {
                return new RegionLoadSnapshot(0, 0, 0, 0.0, false, true);
            }

            long sum = runningSum.get();
            long max = 0;
            long min = Long.MAX_VALUE;
            boolean foundValid = false; // Mili - fix: track whether any valid sample was found

            int startIdx = (writeIndex.get() - count + history.length()) % history.length();
            for (int i = 0; i < count; i++) {
                int idx = (startIdx + i) % history.length();
                long v = history.get(idx);
                if (v <= 0) continue;
                foundValid = true; // Mili
                if (v > max) max = v;
                if (v < min) min = v;
            }
            // Mili start - fix: if no valid samples found, return empty snapshot
            if (!foundValid || sum == 0) {
                return new RegionLoadSnapshot(0, 0, 0, 0.0, false, true);
            }
            // Mili end

            long avg = sum / count;
            double thresholdHigh = RegionBalancerConfig.highLoadThresholdMs * 1_000_000.0;
            double thresholdLow = RegionBalancerConfig.lowLoadThresholdMs * 1_000_000.0;
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

    private static int keyOf(Object schedule) {
        return System.identityHashCode(schedule);
    }

    /**
     * Called before a region tick starts.
     */
    public static void beforeTick(Object schedule) {
        if (!RegionBalancerConfig.enabled) return;
        // Nothing to record here; timestamp is captured in afterTick
    }

    /**
     * Called after a region tick completes.
     *
     * @param schedule the region schedule
     * @param elapsedNanos total time spent in this tick
     */
    public static void afterTick(Object schedule, long elapsedNanos) {
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
    public static RegionLoadSnapshot getSnapshot(Object schedule) {
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
    public static double computePriority(Object schedule, long lastTickTime) {
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
    public static void remove(Object schedule) {
        if (schedule == null) return;
        STATS.remove(keyOf(schedule));
    }

    /**
     * Get snapshots of all tracked regions.
     */
    public static java.util.Collection<RegionLoadSnapshot> getAllSnapshots() {
        java.util.List<RegionLoadSnapshot> result = new java.util.ArrayList<>();
        for (RegionStats stats : STATS.values()) {
            result.add(stats.snapshot());
        }
        return result;
    }

    public static java.util.Map<Integer, RegionLoadSnapshot> getAllSnapshotMap() {
        java.util.Map<Integer, RegionLoadSnapshot> result = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<Integer, RegionStats> entry : STATS.entrySet()) {
            result.put(entry.getKey(), entry.getValue().snapshot());
        }
        return result;
    }
}