package net.minecraft.util.profiling;

import com.mojang.jtracy.Plot;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.util.profiling.metrics.MetricCategory;
import org.slf4j.Logger;

public class TracyZoneFiller implements ProfilerFiller {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(Set.of(Option.RETAIN_CLASS_REFERENCE), 5);
    private final List<com.mojang.jtracy.Zone> activeZones = new ArrayList<>();
    private final Map<String, TracyZoneFiller.PlotAndValue> plots = new HashMap<>();
    private final String name = Thread.currentThread().getName();

    @Override
    public void startTick() {
    }

    @Override
    public void endTick() {
        for (TracyZoneFiller.PlotAndValue plotAndValue : this.plots.values()) {
            plotAndValue.set(0);
        }
    }

    @Override
    public void push(String name) {
        String string = "";
        String string1 = "";
        int i = 0;
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            Optional<StackFrame> optional = STACK_WALKER.walk(
                stream -> stream.filter(
                        stackFrame1 -> stackFrame1.getDeclaringClass() != TracyZoneFiller.class
                            && stackFrame1.getDeclaringClass() != ProfilerFiller.CombinedProfileFiller.class
                    )
                    .findFirst()
            );
            if (optional.isPresent()) {
                StackFrame stackFrame = optional.get();
                string = stackFrame.getMethodName();
                string1 = stackFrame.getFileName();
                i = stackFrame.getLineNumber();
            }
        }

        com.mojang.jtracy.Zone zone = TracyClient.beginZone(name, string, string1, i);
        this.activeZones.add(zone);
    }

    @Override
    public void push(Supplier<String> nameSupplier) {
        this.push(nameSupplier.get());
    }

    @Override
    public void pop() {
        if (this.activeZones.isEmpty()) {
            LOGGER.error("Tried to pop one too many times! Mismatched push() and pop()?");
        } else {
            com.mojang.jtracy.Zone zone = this.activeZones.removeLast();
            zone.close();
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
        this.push(nameSupplier.get());
    }

    @Override
    public void markForCharting(MetricCategory category) {
    }

    @Override
    public void incrementCounter(String counterName, int increment) {
        this.plots.computeIfAbsent(counterName, string -> new TracyZoneFiller.PlotAndValue(this.name + " " + counterName)).add(increment);
    }

    @Override
    public void incrementCounter(Supplier<String> counterNameSupplier, int increment) {
        this.incrementCounter(counterNameSupplier.get(), increment);
    }

    private com.mojang.jtracy.Zone activeZone() {
        return this.activeZones.getLast();
    }

    @Override
    public void addZoneText(String text) {
        this.activeZone().addText(text);
    }

    @Override
    public void addZoneValue(long value) {
        this.activeZone().addValue(value);
    }

    @Override
    public void setZoneColor(int color) {
        this.activeZone().setColor(color);
    }

    static final class PlotAndValue {
        private final Plot plot;
        private int value;

        PlotAndValue(String name) {
            this.plot = TracyClient.createPlot(name);
            this.value = 0;
        }

        void set(int value) {
            this.value = value;
            this.plot.setValue(value);
        }

        void add(int value) {
            this.set(this.value + value);
        }
    }
}
