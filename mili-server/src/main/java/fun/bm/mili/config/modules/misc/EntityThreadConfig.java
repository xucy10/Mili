package fun.bm.mili.config.modules.misc;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

/**
 * 实体独立线程配置 / Entity independent thread configuration.
 *
 * <p>控制实体 tick 是否使用独立线程池，以及高实体区域保护阈值 /
 * Controls whether entity ticking uses dedicated thread pool and
 * the high entity count protection threshold.
 */
@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "entity-thread")
public class EntityThreadConfig implements IConfigModule {

    @ConfigInfo(name = "enabled", comments = """
            启用实体独立线程调度 / Enable entity independent thread scheduler.
            将实体 tick 分离到独立线程池，避免实体异常导致区域卡死 /
            Separates entity ticking to dedicated thread pool, preventing
            entity issues from freezing regions.
            注意：刷怪塔等高实体区域会自动降级回区域线程 / Note: high entity
            areas (mob farms) auto-fallback to region thread.
            默认关闭 / Disabled by default.""")
    public static boolean enabled = true;

    @ConfigInfo(name = "worker-threads", comments = """
            实体工作线程数 / Entity worker thread count.
            0 = CPU 核心数 / 2 / 0 = CPU cores / 2.
            推荐 / Recommended: 2-4.""")
    public static int workerThreads = 0;

    @ConfigInfo(name = "high-entity-threshold", comments = """
            高实体数量阈值 / High entity count threshold.
            当区域内实体数超过此值时，自动降级回区域线程以保证刷怪塔性能 /
            When region entity count exceeds this, auto-fallback to region
            thread to ensure mob farm performance.
            推荐 / Recommended: 500-2000.""")
    public static int highEntityThreshold = 1000;

    @ConfigInfo(name = "debug", comments = """
            启用调试日志 / Enable debug logging.
            输出实体处理统计和降级事件 / Logs entity processing stats and fallback events.""")
    public static boolean debug = false;
}
