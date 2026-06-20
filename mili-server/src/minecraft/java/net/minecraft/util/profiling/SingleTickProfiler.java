package net.minecraft.util.profiling;

import com.mojang.logging.LogUtils;
import java.io.File;
import java.util.function.LongSupplier;
import net.minecraft.SharedConstants;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class SingleTickProfiler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final LongSupplier realTime;
    private final long saveThreshold;
    private int tick;
    private final File location;
    private ProfileCollector profiler = InactiveProfiler.INSTANCE;

    public SingleTickProfiler(LongSupplier realTime, String location, long saveThreshold) {
        this.realTime = realTime;
        this.location = new File("debug", location);
        this.saveThreshold = saveThreshold;
    }

    public ProfilerFiller startTick() {
        this.profiler = new ActiveProfiler(this.realTime, () -> this.tick, () -> true);
        this.tick++;
        return this.profiler;
    }

    public void endTick() {
        if (this.profiler != InactiveProfiler.INSTANCE) {
            ProfileResults results = this.profiler.getResults();
            this.profiler = InactiveProfiler.INSTANCE;
            if (results.getNanoDuration() >= this.saveThreshold) {
                File file = new File(this.location, "tick-results-" + Util.getFilenameFormattedDateTime() + ".txt");
                results.saveResults(file.toPath());
                LOGGER.info("Recorded long tick -- wrote info to: {}", file.getAbsolutePath());
            }
        }
    }

    public static @Nullable SingleTickProfiler createTickProfiler(String name) {
        return SharedConstants.DEBUG_MONITOR_TICK_TIMES ? new SingleTickProfiler(Util.timeSource, name, SharedConstants.MAXIMUM_TICK_TIME_NANOS) : null;
    }

    public static ProfilerFiller decorateFiller(ProfilerFiller profiler, @Nullable SingleTickProfiler singleTickProfiler) {
        return singleTickProfiler != null ? ProfilerFiller.combine(singleTickProfiler.startTick(), profiler) : profiler;
    }
}
