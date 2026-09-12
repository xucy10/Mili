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

    @ConfigInfo(name = "dag-enabled", comments = """
            DAG 依赖感知调度器开关。
            支持将区域 tick 建模为有向无环图（DAG），依赖的 tick 阶段按拓扑序独立并行执行。
            （实验性功能，需启用 region-balancer 后生效）""")
    public static boolean dagEnabled = false;

    @ConfigInfo(name = "dag-batch-size", comments = """
            DAG 调度器的最大批次任务数。
            超过此限制的批次会被截断。
            增大此值可处理更多并行 tick，但也可能增加延迟""")
    public static int dagBatchSize = 4096;

    @ConfigInfo(name = "dag-max-waves", comments = """
            DAG 调度的最大波数（拓扑层级数）。
            过深的 DAG 会被回退为平面顺序执行""")
    public static int dagMaxWaves = 16;

    @ConfigInfo(name = "governor-enabled", comments = """
            Tick 持续时间 PI 调节器开关。
            替代纯 TPS 触发的追赶机制，通过 PI 控制器动态调整 tick 间隔，
            同时限制 CPU、队列深度和 worker 利用率三大硬上限。
            可有效防止 "越追赶越卡" 的正反馈崩溃""")
    public static boolean governorEnabled = false;

    @ConfigInfo(name = "governor-target-interval-ns", comments = """
            目标 tick 间隔（纳秒）。
            50,000,000ns = 20 TPS""")
    public static long governorTargetIntervalNs = 50_000_000L;

    @ConfigInfo(name = "governor-min-interval-ns", comments = """
            最快 tick 间隔（纳秒）。
            即使在高追赶压力下也不能更快""")
    public static long governorMinIntervalNs = 45_000_000L;

    @ConfigInfo(name = "governor-max-interval-ns", comments = """
            最慢 tick 间隔（纳秒）。
            即使在严重过载下也不能更慢""")
    public static long governorMaxIntervalNs = 60_000_000L;

    @ConfigInfo(name = "pi-kp", comments = """
            PI 控制器的比例增益 Kp。
            越高，响应越激进；过低，无法有效追赶""")
    public static double piKp = 0.3;

    @ConfigInfo(name = "pi-ki", comments = """
            PI 控制器的积分增益 Ki。
            纠正累积误差，但过高会引起振荡""")
    public static double piKi = 0.05;

    @ConfigInfo(name = "pi-max-catchup", comments = """
            PI 控制器允许的单次最大追赶 tick 数""")
    public static int piMaxCatchup = 20;

    @ConfigInfo(name = "pi-cpu-budget-ms", comments = """
            CPU 预算：允许单 tick 消耗的最大时间（毫秒）。
            超过此值时 PI 控制器拒绝追赶""")
    public static long piCpuBudgetMs = 40;

    @ConfigInfo(name = "pi-max-queue-depth", comments = """
            队列深度上限：待处理任务数超过此值时 PI 控制器拒绝追赶""")
    public static int piMaxQueueDepth = 500;

    @ConfigInfo(name = "pi-max-worker-util", comments = """
            Worker 利用率上限（0.0~1.0）。
            超过此值时 PI 控制器拒绝追赶""")
    public static double piMaxWorkerUtil = 0.9;

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