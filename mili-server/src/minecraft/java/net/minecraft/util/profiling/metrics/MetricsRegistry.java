package net.minecraft.util.profiling.metrics;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public class MetricsRegistry {
    public static final MetricsRegistry INSTANCE = new MetricsRegistry();
    private final WeakHashMap<ProfilerMeasured, Void> measuredInstances = new WeakHashMap<>();

    private MetricsRegistry() {
    }

    public void add(ProfilerMeasured key) {
        this.measuredInstances.put(key, null);
    }

    public List<MetricSampler> getRegisteredSamplers() {
        Map<String, List<MetricSampler>> map = this.measuredInstances
            .keySet()
            .stream()
            .flatMap(profilerMeasured -> profilerMeasured.profiledMetrics().stream())
            .collect(Collectors.groupingBy(MetricSampler::getName));
        return aggregateDuplicates(map);
    }

    private static List<MetricSampler> aggregateDuplicates(Map<String, List<MetricSampler>> samplers) {
        return samplers.entrySet().stream().map(entry -> {
            String string = entry.getKey();
            List<MetricSampler> list = entry.getValue();
            return (MetricSampler)(list.size() > 1 ? new MetricsRegistry.AggregatedMetricSampler(string, list) : list.get(0));
        }).collect(Collectors.toList());
    }

    static class AggregatedMetricSampler extends MetricSampler {
        private final List<MetricSampler> delegates;

        AggregatedMetricSampler(String name, List<MetricSampler> delegates) {
            super(name, delegates.get(0).getCategory(), () -> averageValueFromDelegates(delegates), () -> beforeTick(delegates), thresholdTest(delegates));
            this.delegates = delegates;
        }

        private static MetricSampler.ThresholdTest thresholdTest(List<MetricSampler> samplers) {
            return value -> samplers.stream().anyMatch(metricSampler -> metricSampler.thresholdTest != null && metricSampler.thresholdTest.test(value));
        }

        private static void beforeTick(List<MetricSampler> samplers) {
            for (MetricSampler metricSampler : samplers) {
                metricSampler.onStartTick();
            }
        }

        private static double averageValueFromDelegates(List<MetricSampler> samplers) {
            double d = 0.0;

            for (MetricSampler metricSampler : samplers) {
                d += metricSampler.getSampler().getAsDouble();
            }

            return d / samplers.size();
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            } else if (other == null || this.getClass() != other.getClass()) {
                return false;
            } else if (!super.equals(other)) {
                return false;
            } else {
                MetricsRegistry.AggregatedMetricSampler aggregatedMetricSampler = (MetricsRegistry.AggregatedMetricSampler)other;
                return this.delegates.equals(aggregatedMetricSampler.delegates);
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), this.delegates);
        }
    }
}
