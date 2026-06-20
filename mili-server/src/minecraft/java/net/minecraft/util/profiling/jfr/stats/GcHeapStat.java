package net.minecraft.util.profiling.jfr.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jdk.jfr.consumer.RecordedEvent;

public record GcHeapStat(Instant timestamp, long heapUsed, GcHeapStat.Timing timing) {
    public static GcHeapStat from(RecordedEvent event) {
        return new GcHeapStat(
            event.getStartTime(),
            event.getLong("heapUsed"),
            event.getString("when").equalsIgnoreCase("before gc") ? GcHeapStat.Timing.BEFORE_GC : GcHeapStat.Timing.AFTER_GC
        );
    }

    public static GcHeapStat.Summary summary(Duration duration, List<GcHeapStat> stats, Duration gcTotalDuration, int totalGCs) {
        return new GcHeapStat.Summary(duration, gcTotalDuration, totalGCs, calculateAllocationRatePerSecond(stats));
    }

    private static double calculateAllocationRatePerSecond(List<GcHeapStat> stats) {
        long l = 0L;
        Map<GcHeapStat.Timing, List<GcHeapStat>> map = stats.stream().collect(Collectors.groupingBy(gcHeapStat2 -> gcHeapStat2.timing));
        List<GcHeapStat> list = map.get(GcHeapStat.Timing.BEFORE_GC);
        List<GcHeapStat> list1 = map.get(GcHeapStat.Timing.AFTER_GC);

        for (int i = 1; i < list.size(); i++) {
            GcHeapStat gcHeapStat = list.get(i);
            GcHeapStat gcHeapStat1 = list1.get(i - 1);
            l += gcHeapStat.heapUsed - gcHeapStat1.heapUsed;
        }

        Duration duration = Duration.between(stats.get(1).timestamp, stats.get(stats.size() - 1).timestamp);
        return (double)l / duration.getSeconds();
    }

    public record Summary(Duration duration, Duration gcTotalDuration, int totalGCs, double allocationRateBytesPerSecond) {
        public float gcOverHead() {
            return (float)this.gcTotalDuration.toMillis() / (float)this.duration.toMillis();
        }
    }

    static enum Timing {
        BEFORE_GC,
        AFTER_GC;
    }
}
