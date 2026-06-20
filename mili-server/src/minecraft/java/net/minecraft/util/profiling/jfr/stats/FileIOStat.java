package net.minecraft.util.profiling.jfr.stats;

import com.mojang.datafixers.util.Pair;
import java.time.Duration;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public record FileIOStat(Duration duration, @Nullable String path, long bytes) {
    public static FileIOStat.Summary summary(Duration duration, List<FileIOStat> stats) {
        long l = stats.stream().mapToLong(fileIoStat -> fileIoStat.bytes).sum();
        return new FileIOStat.Summary(
            l,
            (double)l / duration.getSeconds(),
            stats.size(),
            (double)stats.size() / duration.getSeconds(),
            stats.stream().map(FileIOStat::duration).reduce(Duration.ZERO, Duration::plus),
            stats.stream()
                .filter(fileIoStat -> fileIoStat.path != null)
                .collect(Collectors.groupingBy(fileIoStat -> fileIoStat.path, Collectors.summingLong(fileIoStat -> fileIoStat.bytes)))
                .entrySet()
                .stream()
                .sorted(Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> Pair.of(entry.getKey(), entry.getValue()))
                .limit(10L)
                .toList()
        );
    }

    public record Summary(
        long totalBytes,
        double bytesPerSecond,
        long counts,
        double countsPerSecond,
        Duration timeSpentInIO,
        List<Pair<String, Long>> topTenContributorsByTotalBytes
    ) {
    }
}
