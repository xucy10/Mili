package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import fun.bm.mili.scheduler.RegionIdRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Region load monitor.
 * Tracks per-region tick duration using a sliding window to compute average load.
 * Thread-safe: all operations are lock-free (atomic arrays).
 * <p>
 * Regions are keyed by their stable {@link RegionIdRegistry} id. The previous
 * implementation keyed by {@code System.identityHashCode(region)} — a 32-bit
 * value that is not unique and not shared with the scheduler, metrics or
 * cross-region routing. Two regions landing on the same hash silently shared one
 * statistics window (so a loaded region could inherit an idle region's numbers
 * and be scheduled accordingly), and because the key was an {@code Integer} with
 * no way back to the region, the entry could never be cleaned up.
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

    /** Keyed by the stable region id from {@link RegionIdRegistry}. */
    private static final ConcurrentHashMap<Long, RegionStats> STATS = new ConcurrentHashMap<>();

    /**
     * Stable key for a region.
     * <p>
     * Uses {@link RegionIdRegistry#peek} rather than {@code idOf}: a monitor that
     * is asked about a region nobody registered should report "no data", not
     * quietly allocate an identity for it.
     */
    private static long keyOf(Object schedule) {
        return RegionIdRegistry.peek(schedule);
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

        long regionId = RegionIdRegistry.idOf(schedule);
        if (regionId == RegionIdRegistry.UNKNOWN) return;

        RegionStats stats = STATS.computeIfAbsent(regionId, k ->
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
     * <p>
     * Previous behaviour: this method existed but had no callers anywhere in the
     * repository, so a server that churned regions accumulated one statistics
     * window per region that had ever ticked. It is now wired into the region
     * destroy sequence through {@link #removeById(long)}.
     */
    public static void remove(Object schedule) {
        if (schedule == null) return;
        long regionId = keyOf(schedule);
        if (regionId != RegionIdRegistry.UNKNOWN) {
            STATS.remove(regionId);
        }
    }

    /** Cleanup by stable id — used by the region destroy hook. */
    public static void removeById(long regionId) {
        if (regionId == RegionIdRegistry.UNKNOWN) return;
        STATS.remove(regionId);
    }

    /** Number of regions currently tracked. Diagnostics; used to verify cleanup works. */
    public static int trackedRegions() {
        return STATS.size();
    }

    /** Drop all statistics. Shutdown only. */
    public static void clear() {
        STATS.clear();
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

    public static java.util.Map<Long, RegionLoadSnapshot> getAllSnapshotMap() {
        java.util.Map<Long, RegionLoadSnapshot> result = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<Long, RegionStats> entry : STATS.entrySet()) {
            result.put(entry.getKey(), entry.getValue().snapshot());
        }
        return result;
    }
}