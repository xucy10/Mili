package fun.bm.mili.utils;

import fun.bm.mili.chunk.MiliChunkSystem;
import com.mojang.logging.LogUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PerformanceCollector {

    private PerformanceCollector() {}

    private static final ConcurrentHashMap<String, Metric> METRICS = new ConcurrentHashMap<>();
    private static final AtomicLong collectionStartTime = new AtomicLong(System.nanoTime());

    public static void init() {
        collectionStartTime.set(System.nanoTime());
        METRICS.clear();
        LogUtils.getLogger().info("[Mili] PerformanceCollector initialized");
    }

    public static void recordMetric(String name, long value) {
        ((ValueMetric) METRICS.computeIfAbsent(name, k -> new ValueMetric(k))).record(value);
    }

    public static void incrementCounter(String name) {
        incrementCounter(name, 1);
    }

    public static void incrementCounter(String name, long delta) {
        ((CounterMetric) METRICS.computeIfAbsent(name, k -> new CounterMetric(k))).increment(delta);
    }

    public static void startTiming(String name) {
        TimingMetric metric = (TimingMetric) METRICS.computeIfAbsent(
                name, k -> new TimingMetric(k)
        );
        metric.start();
    }

    public static void stopTiming(String name) {
        Metric metric = METRICS.get(name);
        if (metric instanceof TimingMetric timing) {
            timing.stop();
        }
    }

    public static Optional<Metric> getMetric(String name) {
        return Optional.ofNullable(METRICS.get(name));
    }

    public static long getCounterValue(String name) {
        Metric metric = METRICS.get(name);
        if (metric instanceof CounterMetric counter) {
            return counter.getValue();
        }
        return 0;
    }

    public static double getAverage(String name) {
        Metric metric = METRICS.get(name);
        if (metric != null) {
            return metric.getAverage();
        }
        return 0.0;
    }

    public static Map<String, Object> getAllMetrics() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.putAll(MiliChunkSystem.getStats());
        result.putAll(MemoryOptimizer.getStats());
        result.putAll(RegionBalancer.getStats());
        result.putAll(SmartRegionManager.getStats());
        result.putAll(CrossRegionHelper.getStats());

        for (Map.Entry<String, Metric> entry : METRICS.entrySet()) {
            Metric metric = entry.getValue();
            if (metric instanceof CounterMetric counter) {
                result.put("metric_" + entry.getKey(), counter.getValue());
            } else {
                result.put("metric_" + entry.getKey(), String.format("%.2f", metric.getAverage()));
            }
        }

        long uptimeSeconds = (System.nanoTime() - collectionStartTime.get()) / 1_000_000_000L;
        result.put("uptime_seconds", uptimeSeconds);
        result.put("total_custom_metrics", METRICS.size());

        return result;
    }

    public static void resetMetrics() {
        for (Metric metric : METRICS.values()) {
            metric.reset();
        }
        collectionStartTime.set(System.nanoTime());
    }

    public static void removeMetric(String name) {
        METRICS.remove(name);
    }

    public interface Metric {
        String getName();
        double getAverage();
        void reset();
    }

    public static final class ValueMetric implements Metric {
        private final String name;
        private final AtomicLong sum = new AtomicLong(0);
        private final AtomicLong count = new AtomicLong(0);

        ValueMetric(String name) {
            this.name = name;
        }

        @Override
        public String getName() { return name; }

        void record(long value) {
            sum.addAndGet(value);
            count.incrementAndGet();
        }

        @Override
        public double getAverage() {
            long c = count.get();
            return c == 0 ? 0.0 : (double) sum.get() / c;
        }

        @Override
        public void reset() {
            sum.set(0);
            count.set(0);
        }
    }

    public static final class CounterMetric implements Metric {
        private final String name;
        private final AtomicLong value = new AtomicLong(0);

        CounterMetric(String name) {
            this.name = name;
        }

        @Override
        public String getName() { return name; }

        void increment(long delta) {
            value.addAndGet(delta);
        }

        long getValue() {
            return value.get();
        }

        @Override
        public double getAverage() {
            return value.get();
        }

        @Override
        public void reset() {
            value.set(0);
        }
    }

    public static final class TimingMetric implements Metric {
        private final String name;
        private final AtomicLong totalTimeNs = new AtomicLong(0);
        private final AtomicLong count = new AtomicLong(0);
        private final ThreadLocal<Long> startTime = new ThreadLocal<>();

        TimingMetric(String name) {
            this.name = name;
        }

        void start() {
            startTime.set(System.nanoTime());
        }

        void stop() {
            Long start = startTime.get();
            if (start != null) {
                long elapsed = System.nanoTime() - start;
                totalTimeNs.addAndGet(elapsed);
                count.incrementAndGet();
                startTime.remove();
            }
        }

        @Override
        public String getName() { return name; }

        @Override
        public double getAverage() {
            long c = count.get();
            return c == 0 ? 0.0 : (double) totalTimeNs.get() / c / 1_000_000.0;
        }

        @Override
        public void reset() {
            totalTimeNs.set(0);
            count.set(0);
        }
    }
}