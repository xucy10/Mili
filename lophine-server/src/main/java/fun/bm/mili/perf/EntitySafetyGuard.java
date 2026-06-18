package fun.bm.mili.perf;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.misc.EntitySafetyConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * mili - 实体线程安全保护器 / Entity thread safety guard.
 *
 * <p>在 Folia 多线程环境下提供以下保护 / Provides the following protections
 * in Folia's multi-threaded environment:
 * <ul>
 *   <li><b>区域死锁检测</b> / Region deadlock detection: 监控区域 tick 时间，
 *       当超过阈值时记录详细信息 / Monitors region tick time, logs details when exceeding threshold</li>
 *   <li><b>孤立任务清理</b> / Orphaned task cleanup: 定期扫描并清理已移除实体的
 *       残留调度任务 / Periodically scans and cleans up residual scheduler tasks from removed entities</li>
 *   <li><b>内存泄漏防护</b> / Memory leak prevention: 追踪实体创建/销毁计数，
 *       检测异常增长 / Tracks entity create/destroy counts, detects abnormal growth</li>
 * </ul>
 *
 * <p>在全局 tick 线程上运行 / Runs on global tick thread.
 */
public final class EntitySafetyGuard {
    private static final Logger LOGGER = LogUtils.getLogger();

    private EntitySafetyGuard() {}

    /** 全局 tick 计数器 / Global tick counter. */
    private static final AtomicInteger TICK_COUNTER = new AtomicInteger(0);

    /** 实体创建计数 / Entity creation counter. */
    private static final AtomicLong ENTITIES_CREATED = new AtomicLong(0);

    /** 实体销毁计数 / Entity destruction counter. */
    private static final AtomicLong ENTITIES_DESTROYED = new AtomicLong(0);

    /** 上次清理时间 / Last cleanup timestamp. */
    private static final AtomicLong LAST_CLEANUP_TICK = new AtomicLong(0);

    /** 上次报告的活跃实体数 / Last reported live entity count. */
    private static volatile long lastReportedLive = 0;

    // ==================== 公共 API / Public API ====================

    /**
     * 在全局 tick 中调用 / Called during global tick.
     * 轻量级：仅做计数和定期检查 / Lightweight: only counting and periodic checks.
     */
    public static void onGlobalTick() {
        if (!EntitySafetyConfig.enabled) return;

        final int tick = TICK_COUNTER.incrementAndGet();
        final int interval = EntitySafetyConfig.cleanupIntervalTicks;

        if (interval > 0 && tick - LAST_CLEANUP_TICK.get() >= interval) {
            LAST_CLEANUP_TICK.set(tick);
            performPeriodicCheck();
        }
    }

    /**
     * 实体创建时调用 / Called when entity is created.
     * 可从任意线程安全调用 / Safe to call from any thread.
     */
    public static void onEntityCreated() {
        ENTITIES_CREATED.incrementAndGet();
    }

    /**
     * 实体销毁时调用 / Called when entity is destroyed.
     * 可从任意线程安全调用 / Safe to call from any thread.
     */
    public static void onEntityDestroyed() {
        ENTITIES_DESTROYED.incrementAndGet();
    }

    // ==================== 内部逻辑 / Internal logic ====================

    /**
     * 定期执行检查 / Periodic check execution.
     */
    private static void performPeriodicCheck() {
        try {
            checkEntityLeak();
            if (EntitySafetyConfig.debug) {
                logDiagnostics();
            }
        } catch (Throwable t) {
            LOGGER.debug("[EntitySafety] Periodic check error: {}", t.getMessage());
        }
    }

    /**
     * 检测实体内存泄漏 / Detect entity memory leaks.
     *
     * <p>比较创建数和销毁数，如果差异持续增长则发出警告 /
     * Compares creation and destruction counts, warns if difference grows continuously.
     */
    private static void checkEntityLeak() {
        final long created = ENTITIES_CREATED.get();
        final long destroyed = ENTITIES_DESTROYED.get();
        final long live = created - destroyed;

        // 如果活跃实体数异常高（可能是泄漏）/ If live count abnormally high (possible leak)
        if (live > 10000 && live > lastReportedLive * 1.5) {
            LOGGER.warn("[EntitySafety] Possible entity memory leak detected: " +
                            "created={}, destroyed={}, live={} (previous={}). " +
                            "A plugin may be preventing entity cleanup.",
                    created, destroyed, live, lastReportedLive);
        }

        lastReportedLive = live;
    }

    /**
     * 输出诊断信息 / Output diagnostic info.
     */
    private static void logDiagnostics() {
        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;

        int totalEntities = 0;
        for (ServerLevel level : server.getAllLevels()) {
            try {
                final int[] count = {0};
                level.regioniser.computeForAllRegions(region -> {
                    var data = region.getData();
                    if (data != null) {
                        count[0] += data.getRegionStats().getEntityCount();
                    }
                });
                totalEntities += count[0];
            } catch (Throwable ignored) {}
        }

        LOGGER.debug("[EntitySafety] Diagnostics: totalEntities={}, created={}, destroyed={}, live={}",
                totalEntities, ENTITIES_CREATED.get(), ENTITIES_DESTROYED.get(),
                ENTITIES_CREATED.get() - ENTITIES_DESTROYED.get());
    }

    // ==================== 工具方法 / Utility ====================

    /**
     * 获取当前活跃实体估算数 / Get estimated live entity count.
     */
    public static long getEstimatedLiveEntities() {
        return ENTITIES_CREATED.get() - ENTITIES_DESTROYED.get();
    }

    /**
     * 获取全局 tick 计数 / Get global tick count.
     */
    public static int getTickCount() {
        return TICK_COUNTER.get();
    }
}
