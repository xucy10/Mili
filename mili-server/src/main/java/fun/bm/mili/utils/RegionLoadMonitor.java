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
 * <p>
 * Tracks per-region tick duration using a sliding window to compute average load.
 * Thread-safe: all operations are lock-free (atomic arrays).
 * <p>
 * <b>Metric contract (M4 decision — do not silently redefine).</b>
 * The sample fed to {@link #afterTick(Object, long)} is the duration of the <b>whole
 * region tick</b>: every world the region owns, plus the scheduler's own overhead.  In
 * other words a per-region MSPT.  It is <b>not</b> "how long Mili spent working", and it
 * must never be handed the cost of a Mili drain.  Three pieces of existing code depend on
 * this reading:
 * <ul>
 *   <li>{@link AdaptiveTPSManager} feeds {@code loadFactor} straight into
 *       {@code TickRegionScheduler.TIME_BETWEEN_TICKS}, scaled from a 50&nbsp;ms / 20&nbsp;TPS
 *       base.  A "Mili work only" figure would make the server slow its <i>own</i> tick
 *       cadence according to how busy an unrelated queue happened to be.</li>
 *   <li>The default thresholds — {@code low-load-threshold-ms = 2} and
 *       {@code high-load-threshold-ms = 20} — are only meaningful as MSPT: 20&nbsp;ms is
 *       40&nbsp;% of a tick (genuinely heavy), 2&nbsp;ms means the region has nothing to do.</li>
 *   <li>{@link SmartRegionManager} treats {@code loadFactor < 0.1} as "severely
 *       underloaded, worth migrating or merging" — again a judgement about the whole tick.</li>
 * </ul>
 * Mili's own drain cost is a <i>separate</i> metric and already has a home:
 * {@code MiliRegionRuntime.debt().recordTick(elapsed)} inside
 * {@link fun.bm.mili.scheduler.FoliaSchedulerAdapter#onRegionTick}.  The two must not be
 * conflated.
 * <p>
 * <b>Where the sample must come from.</b> The only legal producer is a bracket around the
 * region tick itself — {@link RegionBalancer#submitAndWait} when it owns that tick, or a
 * Minecraft-side hook that wraps the tick.  Folia owns the tick loop today, so this monitor
 * currently has <b>no</b> producer and reports zeros.  Those zeros are the honest answer;
 * do not paper over the gap by wiring in a metric that means something else, because the
 * consumers above act on the number (lowering TPS, migrating regions) rather than merely
 * displaying it.
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

            // Mili start - fix: floorMod keeps the index valid when writeIndex has wrapped
            int startIdx = Math.floorMod(writeIndex.get() - count, history.length());
            for (int i = 0; i < count; i++) {
                int idx = Math.floorMod(startIdx + i, history.length());
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

    // Mili start - fix: key by the stable region id instead of System.identityHashCode.
    // identityHashCode is not unique and is not shared with the scheduler / metrics /
    // cross-region / entity-budget subsystems (fix.md §8).
    private static final ConcurrentHashMap<Long, RegionStats> STATS = new ConcurrentHashMap<>();

    private static long keyOf(Object schedule) {
        return RegionIdRegistry.idOf(schedule);
    }

    /**
     * Called before a region tick starts.
     * <p>
     * Records nothing: the sample is handed over as a single value in
     * {@link #afterTick(Object, long)}, so no begin/end pair is needed.  Kept because the
     * tick bracket is symmetric and a future producer may want the start hook.
     */
    public static void beforeTick(Object schedule) {
        if (!RegionBalancerConfig.enabled) return;
        // Nothing to record here; the elapsed time is supplied to afterTick.
    }

    /**
     * Called after a region tick completes.
     *
     * @param schedule     the region schedule
     * @param elapsedNanos wall-clock time spent in this tick for the <b>whole</b> region
     *                     — see the metric contract on this class.  This must <b>not</b> be
     *                     the cost of a Mili drain; that figure belongs to
     *                     {@code MiliRegionRuntime.debt().recordTick(...)}.
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

    public static java.util.Map<Long, RegionLoadSnapshot> getAllSnapshotMap() {
        java.util.Map<Long, RegionLoadSnapshot> result = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<Long, RegionStats> entry : STATS.entrySet()) {
            result.put(entry.getKey(), entry.getValue().snapshot());
        }
        return result;
    }

    /** Number of regions tracked. Used by the lifecycle teardown checks. */
    public static int trackedRegions() {
        return STATS.size();
    }

    /** Drop every tracked region; used on shutdown (fix.md §9). */
    public static void clear() {
        STATS.clear();
    }
}