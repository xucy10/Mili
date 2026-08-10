package net.minecraft.util.profiling;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.ReportType;
import net.minecraft.SharedConstants;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;

public class FilledProfileResults implements ProfileResults {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ProfilerPathEntry EMPTY = new ProfilerPathEntry() {
        @Override
        public long getDuration() {
            return 0L;
        }

        @Override
        public long getMaxDuration() {
            return 0L;
        }

        @Override
        public long getCount() {
            return 0L;
        }

        @Override
        public Object2LongMap<String> getCounters() {
            return Object2LongMaps.emptyMap();
        }
    };
    private static final Splitter SPLITTER = Splitter.on('\u001e');
    private static final Comparator<Entry<String, FilledProfileResults.CounterCollector>> COUNTER_ENTRY_COMPARATOR = Entry.<String, FilledProfileResults.CounterCollector>comparingByValue(
            Comparator.comparingLong(counterCollector -> counterCollector.totalValue)
        )
        .reversed();
    private final Map<String, ? extends ProfilerPathEntry> entries;
    private final long startTimeNano;
    private final int startTimeTicks;
    private final long endTimeNano;
    private final int endTimeTicks;
    private final int tickDuration;

    public FilledProfileResults(Map<String, ? extends ProfilerPathEntry> entries, long startTimeNano, int startTimeTicks, long endTimeNano, int endTimeTicks) {
        this.entries = entries;
        this.startTimeNano = startTimeNano;
        this.startTimeTicks = startTimeTicks;
        this.endTimeNano = endTimeNano;
        this.endTimeTicks = endTimeTicks;
        this.tickDuration = endTimeTicks - startTimeTicks;
    }

    private ProfilerPathEntry getEntry(String key) {
        ProfilerPathEntry profilerPathEntry = this.entries.get(key);
        return profilerPathEntry != null ? profilerPathEntry : EMPTY;
    }

    @Override
    public List<ResultField> getTimes(String sectionPath) {
        String string = sectionPath;
        ProfilerPathEntry entry = this.getEntry("root");
        long duration = entry.getDuration();
        ProfilerPathEntry entry1 = this.getEntry(sectionPath);
        long duration1 = entry1.getDuration();
        long count = entry1.getCount();
        List<ResultField> list = Lists.newArrayList();
        if (!sectionPath.isEmpty()) {
            sectionPath = sectionPath + "\u001e";
        }

        long l = 0L;

        for (String string1 : this.entries.keySet()) {
            if (isDirectChild(sectionPath, string1)) {
                l += this.getEntry(string1).getDuration();
            }
        }

        float f = (float)l;
        if (l < duration1) {
            l = duration1;
        }

        if (duration < l) {
            duration = l;
        }

        for (String string2 : this.entries.keySet()) {
            if (isDirectChild(sectionPath, string2)) {
                ProfilerPathEntry entry2 = this.getEntry(string2);
                long duration2 = entry2.getDuration();
                double d = duration2 * 100.0 / l;
                double d1 = duration2 * 100.0 / duration;
                String sub = string2.substring(sectionPath.length());
                list.add(new ResultField(sub, d, d1, entry2.getCount()));
            }
        }

        if ((float)l > f) {
            list.add(new ResultField("unspecified", ((float)l - f) * 100.0 / l, ((float)l - f) * 100.0 / duration, count));
        }

        Collections.sort(list);
        list.add(0, new ResultField(string, 100.0, l * 100.0 / duration, count));
        return list;
    }

    private static boolean isDirectChild(String sectionPath, String entry) {
        return entry.length() > sectionPath.length() && entry.startsWith(sectionPath) && entry.indexOf(30, sectionPath.length() + 1) < 0;
    }

    private Map<String, FilledProfileResults.CounterCollector> getCounterValues() {
        Map<String, FilledProfileResults.CounterCollector> map = Maps.newTreeMap();
        this.entries
            .forEach(
                (string, profilerPathEntry) -> {
                    Object2LongMap<String> counters = profilerPathEntry.getCounters();
                    if (!counters.isEmpty()) {
                        List<String> parts = SPLITTER.splitToList(string);
                        counters.forEach(
                            (string1, l) -> map.computeIfAbsent(string1, string2 -> new FilledProfileResults.CounterCollector()).addValue(parts.iterator(), l)
                        );
                    }
                }
            );
        return map;
    }

    @Override
    public long getStartTimeNano() {
        return this.startTimeNano;
    }

    @Override
    public int getStartTimeTicks() {
        return this.startTimeTicks;
    }

    @Override
    public long getEndTimeNano() {
        return this.endTimeNano;
    }

    @Override
    public int getEndTimeTicks() {
        return this.endTimeTicks;
    }

    @Override
    public boolean saveResults(Path path) {
        Writer writer = null;

        boolean var4;
        try {
            Files.createDirectories(path.getParent());
            writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
            writer.write(this.getProfilerResults(this.getNanoDuration(), this.getTickDuration()));
            return true;
        } catch (Throwable var8) {
            LOGGER.error("Could not save profiler results to {}", path, var8);
            var4 = false;
        } finally {
            IOUtils.closeQuietly(writer);
        }

        return var4;
    }

    protected String getProfilerResults(long timeSpan, int tickSpan) {
        StringBuilder stringBuilder = new StringBuilder();
        ReportType.PROFILE.appendHeader(stringBuilder, List.of());
        stringBuilder.append("Version: ").append(SharedConstants.getCurrentVersion().id()).append('\n');
        stringBuilder.append("Time span: ").append(timeSpan / 1000000L).append(" ms\n");
        stringBuilder.append("Tick span: ").append(tickSpan).append(" ticks\n");
        stringBuilder.append("// This is approximately ")
            .append(String.format(Locale.ROOT, "%.2f", tickSpan / ((float)timeSpan / 1.0E9F)))
            .append(" ticks per second. It should be ")
            .append(20)
            .append(" ticks per second\n\n");
        stringBuilder.append("--- BEGIN PROFILE DUMP ---\n\n");
        this.appendProfilerResults(0, "root", stringBuilder);
        stringBuilder.append("--- END PROFILE DUMP ---\n\n");
        Map<String, FilledProfileResults.CounterCollector> counterValues = this.getCounterValues();
        if (!counterValues.isEmpty()) {
            stringBuilder.append("--- BEGIN COUNTER DUMP ---\n\n");
            this.appendCounters(counterValues, stringBuilder, tickSpan);
            stringBuilder.append("--- END COUNTER DUMP ---\n\n");
        }

        return stringBuilder.toString();
    }

    @Override
    public String getProfilerResults() {
        StringBuilder stringBuilder = new StringBuilder();
        this.appendProfilerResults(0, "root", stringBuilder);
        return stringBuilder.toString();
    }

    private static StringBuilder indentLine(StringBuilder builder, int indents) {
        builder.append(String.format(Locale.ROOT, "[%02d] ", indents));

        for (int i = 0; i < indents; i++) {
            builder.append("|   ");
        }

        return builder;
    }

    private void appendProfilerResults(int depth, String sectionPath, StringBuilder builder) {
        List<ResultField> times = this.getTimes(sectionPath);
        Object2LongMap<String> counters = ObjectUtils.firstNonNull(this.entries.get(sectionPath), EMPTY).getCounters();
        counters.forEach(
            (string, l) -> indentLine(builder, depth).append('#').append(string).append(' ').append(l).append('/').append(l / this.tickDuration).append('\n')
        );
        if (times.size() >= 3) {
            for (int i = 1; i < times.size(); i++) {
                ResultField resultField = times.get(i);
                indentLine(builder, depth)
                    .append(resultField.name)
                    .append('(')
                    .append(resultField.count)
                    .append('/')
                    .append(String.format(Locale.ROOT, "%.0f", (float)resultField.count / this.tickDuration))
                    .append(')')
                    .append(" - ")
                    .append(String.format(Locale.ROOT, "%.2f", resultField.percentage))
                    .append("%/")
                    .append(String.format(Locale.ROOT, "%.2f", resultField.globalPercentage))
                    .append("%\n");
                if (!"unspecified".equals(resultField.name)) {
                    try {
                        this.appendProfilerResults(depth + 1, sectionPath + "\u001e" + resultField.name, builder);
                    } catch (Exception var9) {
                        builder.append("[[ EXCEPTION ").append(var9).append(" ]]");
                    }
                }
            }
        }
    }

    private void appendCounterResults(int indents, String name, FilledProfileResults.CounterCollector collector, int tickSpan, StringBuilder builder) {
        indentLine(builder, indents)
            .append(name)
            .append(" total:")
            .append(collector.selfValue)
            .append('/')
            .append(collector.totalValue)
            .append(" average: ")
            .append(collector.selfValue / tickSpan)
            .append('/')
            .append(collector.totalValue / tickSpan)
            .append('\n');
        collector.children
            .entrySet()
            .stream()
            .sorted(COUNTER_ENTRY_COMPARATOR)
            .forEach(entry -> this.appendCounterResults(indents + 1, entry.getKey(), entry.getValue(), tickSpan, builder));
    }

    private void appendCounters(Map<String, FilledProfileResults.CounterCollector> counters, StringBuilder builder, int tickSpan) {
        counters.forEach((string, counterCollector) -> {
            builder.append("-- Counter: ").append(string).append(" --\n");
            this.appendCounterResults(0, "root", counterCollector.children.get("root"), tickSpan, builder);
            builder.append("\n\n");
        });
    }

    @Override
    public int getTickDuration() {
        return this.tickDuration;
    }

    static class CounterCollector {
        long selfValue;
        long totalValue;
        final Map<String, FilledProfileResults.CounterCollector> children = Maps.newHashMap();

        public void addValue(Iterator<String> counters, long value) {
            this.totalValue += value;
            if (!counters.hasNext()) {
                this.selfValue += value;
            } else {
                this.children.computeIfAbsent(counters.next(), string -> new FilledProfileResults.CounterCollector()).addValue(counters, value);
            }
        }
    }
}
