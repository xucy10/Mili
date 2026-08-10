package net.minecraft.util.profiling.jfr.stats;

import com.google.common.base.MoreObjects;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

public record ThreadAllocationStat(Instant timestamp, String threadName, long totalBytes) {
    private static final String UNKNOWN_THREAD = "unknown";

    public static ThreadAllocationStat from(RecordedEvent event) {
        RecordedThread thread = event.getThread("thread");
        String string = thread == null ? "unknown" : MoreObjects.firstNonNull(thread.getJavaName(), "unknown");
        return new ThreadAllocationStat(event.getStartTime(), string, event.getLong("allocated"));
    }

    public static ThreadAllocationStat.Summary summary(List<ThreadAllocationStat> stats) {
        Map<String, Double> map = new TreeMap<>();
        Map<String, List<ThreadAllocationStat>> map1 = stats.stream().collect(Collectors.groupingBy(threadAllocationStat -> threadAllocationStat.threadName));
        map1.forEach((string, list) -> {
            if (list.size() >= 2) {
                ThreadAllocationStat threadAllocationStat = list.get(0);
                ThreadAllocationStat threadAllocationStat1 = list.get(list.size() - 1);
                long seconds = Duration.between(threadAllocationStat.timestamp, threadAllocationStat1.timestamp).getSeconds();
                long l = threadAllocationStat1.totalBytes - threadAllocationStat.totalBytes;
                map.put(string, (double)l / seconds);
            }
        });
        return new ThreadAllocationStat.Summary(map);
    }

    public record Summary(Map<String, Double> allocationsPerSecondByThread) {
    }
}
