package net.minecraft.util.profiling;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.util.profiling.metrics.MetricCategory;
import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;

public class InactiveProfiler implements ProfileCollector {
    public static final InactiveProfiler INSTANCE = new InactiveProfiler();

    private InactiveProfiler() {
    }

    @Override
    public void startTick() {
    }

    @Override
    public void endTick() {
    }

    @Override
    public void push(String name) {
    }

    @Override
    public void push(Supplier<String> nameSupplier) {
    }

    @Override
    public void markForCharting(MetricCategory category) {
    }

    @Override
    public void pop() {
    }

    @Override
    public void popPush(String name) {
    }

    @Override
    public void popPush(Supplier<String> nameSupplier) {
    }

    @Override
    public Zone zone(String name) {
        return Zone.INACTIVE;
    }

    @Override
    public Zone zone(Supplier<String> name) {
        return Zone.INACTIVE;
    }

    @Override
    public void incrementCounter(String counterName, int increment) {
    }

    @Override
    public void incrementCounter(Supplier<String> counterNameSupplier, int increment) {
    }

    @Override
    public ProfileResults getResults() {
        return EmptyProfileResults.EMPTY;
    }

    @Override
    public ActiveProfiler.@Nullable PathEntry getEntry(String entryId) {
        return null;
    }

    @Override
    public Set<Pair<String, MetricCategory>> getChartedPaths() {
        return ImmutableSet.of();
    }
}
