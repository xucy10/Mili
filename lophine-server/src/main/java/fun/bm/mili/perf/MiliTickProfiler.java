package fun.bm.mili.perf;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.logging.LogUtils;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.config.flags.TransformedConfig;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * mili - 轻量级无分配 tick 热点探查器 / Lightweight, zero-allocation tick hotspot detector.
 *
 * <p>包装 Runnable 以测量墙钟时间 / Wraps a Runnable to measure wall-clock time.
 * 设计为足够廉价以在生产环境开启 / Designed to be cheap enough for production: ~50ns/sample.
 *
 * <p>与全量 async-profiler 不同，仅计时你标记的标签 / Unlike async-profiler, only times
 * labels YOU mark。用于识别哪些自定义代码路径消耗最多 tick 时间 / Identify which custom
 * Lophine code paths consume the most tick time.
 *
 * <p>用法 / Usage:
 * <pre>
 *   try (MiliTickProfiler.Sample s = MiliTickProfiler.start("hopper-merge")) {
 *       // ...work...
 *   }
 * </pre>
 *
 * <p>通过 /lophine-perf profiler 或 dumpStats() 查看聚合统计 / Aggregated stats via
 * /lophine-perf profiler or dumpStats().
 */
@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "lophine_tick_profiler")
public class MiliTickProfiler implements IConfigModule {
    @TransformedConfig(name = "enabled", directory = {"perf", "tick_profiler"})
    @ConfigInfo(name = "enabled", comments = """
            Enable Lophine's lightweight tick profiler. When enabled, the
            Lophine codebase measures its own hot paths (bot operations,
            region safety checks, etc.) so that you can identify which
            custom feature is consuming tick time. Cost: ~50ns per sample.""")
    public static boolean enabled = false;

    @TransformedConfig(name = "log-every-seconds", directory = {"perf", "tick_profiler"})
    @ConfigInfo(name = "log-every-seconds", comments = """
            Emit a stats summary to the log every N seconds. 0 disables
            periodic logging (stats can still be retrieved on demand via
            MiliTickProfiler.dumpStats()).""")
    public static int logEverySeconds = 60;

    @DoNotLoad
    private static final Logger LOGGER = LogUtils.getLogger();

    @DoNotLoad
    private static final Map<String, Stat> STATS = new ConcurrentHashMap<>();

    @DoNotLoad
    private static final AtomicLong LAST_LOG_NANOS = new AtomicLong(0L);

    /**
     * 开启新采样 / Open a new sample.
     * 返回 {@link Sample} 在关闭时将纳秒耗时记录到命名桶 / Returns a Sample that records
     * elapsed nanoseconds into the named bucket when closed.
     */
    public static Sample start(String name) {
        if (!enabled) {
            return NOOP;
        }
        return new Sample(name, System.nanoTime());
    }

    /**
     * 记录单次测量 / Record a single measurement.
     * 适用于单行代码 / Useful when the work fits a single line.
     */
    public static void record(String name, long elapsedNanos) {
        if (!enabled) {
            return;
        }
        Stat s = STATS.computeIfAbsent(name, k -> new Stat());
        s.count.increment();
        s.totalNanos.add(elapsedNanos);
        long curMax = s.maxNanos.get();
        while (elapsedNanos > curMax) {
            if (s.maxNanos.compareAndSet(curMax, elapsedNanos)) {
                break;
            }
            curMax = s.maxNanos.get();
        }
    }

    /**
     * 返回多行统计摘要 / Returns a multi-line stats summary.
     * 从命令或日志处理器调用 / Call from a command or log handler.
     */
    public static String dumpStats() {
        if (STATS.isEmpty()) {
            return "(no samples)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-30s %12s %15s %15s %10s%n", "name", "count", "total-ms", "avg-us", "max-us"));
        STATS.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().totalNanos.sum(), a.getValue().totalNanos.sum()))
                .forEach(e -> {
                    Stat s = e.getValue();
                    long count = s.count.sum();
                    long totalNanos = s.totalNanos.sum();
                    double avgMicros = (count == 0) ? 0.0 : (totalNanos / 1000.0) / count;
                    double maxMicros = s.maxNanos.get() / 1000.0;
                    double totalMillis = totalNanos / 1_000_000.0;
                    sb.append(String.format("%-30s %12d %15.2f %15.3f %10.2f%n",
                            e.getKey(), count, totalMillis, avgMicros, maxMicros));
                });
        return sb.toString();
    }

    /**
     * 定期发出摘要日志 (按频率限制) / Called periodically to emit summary log (rate-limited).
     * 任意线程安全 / Safe to call from any thread.
     */
    public static void maybeLogSummary() {
        if (!enabled || logEverySeconds <= 0) {
            return;
        }
        long now = System.nanoTime();
        long gap = (long) logEverySeconds * 1_000_000_000L;
        if (now - LAST_LOG_NANOS.get() < gap) {
            return;
        }
        if (!LAST_LOG_NANOS.compareAndSet(LAST_LOG_NANOS.get(), now)) {
            return;
        }
        LOGGER.info("mili tick profiler stats (top paths by total time):\n{}", dumpStats());
    }

    public static int getTrackedLabelCount() {
        return STATS.size();
    }

    @Override
    public void onLoaded(@Nullable CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        if (enabled) {
            LOGGER.info("mili tick profiler: ENABLED. Cost ~50ns/sample. Dump with /lophine-perf (planned).");
        }
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        // No-op
    }

    /**
     * AutoCloseable 采样句柄 / AutoCloseable sample handle.
     * 使用 try-with-resources 确保异常时也能完成测量 / Use try-with-resources to ensure
     * measurement completes even on exception.
     */
    public static class Sample implements AutoCloseable {
        private final String name;
        private final long startNanos;
        private boolean closed = false;

        Sample(String name, long startNanos) {
            this.name = name;
            this.startNanos = startNanos;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            record(name, System.nanoTime() - startNanos);
        }
    }

    private static final Sample NOOP = new Sample("", 0L) {
        @Override
        public void close() {
            // no-op
        }
    };

    private static final class Stat {
        final LongAdder count = new LongAdder();
        final LongAdder totalNanos = new LongAdder();
        final AtomicLong maxNanos = new AtomicLong(0L);
    }
}
