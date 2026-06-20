package net.minecraft.util.profiling.metrics.profiling;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.util.profiling.ActiveProfiler;
import net.minecraft.util.profiling.ContinuousProfiler;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfileCollector;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.metrics.MetricSampler;
import net.minecraft.util.profiling.metrics.MetricsSamplerProvider;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import net.minecraft.util.profiling.metrics.storage.RecordedDeviation;
import org.jspecify.annotations.Nullable;

public class ActiveMetricsRecorder implements MetricsRecorder {
    public static final int PROFILING_MAX_DURATION_SECONDS = 10;
    private static @Nullable Consumer<Path> globalOnReportFinished = null;
    private final Map<MetricSampler, List<RecordedDeviation>> deviationsBySampler = new Object2ObjectOpenHashMap<>();
    private final ContinuousProfiler taskProfiler;
    private final Executor ioExecutor;
    private final MetricsPersister metricsPersister;
    private final Consumer<ProfileResults> onProfilingEnd;
    private final Consumer<Path> onReportFinished;
    private final MetricsSamplerProvider metricsSamplerProvider;
    private final LongSupplier wallTimeSource;
    private final long deadlineNano;
    private int currentTick;
    private ProfileCollector singleTickProfiler;
    private volatile boolean killSwitch;
    private Set<MetricSampler> thisTickSamplers = ImmutableSet.of();

    private ActiveMetricsRecorder(
        MetricsSamplerProvider metricsSamplerProvider,
        LongSupplier wallTimeSource,
        Executor ioExecutor,
        MetricsPersister metricPersister,
        Consumer<ProfileResults> onProfilerEnd,
        Consumer<Path> onReportFinished
    ) {
        this.metricsSamplerProvider = metricsSamplerProvider;
        this.wallTimeSource = wallTimeSource;
        this.taskProfiler = new ContinuousProfiler(wallTimeSource, () -> this.currentTick, () -> false);
        this.ioExecutor = ioExecutor;
        this.metricsPersister = metricPersister;
        this.onProfilingEnd = onProfilerEnd;
        this.onReportFinished = globalOnReportFinished == null ? onReportFinished : onReportFinished.andThen(globalOnReportFinished);
        this.deadlineNano = wallTimeSource.getAsLong() + TimeUnit.NANOSECONDS.convert(10L, TimeUnit.SECONDS);
        this.singleTickProfiler = new ActiveProfiler(this.wallTimeSource, () -> this.currentTick, () -> true);
        this.taskProfiler.enable();
    }

    public static ActiveMetricsRecorder createStarted(
        MetricsSamplerProvider metricsSamplerProvider,
        LongSupplier wallTimeSource,
        Executor ioExecutor,
        MetricsPersister metricsPersister,
        Consumer<ProfileResults> onProfilerEnd,
        Consumer<Path> onReportFinished
    ) {
        return new ActiveMetricsRecorder(metricsSamplerProvider, wallTimeSource, ioExecutor, metricsPersister, onProfilerEnd, onReportFinished);
    }

    @Override
    public synchronized void end() {
        if (this.isRecording()) {
            this.killSwitch = true;
        }
    }

    @Override
    public synchronized void cancel() {
        if (this.isRecording()) {
            this.singleTickProfiler = InactiveProfiler.INSTANCE;
            this.onProfilingEnd.accept(EmptyProfileResults.EMPTY);
            this.cleanup(this.thisTickSamplers);
        }
    }

    @Override
    public void startTick() {
        this.verifyStarted();
        this.thisTickSamplers = this.metricsSamplerProvider.samplers(() -> this.singleTickProfiler);

        for (MetricSampler metricSampler : this.thisTickSamplers) {
            metricSampler.onStartTick();
        }

        this.currentTick++;
    }

    @Override
    public void endTick() {
        this.verifyStarted();
        if (this.currentTick != 0) {
            for (MetricSampler metricSampler : this.thisTickSamplers) {
                metricSampler.onEndTick(this.currentTick);
                if (metricSampler.triggersThreshold()) {
                    RecordedDeviation recordedDeviation = new RecordedDeviation(Instant.now(), this.currentTick, this.singleTickProfiler.getResults());
                    this.deviationsBySampler.computeIfAbsent(metricSampler, metricSampler1 -> Lists.newArrayList()).add(recordedDeviation);
                }
            }

            if (!this.killSwitch && this.wallTimeSource.getAsLong() <= this.deadlineNano) {
                this.singleTickProfiler = new ActiveProfiler(this.wallTimeSource, () -> this.currentTick, () -> true);
            } else {
                this.killSwitch = false;
                ProfileResults results = this.taskProfiler.getResults();
                this.singleTickProfiler = InactiveProfiler.INSTANCE;
                this.onProfilingEnd.accept(results);
                this.scheduleSaveResults(results);
            }
        }
    }

    @Override
    public boolean isRecording() {
        return this.taskProfiler.isEnabled();
    }

    @Override
    public ProfilerFiller getProfiler() {
        return ProfilerFiller.combine(this.taskProfiler.getFiller(), this.singleTickProfiler);
    }

    private void verifyStarted() {
        if (!this.isRecording()) {
            throw new IllegalStateException("Not started!");
        }
    }

    private void scheduleSaveResults(ProfileResults results) {
        HashSet<MetricSampler> set = new HashSet<>(this.thisTickSamplers);
        this.ioExecutor.execute(() -> {
            Path path = this.metricsPersister.saveReports(set, this.deviationsBySampler, results);
            this.cleanup(set);
            this.onReportFinished.accept(path);
        });
    }

    private void cleanup(Collection<MetricSampler> samplers) {
        for (MetricSampler metricSampler : samplers) {
            metricSampler.onFinished();
        }

        this.deviationsBySampler.clear();
        this.taskProfiler.disable();
    }

    public static void registerGlobalCompletionCallback(Consumer<Path> globalOnReportFinished) {
        ActiveMetricsRecorder.globalOnReportFinished = globalOnReportFinished;
    }
}
