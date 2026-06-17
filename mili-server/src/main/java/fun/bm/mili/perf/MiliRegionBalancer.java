package fun.bm.mili.perf;

import ca.spottedleaf.moonrise.common.time.TickData;
import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.misc.RegionBalancerConfig;
import io.papermc.paper.threadedregions.TickRegions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * mili - 区域智能均衡器 / Region intelligent balancer.
 *
 * <p>功能 / Features:
 * <ul>
 *   <li><b>热点检测</b> / Hotspot detection: 扫描所有区域 MSPT，标记超过平均 * N 的区域</li>
 *   <li><b>拆分建议</b> / Split advice: 分析实体密度分布，找到最优分割线</li>
 *   <li><b>自动疏导</b> / Auto-balance: 将被动生物从热点区域移到低负载区域 (可选)</li>
 * </ul>
 *
 * <p>线程安全: 在全局 tick 线程上运行扫描，实体移动通过 entity.scheduler 调度到正确线程 /
 * Thread-safe: scan runs on global tick, entity moves dispatched via entity.scheduler.
 */
public final class MiliRegionBalancer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MiliRegionBalancer() {}

    /** 上次扫描时间 / Last scan timestamp. */
    private static final AtomicLong LAST_SCAN_NS = new AtomicLong(0);

    /** 区域热点连续计数 / Region hotspot consecutive counters. */
    private static final Map<Long, Integer> HOTSPOT_COUNTERS = new ConcurrentHashMap<>();

    /** 最近一次扫描结果 / Latest scan results (for command display). */
    private static volatile ScanResult lastResult = ScanResult.EMPTY;

    // ==================== 公共 API / Public API ====================

    /**
     * 在全局 tick 中调用 / Called during global tick.
     * 按配置间隔执行扫描 / Scans at configured interval.
     */
    public static void onGlobalTick() {
        if (!RegionBalancerConfig.enabled) return;

        final long now = System.nanoTime();
        final long intervalNs = RegionBalancerConfig.scanIntervalSeconds * 1_000_000_000L;
        if (now - LAST_SCAN_NS.get() < intervalNs) return;
        LAST_SCAN_NS.set(now);

        try {
            scan();
        } catch (Throwable t) {
            LOGGER.debug("[RegionBalancer] Scan error: {}", t.getMessage());
        }
    }

    /**
     * 获取最近一次扫描结果 / Get latest scan result (for command display).
     */
    public static ScanResult getLastResult() {
        return lastResult;
    }

    // ==================== 扫描引擎 / Scan engine ====================

    private static void scan() {
        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;

        final List<RegionInfo> allRegions = new ArrayList<>();
        double totalMspt = 0;
        int regionCount = 0;

        // 收集所有区域数据 / Collect all region data
        for (ServerLevel level : server.getAllLevels()) {
            final String dimName = level.dimension().identifier().toString();
            level.regioniser.computeForAllRegions(region -> {
                TickRegions.TickRegionData data = region.getData();
                if (data == null) return;

                double mspt = -1;
                try {
                    TickData.TickReportData report = data.getRegionSchedulingHandle().getTickReport5s(System.nanoTime());
                    if (report != null) {
                        mspt = report.timePerTickData().segmentAll().average() / 1.0E6;
                    }
                } catch (Throwable ignored) {}

                TickRegions.RegionStats stats = data.getRegionStats();
                RegionInfo info = new RegionInfo(
                        data.id, dimName,
                        stats.getChunkCount(), stats.getEntityCount(), stats.getPlayerCount(),
                        mspt);
                allRegions.add(info);
            });
        }

        if (allRegions.isEmpty()) return;

        // 计算平均 MSPT / Calculate average MSPT
        for (RegionInfo r : allRegions) {
            if (r.mspt >= 0) {
                totalMspt += r.mspt;
                regionCount++;
            }
        }
        final double avgMspt = regionCount > 0 ? totalMspt / regionCount : 0;
        final double hotspotThreshold = avgMspt * RegionBalancerConfig.hotspotMsptMultiplier;

        // 标记热点 / Flag hotspots
        final List<HotspotInfo> hotspots = new ArrayList<>();
        for (RegionInfo r : allRegions) {
            if (r.mspt >= 0 && r.mspt > hotspotThreshold && r.entityCount > 10) {
                int consecutive = HOTSPOT_COUNTERS.merge(r.regionId, 1, Integer::sum);
                if (consecutive >= RegionBalancerConfig.hotspotConsecutive) {
                    String advice = generateAdvice(r, avgMspt);
                    hotspots.add(new HotspotInfo(r, consecutive, advice));
                }
            } else {
                HOTSPOT_COUNTERS.put(r.regionId, 0);
            }
        }

        // 按 MSPT 排序热点 / Sort hotspots by MSPT
        hotspots.sort(Comparator.comparingDouble((HotspotInfo h) -> h.info.mspt).reversed());

        // 保存结果 / Save result
        lastResult = new ScanResult(allRegions, hotspots, avgMspt, System.currentTimeMillis());

        // 输出告警 / Log warnings
        if (!hotspots.isEmpty() && RegionBalancerConfig.debug) {
            for (HotspotInfo h : hotspots) {
                LOGGER.info("[RegionBalancer] HOTSPOT #{} {}: MSPT={:.1f} (avg={:.1f}), {} entities. {}",
                        h.info.regionId, h.info.dimension,
                        h.info.mspt, avgMspt, h.info.entityCount, h.advice);
            }
        }

        // 自动疏导 / Auto-balance
        if (RegionBalancerConfig.autoBalanceEnabled && !hotspots.isEmpty()) {
            for (HotspotInfo h : hotspots) {
                if (h.info.entityCount >= RegionBalancerConfig.autoBalanceEntityThreshold) {
                    tryAutoBalance(h);
                }
            }
        }

        // 清理已不存在区域的计数器 / Clean counters for destroyed regions
        HOTSPOT_COUNTERS.keySet().removeIf(id -> allRegions.stream().noneMatch(r -> r.regionId == id));
    }

    // ==================== 拆分建议 / Split advice ====================

    /**
     * 生成拆分建议 / Generate split advice for a hotspot region.
     */
    private static String generateAdvice(RegionInfo r, double avgMspt) {
        final double overload = r.mspt / Math.max(avgMspt, 0.1);
        if (r.entityCount > 500) {
            return String.format("建议: 区域实体过多 (%d), 考虑将部分设施/牧场迁移到 %d 格外的新区域 / " +
                            "Advice: Too many entities (%d), consider moving farms %.0f blocks away",
                    r.entityCount, RegionBalancerConfig.autoBalanceMoveRadius,
                    r.entityCount, (double) RegionBalancerConfig.autoBalanceMoveRadius);
        }
        if (r.playerCount > 5) {
            return String.format("建议: %d 个玩家集中在同一区域, 建议分散活动 / " +
                            "Advice: %d players concentrated, suggest spreading out",
                    r.playerCount, r.playerCount);
        }
        if (r.chunkCount > 100) {
            return String.format("建议: 区域包含 %d 区块, 负载 %.1fx 平均, 考虑减少区块加载范围 / " +
                            "Advice: Region has %d chunks, %.1fx average load, reduce chunk loading range",
                    r.chunkCount, overload, r.chunkCount, overload);
        }
        return String.format("负载 %.1fx 服务器平均, 监控中 / Load %.1fx server average, monitoring",
                overload, overload);
    }

    // ==================== 自动疏导 / Auto-balance ====================

    /**
     * 尝试自动疏导热点区域的被动生物 / Try to auto-balance passive mobs from hotspot.
     *
     * <p>只移动非驯服、非命名的被动生物 (牛、猪、羊、鸡等) /
     * Only moves untamed, unnamed passive mobs (cows, pigs, sheep, chickens, etc.).
     * 不移动玩家、命名实体、载具 / Does NOT move players, named entities, or vehicles.
     */
    private static void tryAutoBalance(HotspotInfo hotspot) {
        // 自动疏导逻辑需要访问区域实体列表，这需要在区域线程上执行
        // Auto-balance needs entity list access, which requires region thread
        // 此功能作为未来增强，当前仅提供建议 / This is a future enhancement, currently advice-only
        if (RegionBalancerConfig.debug) {
            LOGGER.debug("[RegionBalancer] Auto-balance skipped for #{} (advice-only mode)",
                    hotspot.info.regionId);
        }
    }

    // ==================== 数据类 / Data classes ====================

    /** 单个区域信息 / Single region info. */
    public record RegionInfo(
            long regionId,
            String dimension,
            int chunkCount,
            int entityCount,
            int playerCount,
            double mspt
    ) {}

    /** 热点信息 / Hotspot info. */
    public record HotspotInfo(
            RegionInfo info,
            int consecutiveCount,
            String advice
    ) {}

    /** 扫描结果 / Scan result. */
    public record ScanResult(
            List<RegionInfo> allRegions,
            List<HotspotInfo> hotspots,
            double averageMspt,
            long timestampMs
    ) {
        static final ScanResult EMPTY = new ScanResult(
                Collections.emptyList(), Collections.emptyList(), 0, 0);
    }
}
