package net.minecraft.util.profiling.metrics.profiling;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;
import net.minecraft.SystemReport;
import net.minecraft.util.profiling.ProfileCollector;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.util.profiling.metrics.MetricSampler;
import net.minecraft.util.profiling.metrics.MetricsRegistry;
import net.minecraft.util.profiling.metrics.MetricsSamplerProvider;
import org.slf4j.Logger;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

public class ServerMetricsSamplersProvider implements MetricsSamplerProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Set<MetricSampler> samplers = new ObjectOpenHashSet<>();
    private final ProfilerSamplerAdapter samplerFactory = new ProfilerSamplerAdapter();

    public ServerMetricsSamplersProvider(LongSupplier timeSource, boolean dedicatedServer) {
        this.samplers.add(tickTimeSampler(timeSource));
        if (dedicatedServer) {
            this.samplers.addAll(runtimeIndependentSamplers());
        }
    }

    public static Set<MetricSampler> runtimeIndependentSamplers() {
        Builder<MetricSampler> builder = ImmutableSet.builder();

        try {
            ServerMetricsSamplersProvider.CpuStats cpuStats = new ServerMetricsSamplersProvider.CpuStats();
            IntStream.range(0, cpuStats.nrOfCpus)
                .mapToObj(i -> MetricSampler.create("cpu#" + i, MetricCategory.CPU, () -> cpuStats.loadForCpu(i)))
                .forEach(builder::add);
        } catch (Throwable var2) {
            LOGGER.warn("Failed to query cpu, no cpu stats will be recorded", var2);
        }

        builder.add(
            MetricSampler.create(
                "heap MiB", MetricCategory.JVM, () -> SystemReport.sizeInMiB(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
            )
        );
        builder.addAll(MetricsRegistry.INSTANCE.getRegisteredSamplers());
        return builder.build();
    }

    @Override
    public Set<MetricSampler> samplers(Supplier<ProfileCollector> profiles) {
        this.samplers.addAll(this.samplerFactory.newSamplersFoundInProfiler(profiles));
        return this.samplers;
    }

    public static MetricSampler tickTimeSampler(final LongSupplier timeSource) {
        Stopwatch stopwatch = Stopwatch.createUnstarted(new Ticker() {
            @Override
            public long read() {
                return timeSource.getAsLong();
            }
        });
        ToDoubleFunction<Stopwatch> toDoubleFunction = stopwatch1 -> {
            if (stopwatch1.isRunning()) {
                stopwatch1.stop();
            }

            long l = stopwatch1.elapsed(TimeUnit.NANOSECONDS);
            stopwatch1.reset();
            return l;
        };
        MetricSampler.ValueIncreasedByPercentage valueIncreasedByPercentage = new MetricSampler.ValueIncreasedByPercentage(2.0F);
        return MetricSampler.builder("ticktime", MetricCategory.TICK_LOOP, toDoubleFunction, stopwatch)
            .withBeforeTick(Stopwatch::start)
            .withThresholdAlert(valueIncreasedByPercentage)
            .build();
    }

    static class CpuStats {
        private final SystemInfo systemInfo = new SystemInfo();
        private final CentralProcessor processor = this.systemInfo.getHardware().getProcessor();
        public final int nrOfCpus = this.processor.getLogicalProcessorCount();
        private long[][] previousCpuLoadTick = this.processor.getProcessorCpuLoadTicks();
        private double[] currentLoad = this.processor.getProcessorCpuLoadBetweenTicks(this.previousCpuLoadTick);
        private long lastPollMs;

        public double loadForCpu(int index) {
            long l = System.currentTimeMillis();
            if (this.lastPollMs == 0L || this.lastPollMs + 501L < l) {
                this.currentLoad = this.processor.getProcessorCpuLoadBetweenTicks(this.previousCpuLoadTick);
                this.previousCpuLoadTick = this.processor.getProcessorCpuLoadTicks();
                this.lastPollMs = l;
            }

            return this.currentLoad[index] * 100.0;
        }
    }
}
