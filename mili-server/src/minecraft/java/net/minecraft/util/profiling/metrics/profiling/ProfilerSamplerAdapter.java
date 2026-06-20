package net.minecraft.util.profiling.metrics.profiling;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.profiling.ActiveProfiler;
import net.minecraft.util.profiling.ProfileCollector;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.util.profiling.metrics.MetricSampler;

public class ProfilerSamplerAdapter {
    private final Set<String> previouslyFoundSamplerNames = new ObjectOpenHashSet<>();

    public Set<MetricSampler> newSamplersFoundInProfiler(Supplier<ProfileCollector> profiles) {
        Set<MetricSampler> set = profiles.get()
            .getChartedPaths()
            .stream()
            .filter(pair -> !this.previouslyFoundSamplerNames.contains(pair.getLeft()))
            .map(pair -> samplerForProfilingPath(profiles, pair.getLeft(), pair.getRight()))
            .collect(Collectors.toSet());

        for (MetricSampler metricSampler : set) {
            this.previouslyFoundSamplerNames.add(metricSampler.getName());
        }

        return set;
    }

    private static MetricSampler samplerForProfilingPath(Supplier<ProfileCollector> profiles, String name, MetricCategory category) {
        return MetricSampler.create(name, category, () -> {
            ActiveProfiler.PathEntry entry = profiles.get().getEntry(name);
            return entry == null ? 0.0 : (double)entry.getMaxDuration() / TimeUtil.NANOSECONDS_PER_MILLISECOND;
        });
    }
}
