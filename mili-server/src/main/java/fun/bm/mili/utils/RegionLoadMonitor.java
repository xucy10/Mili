package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.ConcurrentHashMap;

public class RegionLoadMonitor {

    public record RegionLoadSnapshot(
            long avgTickNanos,
            long maxTickNanos,
            long minTickNanos,
            double loadFactor,
            boolean isHighLoad,
            boolean isLowLoad
    ) {
        public double avgTickMs() { return avgTickNanos / 1_000_000.0; }
        public double maxTickMs() { return maxTickNanos / 1_000_000.0; }
        public double minTickMs() { return minTickNanos / 1_000_000.0; }
    }

    private static final class RingBufferStats {
        final AtomicLongArray buffer;
        final AtomicInteger writePos;
        final AtomicInteger count;
        final int capacity;

        RingBufferStats(int windowSize) {
            this.capacity = windowSize;
            this.buffer = new AtomicLongArray(windowSize);
            this.writePos = new AtomicInteger(0);
            this.count = new AtomicInteger(0);
        }

        void record(long nanos) {
            int pos = writePos.getAndIncrement() % capacity;
            buffer.set(pos, nanos);

            int currentCount = count.get();
            if (currentCount < capacity) {
                count.compareAndSet(currentCount, currentCount + 1);
            }
        }

        RegionLoadSnapshot snapshot() {
            int samples = Math.min(count.get(), capacity);
            if (samples == 0) {
                return new RegionLoadSnapshot(0, 0, 0, 0.0, false, true);
            }

            long sum = 0;
            long max = Long.MIN_VALUE;
            long min = Long.MAX_VALUE;

            for (int i = 0; i < samples; i++) {
                long v = buffer.get(i);
                if (v <= 0) continue;

                sum += v;
                if (v > max) max = v;
                if (v < min) min = v;
            }

            if (sum == 0 || max == Long.MIN_VALUE) {
                return new RegionLoadSnapshot(0, 0, 0, 0.0, false, true);
            }

            long avg = sum / samples;

            double highThreshold = RegionBalancerConfig.highLoadThresholdMs * 1_000_000.0;
            double lowThreshold = RegionBalancerConfig.lowLoadThresholdMs * 1_000_000.0;

            double loadFactor = Math.min(1.0, avg / highThreshold);

            return new RegionLoadSnapshot(
                    avg, max == Long.MIN_VALUE ? 0 : max,
                    min == Long.MAX_VALUE ? 0 : min,
                    loadFactor,
                    avg > highThreshold,
                    avg < lowThreshold
            );
        }

        void reset() {
            writePos.set(0);
            count.set(0);
            for (int i = 0; i < capacity; i++) {
                buffer.set(i, 0);
            }
        }
    }

    private static final ConcurrentHashMap<Integer, RingBufferStats> STATS =
            new ConcurrentHashMap<>();

    private static int keyOf(Object schedule) {
        return System.identityHashCode(schedule);
    }

    public static void beforeTick(Object schedule) {
        if (!RegionBalancerConfig.enabled || schedule == null) return;
    }

    public static void afterTick(Object schedule, long elapsedNanos) {
        if (!RegionBalancerConfig.enabled || schedule == null) return;

        RingBufferStats stats = STATS.computeIfAbsent(keyOf(schedule),
                k -> new RingBufferStats(RegionBalancerConfig.historyWindowSize));
        stats.record(elapsedNanos);
    }

    @NotNull
    public static RegionLoadSnapshot getSnapshot(Object schedule) {
        if (schedule == null) {
            return new RegionLoadSnapshot(0, 0, 0, 0.0, false, true);
        }

        RingBufferStats stats = STATS.get(keyOf(schedule));
        return stats != null ? stats.snapshot() :
                new RegionLoadSnapshot(0, 0, 0, 0.0, false, true);
    }

    public static double computePriority(Object schedule, long lastTickTime) {
        RegionLoadSnapshot snap = getSnapshot(schedule);
        double loadFactor = snap.loadFactor();

        long now = System.nanoTime();
        long overdue = now - lastTickTime;
        double overdueFactor = Math.min(1.0, overdue / 50_000_000.0);

        return loadFactor + overdueFactor * 0.5;
    }

    public static void remove(Object schedule) {
        if (schedule == null) return;
        STATS.remove(keyOf(schedule));
    }

    public static java.util.Collection<RegionLoadSnapshot> getAllSnapshots() {
        java.util.List<RegionLoadSnapshot> result = new java.util.ArrayList<>();
        for (RingBufferStats stats : STATS.values()) {
            result.add(stats.snapshot());
        }
        return result;
    }

    public static int trackedRegionCount() {
        return STATS.size();
    }

    public static void clearAll() {
        STATS.clear();
    }

    public static void resetAll() {
        for (RingBufferStats stats : STATS.values()) {
            stats.reset();
        }
    }
}