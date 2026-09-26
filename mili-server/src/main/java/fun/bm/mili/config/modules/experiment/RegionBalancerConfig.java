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
            注意：默认禁用以保留原版 tick 时序精确性，启用前请确认不影响跨区域红石机器""")
    public static boolean enabled = false;

    @HotReloadUnsupported
    @ConfigInfo(name = "thread-pool-size", comments = """
            Mili 全局工作线程池的线程数。
            0 = 自动（CPU 核心数 / 2，至少 2）。
            注意：该池是全局共享的——区域 tick 任务与异步寻路共用——创建后无法调整大小，
            因此修改后需重启生效。""")
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

    /**
     * The single authoritative size for Mili's shared worker pool.
     * <p>
     * Mili start - fix: this used to return "CPU * 2" while {@code MiliSchedulerImpl}
     * hard-coded "CPU / 2", and neither consulted the other — so the user-configured value
     * was never applied to anything.  {@code MiliSchedulerImpl.init()} now calls this,
     * which makes it the one place the pool size is decided, and the default is aligned
     * with what the scheduler actually used to do.
     */
    public static int getThreadPoolSize() {
        return threadPoolSize > 0
                ? threadPoolSize
                : Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    }
}