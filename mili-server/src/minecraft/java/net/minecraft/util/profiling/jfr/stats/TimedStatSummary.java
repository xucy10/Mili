package net.minecraft.util.profiling.jfr.stats;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.profiling.jfr.Percentiles;
import org.jspecify.annotations.Nullable;

public record TimedStatSummary<T extends TimedStat>(
    T fastest, T slowest, @Nullable T secondSlowest, int count, Map<Integer, Double> percentilesNanos, Duration totalDuration
) {
    public static <T extends TimedStat> Optional<TimedStatSummary<T>> summary(List<T> stats) {
        if (stats.isEmpty()) {
            return Optional.empty();
        } else {
            List<T> list = stats.stream().sorted(Comparator.comparing(TimedStat::duration)).toList();
            Duration duration = list.stream().map(TimedStat::duration).reduce(Duration::plus).orElse(Duration.ZERO);
            T timedStat = (T)list.getFirst();
            T timedStat1 = (T)list.getLast();
            T timedStat2 = list.size() > 1 ? list.get(list.size() - 2) : null;
            int size = list.size();
            Map<Integer, Double> map = Percentiles.evaluate(list.stream().mapToLong(timedStat3 -> timedStat3.duration().toNanos()).toArray());
            return Optional.of(new TimedStatSummary<>(timedStat, timedStat1, timedStat2, size, map, duration));
        }
    }
}
