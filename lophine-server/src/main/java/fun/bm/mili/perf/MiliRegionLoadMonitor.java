package fun.bm.mili.perf;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.logging.LogUtils;
import io.papermc.paper.threadedregions.TickRegions;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.config.flags.TransformedConfig;
import me.earthme.luminol.enums.EnumConfigCategory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * mili - 区域负载监控与均衡顾问 / Region load monitor and balance advisor.
 *
 * <p>定期采样每个 tick 区域的工作负载 / Periodically samples the work done by each tick region.
 * 当某个区域持续最慢时发出警告 / If a region is consistently the slowest, surfaces a warning.
 * 运维人员可据此 / Operators can use these warnings to:
 * <ul>
 *   <li>手动拆分区域 (将负载传送到新位置) / Manually split a region</li>
 *   <li>增加 tick 线程数 / Increase tick thread count</li>
 *   <li>减少热点区域的实体数 / Reduce entity counts in the hot region</li>
 * </ul>
 *
 * <p>监控在全局区域线程上运行 (默认每 100 tick) / The monitor runs on the global region thread
 * (every 100 ticks by default)，不会死锁或干扰区域 tick / so it cannot deadlock.
 * 所有采样存储使用无锁原子快照 / All sample storage uses lock-free atomic snapshots.
 *
 * <p>为什么不自动拆分? / Why not auto-split? 区域拆分开销大 / Region splits are expensive.
 * Folia 在负载降到零时自动触发 / Folia auto-triggers when load drops to zero.
 * 我们只提供数据，不做决策 / We surface data, not decisions.
 */
@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "lophine_region_load_monitor")
public class MiliRegionLoadMonitor implements IConfigModule {
    @TransformedConfig(name = "enabled", directory = {"perf", "region_load_monitor"})
    @ConfigInfo(name = "enabled", comments = """
            Track per-region MSPT, entity count, player count and chunk
            count. Logs a warning when a region consistently takes more
            than 'slow-region-mspt-threshold' milliseconds per tick. Helps
            diagnose why a single region is capping the server TPS.""")
    public static boolean enabled = true;

    @TransformedConfig(name = "sample-interval-ticks", directory = {"perf", "region_load_monitor"})
    @ConfigInfo(name = "sample-interval-ticks", comments = """
            How often (in ticks) to sample region load. Default 100 ticks
            (5s) is a good balance between freshness and overhead.""")
    public static int sampleIntervalTicks = 100;

    @TransformedConfig(name = "slow-region-mspt-threshold", directory = {"perf", "region_load_monitor"})
    @ConfigInfo(name = "slow-region-mspt-threshold", comments = """
            MSPT (milliseconds per tick) above which a region is flagged
            as 'slow'. The warning is only emitted when a region is
            above this threshold for at least 'slow-region-consecutive'
            consecutive samples.""")
    public static double slowRegionMsptThreshold = 45.0;

    @TransformedConfig(name = "slow-region-consecutive", directory = {"perf", "region_load_monitor"})
    @ConfigInfo(name = "slow-region-consecutive", comments = """
            Number of consecutive samples above threshold before warning
            is emitted. Prevents alarm spam from transient spikes.""")
    public static int slowRegionConsecutive = 5;

    @TransformedConfig(name = "log-summary-every-seconds", directory = {"perf", "region_load_monitor"})
    @ConfigInfo(name = "log-summary-every-seconds", comments = """
            Emit a per-level summary log (all regions, sorted by MSPT) at
            this interval. 0 disables summary logs.""")
    public static int logSummaryEverySeconds = 300;

    @DoNotLoad
    private static final Logger LOGGER = LogUtils.getLogger();

    @DoNotLoad
    private static final Map<Long, RegionSample> SAMPLES = new ConcurrentHashMap<>();

    @DoNotLoad
    private static final AtomicLong LAST_SUMMARY_NANOS = new AtomicLong(0L);

    @DoNotLoad
    private static final AtomicLong LAST_SAMPLE_NANOS = new AtomicLong(0L);

    @DoNotLoad
    private static final AtomicReference<String> LAST_SUMMARY = new AtomicReference<>("");

    /**
     * 采样入口 (由全局区域调度器或 patched 代码调用) / Sample entry point.
     * 全级别 O(N) 遍历，任意线程安全调用 / Cheap O(N) over all regions, safe from any thread.
     */
    public static void onSampleTick() {
        if (!enabled) {
            return;
        }
        long now = System.nanoTime();
        if (now - LAST_SAMPLE_NANOS.get() < 50_000_000L /* 50ms minimum gap */) {
            return;
        }
        if (!LAST_SAMPLE_NANOS.compareAndSet(LAST_SAMPLE_NANOS.get(), now)) {
            return;
        }

        try {
            final MinecraftServer server = MinecraftServer.getServer();
            if (server == null) {
                return;
            }
            for (ServerLevel level : server.getAllLevels()) {
                sampleLevel(level, now);
            }
        } catch (Throwable t) {
            // never let the monitor crash a tick
            LOGGER.debug("mili region load monitor: sample error: {}", t.getMessage());
        }
    }

    private static void sampleLevel(ServerLevel level, long nowNanos) {
        try {
            final String levelName = level.dimension().identifier().toString();
            level.regioniser.computeForAllRegions(region -> {
                TickRegions.TickRegionData data = region.getData();
                if (data == null) {
                    return;
                }
                long regionId = data.id;

                // Try to get MSPT from the region scheduling handle
                double mspt = -1.0;
                double tps = -1.0;
                try {
                    var report = data.getRegionSchedulingHandle().getTickReport5s(nowNanos);
                    if (report != null) {
                        mspt = report.timePerTickData().segmentAll().average() / 1.0E6;
                        tps = report.tpsData().segmentAll().average();
                    }
                } catch (Throwable ignored) {
                }

                TickRegions.RegionStats stats = data.getRegionStats();
                int chunkCount = stats.getChunkCount();
                int entityCount = stats.getEntityCount();
                int playerCount = stats.getPlayerCount();

                RegionSample sample = SAMPLES.computeIfAbsent(regionId, k -> new RegionSample());
                sample.levelName = levelName;
                sample.chunkCount = chunkCount;
                sample.entityCount = entityCount;
                sample.playerCount = playerCount;
                sample.lastSampleNanos = nowNanos;
                sample.samplesTaken++;

                // Update MSPT/TPS if available
                if (mspt >= 0) {
                    sample.lastMspt = mspt;
                    if (mspt > sample.peakMspt) {
                        sample.peakMspt = mspt;
                    }
                    sample.emaMspt = 0.9 * sample.emaMspt + 0.1 * mspt;
                }
                if (tps >= 0) {
                    sample.lastTps = tps;
                }

                // Load score: MSPT-weighted composite score (higher MSPT = heavier load)
                // chunks base + entities (tick cost) + players (network) + MSPT (actual impact)
                double loadScore = chunkCount + 2.0 * entityCount + 10.0 * playerCount;
                if (mspt >= 0) loadScore += mspt * 50.0;
                if (loadScore > sample.peakLoadScore) {
                    sample.peakLoadScore = loadScore;
                }
                sample.emaLoadScore = 0.9 * sample.emaLoadScore + 0.1 * loadScore;

                // Track consecutive slow samples
                if (mspt >= slowRegionMsptThreshold) {
                    sample.consecutiveSlow++;
                } else {
                    sample.consecutiveSlow = 0;
                }
            });
        } catch (Throwable t) {
            LOGGER.debug("mili region load monitor: sampleLevel error: {}", t.getMessage());
        }
    }

    /**
     * 发出摘要日志 (幂等、按频率限制) / Emit summary log (idempotent, rate-limited).
     * 任意上下文安全调用 / Safe to call from any context.
     */
    public static void maybeLogSummary() {
        if (!enabled || logSummaryEverySeconds <= 0) {
            return;
        }
        long now = System.nanoTime();
        long gap = (long) logSummaryEverySeconds * 1_000_000_000L;
        if (now - LAST_SUMMARY_NANOS.get() < gap) {
            return;
        }
        if (!LAST_SUMMARY_NANOS.compareAndSet(LAST_SUMMARY_NANOS.get(), now)) {
            return;
        }
        // GC stale region entries (regions destroyed by Folia split/merge)
        gcDestroyedRegions(600_000_000_000L /* 10 minutes */);
        String summary = buildSummary();
        LAST_SUMMARY.set(summary);
        LOGGER.info(summary);
    }

    @Nullable
    public static String getLatestSummary() {
        return LAST_SUMMARY.get();
    }

    private static String buildSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("===== mili region load summary =====\n");

        // Aggregate totals
        int totalRegions = SAMPLES.size();
        int activeRegions = 0;
        int totalEntities = 0;
        int totalChunks = 0;
        int totalPlayers = 0;
        int slowRegions = 0;

        List<Map.Entry<Long, RegionSample>> sorted = new ArrayList<>(SAMPLES.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue().emaLoadScore, a.getValue().emaLoadScore));

        for (Map.Entry<Long, RegionSample> e : sorted) {
            RegionSample s = e.getValue();
            if (s.entityCount > 0 || s.chunkCount > 0 || s.playerCount > 0) {
                activeRegions++;
            }
            totalEntities += s.entityCount;
            totalChunks += s.chunkCount;
            totalPlayers += s.playerCount;
            if (s.consecutiveSlow >= slowRegionConsecutive) {
                slowRegions++;
            }
        }

        sb.append(String.format("  totals: %d regions (%d active) | chunks=%d entities=%d players=%d | slow=%d%n",
                totalRegions, activeRegions, totalChunks, totalEntities, totalPlayers, slowRegions));

        // Show top 15 active regions (skip zero-load ones)
        int shown = 0;
        for (Map.Entry<Long, RegionSample> e : sorted) {
            RegionSample s = e.getValue();
            if (s.entityCount == 0 && s.chunkCount == 0 && s.playerCount == 0 && s.emaLoadScore < 1.0) {
                continue;
            }
            if (shown >= 15) break;

            String loadTag = computeLoadTag(s);
            String msptInfo = s.lastMspt >= 0
                    ? String.format(" mspt=%.1f/avg=%.1f", s.lastMspt, s.emaMspt)
                    : "";
            String tpsInfo = s.lastTps >= 0
                    ? String.format(" tps=%.1f", s.lastTps)
                    : "";
            String slowWarn = s.consecutiveSlow >= slowRegionConsecutive ? " [SLOW]" : "";

            sb.append(String.format("  [%s] #%d %s: chunks=%d ent=%d ply=%d | ema=%.0f peak=%.0f%s%s%s%n",
                    loadTag, e.getKey(), s.levelName,
                    s.chunkCount, s.entityCount, s.playerCount,
                    s.emaLoadScore, s.peakLoadScore,
                    msptInfo, tpsInfo, slowWarn));
            shown++;
        }
        if (shown == 0) {
            sb.append("  (all regions idle)\n");
        }
        return sb.toString();
    }

    /**
     * 返回结构化区域数据供 Adventure 组件显示 / Returns structured region data for Adventure display.
     * 每项为快照，适用于游戏内渲染 / Each entry is a snapshot for in-game rendering.
     */
    public static List<RegionDisplayData> getDisplayData() {
        List<RegionDisplayData> result = new ArrayList<>();
        SAMPLES.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().emaLoadScore, a.getValue().emaLoadScore))
                .limit(20)
                .forEach(e -> result.add(new RegionDisplayData(
                        e.getKey(), e.getValue())));
        return Collections.unmodifiableList(result);
    }

    private static String computeLoadTag(RegionSample s) {
        if (s.entityCount == 0 && s.chunkCount == 0 && s.emaLoadScore < 1.0) return "IDLE";
        if (s.emaLoadScore < 500) return "LOW ";
        if (s.emaLoadScore < 2000) return "MED ";
        if (s.emaLoadScore < 5000) return "HIGH";
        return "CRIT";
    }

    /**
     * 单个区域的数据快照 (供 Adventure 组件渲染) / Snapshot of a single region's data.
     */
    public record RegionDisplayData(
            long regionId,
            String levelName,
            int chunkCount,
            int entityCount,
            int playerCount,
            double emaLoadScore,
            double peakLoadScore,
            double lastMspt,
            double emaMspt,
            double peakMspt,
            double lastTps,
            long samplesTaken,
            int consecutiveSlow,
            String loadTag,
            double hotspotScore,
            String balanceAdvice
    ) {
        RegionDisplayData(long id, RegionSample s) {
            this(id, s.levelName, s.chunkCount, s.entityCount, s.playerCount,
                    s.emaLoadScore, s.peakLoadScore, s.lastMspt, s.emaMspt,
                    s.peakMspt, s.lastTps, s.samplesTaken, s.consecutiveSlow,
                    computeLoadTag(s).trim(),
                    computeHotspotScore(s),
                    computeBalanceAdvice(s));
        }

        private static double computeHotspotScore(RegionSample s) {
            // MSPT 权重 + 实体数权重 / MSPT weight + entity weight
            double score = 0;
            if (s.lastMspt >= 0) score += s.lastMspt * 2.0;
            score += s.entityCount * 0.1;
            score += s.consecutiveSlow * 5.0;
            return score;
        }

        private static String computeBalanceAdvice(RegionSample s) {
            if (s.consecutiveSlow < slowRegionConsecutive) return "";
            if (s.entityCount > 500) {
                return "实体过多 (" + s.entityCount + "), 建议迁移牧场/农场 / Too many entities, move farms";
            }
            if (s.playerCount > 5) {
                return s.playerCount + " 玩家集中, 建议分散活动 / players concentrated, spread out";
            }
            if (s.chunkCount > 100) {
                return "区块过多 (" + s.chunkCount + "), 减少视距 / Too many chunks, reduce view distance";
            }
            return "MSPT=" + String.format("%.1f", s.lastMspt) + ", 监控中 / monitoring";
        }
    }

    public static int getTrackedRegionCount() {
        return SAMPLES.size();
    }

    /**
     * 定期清理已销毁区域的采样 (Folia 拆分/合并) / Cleanup samples for destroyed regions.
     */
    public static void gcDestroyedRegions(long olderThanNanos) {
        long threshold = System.nanoTime() - olderThanNanos;
        SAMPLES.entrySet().removeIf(e -> e.getValue().lastSampleNanos < threshold);
    }

    @Override
    public void onLoaded(@Nullable CommentedFileConfig configInstance, @Nullable Set<Exception> exs) {
        LOGGER.info("mili region load monitor: enabled={}, sample-interval={} ticks, slow-threshold={} MSPT",
                enabled, sampleIntervalTicks, slowRegionMsptThreshold);
    }

    @Override
    public void onUnloaded(CommentedFileConfig configInstance) {
        // No-op
    }

    /**
     * 可变区域采样 / Mutable per-region sample.
     * 存储在 ConcurrentHashMap 中，键为 TickRegionData.id / Keyed by TickRegionData.id.
     */
    private static final class RegionSample {
        volatile String levelName = "unknown";
        volatile int chunkCount;
        volatile int entityCount;
        volatile int playerCount;
        volatile double peakLoadScore;
        volatile double emaLoadScore;
        volatile double lastMspt = -1.0;
        volatile double emaMspt;
        volatile double peakMspt;
        volatile double lastTps = -1.0;
        volatile long lastSampleNanos;
        volatile long samplesTaken;
        volatile int consecutiveSlow;
    }
}
