package net.minecraft.util.profiling;

import java.util.function.Supplier;
import net.minecraft.util.profiling.metrics.MetricCategory;

public interface ProfilerFiller {
    String ROOT = "root";

    void startTick();

    void endTick();

    void push(String name);

    void push(Supplier<String> nameSupplier);

    void pop();

    void popPush(String name);

    void popPush(Supplier<String> nameSupplier);

    default void addZoneText(String text) {
    }

    default void addZoneValue(long value) {
    }

    default void setZoneColor(int color) {
    }

    default Zone zone(String name) {
        this.push(name);
        return new Zone(this);
    }

    default Zone zone(Supplier<String> name) {
        this.push(name);
        return new Zone(this);
    }

    void markForCharting(MetricCategory category);

    default void incrementCounter(String entryId) {
        this.incrementCounter(entryId, 1);
    }

    void incrementCounter(String counterName, int increment);

    default void incrementCounter(Supplier<String> entryIdSupplier) {
        this.incrementCounter(entryIdSupplier, 1);
    }

    void incrementCounter(Supplier<String> counterNameSupplier, int increment);

    static ProfilerFiller combine(ProfilerFiller first, ProfilerFiller second) {
        if (first == InactiveProfiler.INSTANCE) {
            return second;
        } else {
            return (ProfilerFiller)(second == InactiveProfiler.INSTANCE ? first : new ProfilerFiller.CombinedProfileFiller(first, second));
        }
    }

    public static class CombinedProfileFiller implements ProfilerFiller {
        private final ProfilerFiller first;
        private final ProfilerFiller second;

        public CombinedProfileFiller(ProfilerFiller first, ProfilerFiller second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void startTick() {
            this.first.startTick();
            this.second.startTick();
        }

        @Override
        public void endTick() {
            this.first.endTick();
            this.second.endTick();
        }

        @Override
        public void push(String name) {
            this.first.push(name);
            this.second.push(name);
        }

        @Override
        public void push(Supplier<String> nameSupplier) {
            this.first.push(nameSupplier);
            this.second.push(nameSupplier);
        }

        @Override
        public void markForCharting(MetricCategory category) {
            this.first.markForCharting(category);
            this.second.markForCharting(category);
        }

        @Override
        public void pop() {
            this.first.pop();
            this.second.pop();
        }

        @Override
        public void popPush(String name) {
            this.first.popPush(name);
            this.second.popPush(name);
        }

        @Override
        public void popPush(Supplier<String> nameSupplier) {
            this.first.popPush(nameSupplier);
            this.second.popPush(nameSupplier);
        }

        @Override
        public void incrementCounter(String counterName, int increment) {
            this.first.incrementCounter(counterName, increment);
            this.second.incrementCounter(counterName, increment);
        }

        @Override
        public void incrementCounter(Supplier<String> counterNameSupplier, int increment) {
            this.first.incrementCounter(counterNameSupplier, increment);
            this.second.incrementCounter(counterNameSupplier, increment);
        }

        @Override
        public void addZoneText(String text) {
            this.first.addZoneText(text);
            this.second.addZoneText(text);
        }

        @Override
        public void addZoneValue(long value) {
            this.first.addZoneValue(value);
            this.second.addZoneValue(value);
        }

        @Override
        public void setZoneColor(int color) {
            this.first.setZoneColor(color);
            this.second.setZoneColor(color);
        }
    }
}
