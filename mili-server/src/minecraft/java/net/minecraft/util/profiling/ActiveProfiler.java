package net.minecraft.util.profiling;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.metrics.MetricCategory;
import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ActiveProfiler implements ProfileCollector {
    private static final long WARNING_TIME_NANOS = Duration.ofMillis(100L).toNanos();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<String> paths = Lists.newArrayList();
    private final LongList startTimes = new LongArrayList();
    private final Map<String, ActiveProfiler.PathEntry> entries = Maps.newHashMap();
    private final IntSupplier getTickTime;
    private final LongSupplier getRealTime;
    private final long startTimeNano;
    private final int startTimeTicks;
    private String path = "";
    private boolean started;
    private ActiveProfiler.@Nullable PathEntry currentEntry;
    private final BooleanSupplier suppressWarnings;
    private final Set<Pair<String, MetricCategory>> chartedPaths = new ObjectArraySet<>();

    public ActiveProfiler(LongSupplier getRealTime, IntSupplier getTickTime, BooleanSupplier suppressWarnings) {
        this.startTimeNano = getRealTime.getAsLong();
        this.getRealTime = getRealTime;
        this.startTimeTicks = getTickTime.getAsInt();
        this.getTickTime = getTickTime;
        this.suppressWarnings = suppressWarnings;
    }

    @Override
    public void startTick() {
        if (this.started) {
            LOGGER.error("Profiler tick already started - missing endTick()?");
        } else {
            this.started = true;
            this.path = "";
            this.paths.clear();
            this.push("root");
        }
    }

    @Override
    public void endTick() {
        if (!this.started) {
            LOGGER.error("Profiler tick already ended - missing startTick()?");
        } else {
            this.pop();
            this.started = false;
            if (!this.path.isEmpty()) {
                LOGGER.error(
                    "Profiler tick ended before path was fully popped (remainder: '{}'). Mismatched push/pop?",
                    LogUtils.defer(() -> ProfileResults.demanglePath(this.path))
                );
            }
        }
    }

    @Override
    public void push(String name) {
        if (!this.started) {
            LOGGER.error("Cannot push '{}' to profiler if profiler tick hasn't started - missing startTick()?", name);
        } else {
            if (!this.path.isEmpty()) {
                this.path = this.path + "\u001e";
            }

            this.path = this.path + name;
            this.paths.add(this.path);
            this.startTimes.add(Util.getNanos());
            this.currentEntry = null;
        }
    }

    @Override
    public void push(Supplier<String> nameSupplier) {
        this.push(nameSupplier.get());
    }

    @Override
    public void markForCharting(MetricCategory category) {
        this.chartedPaths.add(Pair.of(this.path, category));
    }

    @Override
    public void pop() {
        if (!this.started) {
            LOGGER.error("Cannot pop from profiler if profiler tick hasn't started - missing startTick()?");
        } else if (this.startTimes.isEmpty()) {
            LOGGER.error("Tried to pop one too many times! Mismatched push() and pop()?");
        } else {
            long nanos = Util.getNanos();
            long l = this.startTimes.removeLong(this.startTimes.size() - 1);
            this.paths.removeLast();
            long l1 = nanos - l;
            ActiveProfiler.PathEntry currentEntry = this.getCurrentEntry();
            currentEntry.accumulatedDuration += l1;
            currentEntry.count++;
            currentEntry.maxDuration = Math.max(currentEntry.maxDuration, l1);
            currentEntry.minDuration = Math.min(currentEntry.minDuration, l1);
            if (l1 > WARNING_TIME_NANOS && !this.suppressWarnings.getAsBoolean()) {
                LOGGER.warn(
                    "Something's taking too long! '{}' took aprox {} ms",
                    LogUtils.defer(() -> ProfileResults.demanglePath(this.path)),
                    LogUtils.defer(() -> l1 / 1000000.0)
                );
            }

            this.path = this.paths.isEmpty() ? "" : this.paths.getLast();
            this.currentEntry = null;
        }
    }

    @Override
    public void popPush(String name) {
        this.pop();
        this.push(name);
    }

    @Override
    public void popPush(Supplier<String> nameSupplier) {
        this.pop();
        this.push(nameSupplier);
    }

    private ActiveProfiler.PathEntry getCurrentEntry() {
        if (this.currentEntry == null) {
            this.currentEntry = this.entries.computeIfAbsent(this.path, string -> new ActiveProfiler.PathEntry());
        }

        return this.currentEntry;
    }

    @Override
    public void incrementCounter(String counterName, int increment) {
        this.getCurrentEntry().counters.addTo(counterName, increment);
    }

    @Override
    public void incrementCounter(Supplier<String> counterNameSupplier, int increment) {
        this.getCurrentEntry().counters.addTo(counterNameSupplier.get(), increment);
    }

    @Override
    public ProfileResults getResults() {
        return new FilledProfileResults(this.entries, this.startTimeNano, this.startTimeTicks, this.getRealTime.getAsLong(), this.getTickTime.getAsInt());
    }

    @Override
    public ActiveProfiler.@Nullable PathEntry getEntry(String entryId) {
        return this.entries.get(entryId);
    }

    @Override
    public Set<Pair<String, MetricCategory>> getChartedPaths() {
        return this.chartedPaths;
    }

    public static class PathEntry implements ProfilerPathEntry {
        long maxDuration = Long.MIN_VALUE;
        long minDuration = Long.MAX_VALUE;
        long accumulatedDuration;
        long count;
        final Object2LongOpenHashMap<String> counters = new Object2LongOpenHashMap<>();

        @Override
        public long getDuration() {
            return this.accumulatedDuration;
        }

        @Override
        public long getMaxDuration() {
            return this.maxDuration;
        }

        @Override
        public long getCount() {
            return this.count;
        }

        @Override
        public Object2LongMap<String> getCounters() {
            return Object2LongMaps.unmodifiable(this.counters);
        }
    }
}
