package fun.bm.mili.config.modules.experiment;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.HotReloadUnsupported;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "region_balancer")
public class RegionBalancerConfig implements IConfigModule {
    @HotReloadUnsupported
    @ConfigInfo(name = "enabled", comments = """
            启用自适应区域 tick 平衡器。
            使用固定大小的线程池和基于区域负载的优先级调度替换每个区域的专用线程。
            优点：减少上下文切换，提高 CPU 利用率，稳定 TPS。
            """)
    public static boolean enabled = false;

    @ConfigInfo(name = "thread-pool-size", comments = """
            区域 tick 的工作线程数量。
            默认为 CPU 核心数 * 2。设为 0 则自动检测。
            """)
    public static int threadPoolSize = 0;

    @ConfigInfo(name = "history-window-size", comments = "用于负载计算平均的最近 tick 数量")
    public static int historyWindowSize = 10;

    @ConfigInfo(name = "high-load-threshold-ms", comments = "区域 tick 耗时超过此值视为高负载")
    public static double highLoadThresholdMs = 20.0;

    @ConfigInfo(name = "low-load-threshold-ms", comments = "区域 tick 耗时低于此值视为低负载")
    public static double lowLoadThresholdMs = 2.0;

    @ConfigInfo(name = "max-tick-catchup", comments = "单次执行中区域最多追赶的 tick 数")
    public static int maxTickCatchup = 3;

    @ConfigInfo(name = "analysis-interval-ms", comments = "区域分析的执行间隔（毫秒）")
    public static long analysisIntervalMs = 5000;

    @ConfigInfo(name = "idle-skip-ticks", comments = "低负载区域两次执行之间可跳过的 tick 数")
    public static int idleSkipTicks = 1;

    @ConfigInfo(name = "merge-batch-soft-limit", comments = "低负载区域合并批次的软上限")
    public static int mergeBatchSoftLimit = 4;

    @ConfigInfo(name = "merge-batch-hard-limit", comments = "低负载区域合并批次的硬上限")
    public static int mergeBatchHardLimit = 8;

    public static int getThreadPoolSize() {
        return threadPoolSize > 0 ? threadPoolSize : Runtime.getRuntime().availableProcessors() * 2;
    }
}