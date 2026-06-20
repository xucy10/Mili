package fun.bm.mili.config.modules.misc;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

/**
 * 实体线程安全保护配置 / Entity thread safety guard configuration.
 *
 * <p>在 Folia 多线程环境下，检测和缓解跨区域实体访问问题 /
 * In Folia's multi-threaded environment, detects and mitigates cross-region
 * entity access issues that can cause deadlocks and memory leaks.
 */
@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "entity-safety-guard")
public class EntitySafetyConfig implements IConfigModule {

    @ConfigInfo(name = "enabled", comments = """
            启用实体线程安全保护 / Enable entity thread safety guard.
            检测跨区域的实体访问并记录日志，定期清理孤立的调度任务 /
            Detects cross-region entity access and logs it, periodically
            cleans up orphaned scheduler tasks.""")
    public static boolean enabled = true;

    @ConfigInfo(name = "watchdog-timeout-ms", comments = """
            Folia Watchdog 超时时间 (毫秒) / Folia Watchdog timeout (ms).
            增大此值可以给慢操作更多时间，但会延迟崩溃检测 / Increasing gives slow
            operations more time but delays crash detection.
            推荐 / Recommended: 10000-30000 (对 CraftEngine 等插件)""")
    public static int watchdogTimeoutMs = 15000;

    @ConfigInfo(name = "cleanup-interval-ticks", comments = """
            孤立任务清理间隔 (tick) / Orphaned task cleanup interval (ticks).
            每 N tick 检查一次是否有泄漏的调度任务 / Checks for leaked scheduler
            tasks every N ticks. 推荐 / Recommended: 200-600.""")
    public static int cleanupIntervalTicks = 400;

    @ConfigInfo(name = "log-cross-region-access", comments = """
            记录跨区域实体访问 / Log cross-region entity access.
            开启后会在控制台输出跨线程实体访问警告 / When enabled, logs warnings
            for cross-thread entity access to console. 用于诊断问题插件 /
            Useful for diagnosing problematic plugins.""")
    public static boolean logCrossRegionAccess = false;

    @ConfigInfo(name = "debug", comments = """
            调试日志 / Enable debug logging.""")
    public static boolean debug = false;
}
