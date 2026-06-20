package net.minecraft.util.profiling.metrics;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
import org.jspecify.annotations.Nullable;

public class MetricSampler {
    private final String name;
    private final MetricCategory category;
    private final DoubleSupplier sampler;
    private final ByteBuf ticks;
    private final ByteBuf values;
    private volatile boolean isRunning;
    private final @Nullable Runnable beforeTick;
    final MetricSampler.@Nullable ThresholdTest thresholdTest;
    private double currentValue;

    protected MetricSampler(
        String name, MetricCategory category, DoubleSupplier sampler, @Nullable Runnable beforeTick, MetricSampler.@Nullable ThresholdTest thresholdTest
    ) {
        this.name = name;
        this.category = category;
        this.beforeTick = beforeTick;
        this.sampler = sampler;
        this.thresholdTest = thresholdTest;
        this.values = ByteBufAllocator.DEFAULT.buffer();
        this.ticks = ByteBufAllocator.DEFAULT.buffer();
        this.isRunning = true;
    }

    public static MetricSampler create(String name, MetricCategory category, DoubleSupplier sampler) {
        return new MetricSampler(name, category, sampler, null, null);
    }

    public static <T> MetricSampler create(String name, MetricCategory category, T context, ToDoubleFunction<T> sampler) {
        return builder(name, category, sampler, context).build();
    }

    public static <T> MetricSampler.MetricSamplerBuilder<T> builder(String name, MetricCategory category, ToDoubleFunction<T> sampler, T context) {
        if (sampler == null) {
            throw new IllegalStateException();
        } else {
            return new MetricSampler.MetricSamplerBuilder<>(name, category, sampler, context);
        }
    }

    public void onStartTick() {
        if (!this.isRunning) {
            throw new IllegalStateException("Not running");
        } else {
            if (this.beforeTick != null) {
                this.beforeTick.run();
            }
        }
    }

    public void onEndTick(int tickTime) {
        this.verifyRunning();
        this.currentValue = this.sampler.getAsDouble();
        this.values.writeDouble(this.currentValue);
        this.ticks.writeInt(tickTime);
    }

    public void onFinished() {
        this.verifyRunning();
        this.values.release();
        this.ticks.release();
        this.isRunning = false;
    }

    private void verifyRunning() {
        if (!this.isRunning) {
            throw new IllegalStateException(String.format(Locale.ROOT, "Sampler for metric %s not started!", this.name));
        }
    }

    DoubleSupplier getSampler() {
        return this.sampler;
    }

    public String getName() {
        return this.name;
    }

    public MetricCategory getCategory() {
        return this.category;
    }

    public MetricSampler.SamplerResult result() {
        Int2DoubleMap map = new Int2DoubleOpenHashMap();
        int i = Integer.MIN_VALUE;
        int i1 = Integer.MIN_VALUE;

        while (this.values.isReadable(8)) {
            int _int = this.ticks.readInt();
            if (i == Integer.MIN_VALUE) {
                i = _int;
            }

            map.put(_int, this.values.readDouble());
            i1 = _int;
        }

        return new MetricSampler.SamplerResult(i, i1, map);
    }

    public boolean triggersThreshold() {
        return this.thresholdTest != null && this.thresholdTest.test(this.currentValue);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            MetricSampler metricSampler = (MetricSampler)other;
            return this.name.equals(metricSampler.name) && this.category.equals(metricSampler.category);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    public static class MetricSamplerBuilder<T> {
        private final String name;
        private final MetricCategory category;
        private final DoubleSupplier sampler;
        private final T context;
        private @Nullable Runnable beforeTick;
        private MetricSampler.@Nullable ThresholdTest thresholdTest;

        public MetricSamplerBuilder(String name, MetricCategory category, ToDoubleFunction<T> sampler, T context) {
            this.name = name;
            this.category = category;
            this.sampler = () -> sampler.applyAsDouble(context);
            this.context = context;
        }

        public MetricSampler.MetricSamplerBuilder<T> withBeforeTick(Consumer<T> beforeTick) {
            this.beforeTick = () -> beforeTick.accept(this.context);
            return this;
        }

        public MetricSampler.MetricSamplerBuilder<T> withThresholdAlert(MetricSampler.ThresholdTest thresholdTest) {
            this.thresholdTest = thresholdTest;
            return this;
        }

        public MetricSampler build() {
            return new MetricSampler(this.name, this.category, this.sampler, this.beforeTick, this.thresholdTest);
        }
    }

    public static class SamplerResult {
        private final Int2DoubleMap recording;
        private final int firstTick;
        private final int lastTick;

        public SamplerResult(int firstTick, int lastTick, Int2DoubleMap recording) {
            this.firstTick = firstTick;
            this.lastTick = lastTick;
            this.recording = recording;
        }

        public double valueAtTick(int tick) {
            return this.recording.get(tick);
        }

        public int getFirstTick() {
            return this.firstTick;
        }

        public int getLastTick() {
            return this.lastTick;
        }
    }

    public interface ThresholdTest {
        boolean test(double value);
    }

    public static class ValueIncreasedByPercentage implements MetricSampler.ThresholdTest {
        private final float percentageIncreaseThreshold;
        private double previousValue = Double.MIN_VALUE;

        public ValueIncreasedByPercentage(float percentageIncreaseThreshold) {
            this.percentageIncreaseThreshold = percentageIncreaseThreshold;
        }

        @Override
        public boolean test(double value) {
            boolean flag;
            if (this.previousValue != Double.MIN_VALUE && !(value <= this.previousValue)) {
                flag = (value - this.previousValue) / this.previousValue >= this.percentageIncreaseThreshold;
            } else {
                flag = false;
            }

            this.previousValue = value;
            return flag;
        }
    }
}
