package fun.bm.mili.config.modules.experiment;

import fun.bm.mili.command.MiliThreadsCommand;
import fun.bm.mili.rust.TomlConfigData;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.config.flags.HotReloadUnsupported;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 线程治理层配置。
 *
 * <p>注意与 {@code region_balancer} 的分工：那边管的是"region 该怎么调度"的算法与策略，
 * 这边管的是"整个服务端一共能开多少线程、池怎么分层、跑挂了怎么办"的资源治理。
 * 两者互不相干，可以独立开关。</p>
 */
@ConfigClassInfo(category = EnumConfigCategory.EXPERIMENT, name = "scheduler")
public class SchedulerConfig implements IConfigModule {

    /**
     * 观测门面。复用本配置模块承载命令注册，避免为一条命令新增一个配置段。
     */
    @DoNotLoad
    private static MiliThreadsCommand threadsCommand = null;

    @HotReloadUnsupported
    @ConfigInfo(name = "enabled", comments = """
            启用线程治理层。
            统一托管 Mili 各组件的线程池：共享分层池、统一命名、线程预算、
            周期任务的异常隔离与生命周期闭环。
            关闭时各组件退回自建线程的行为（fail-open），功能不受影响，
            但会失去统一的观测与异常兜底。""")
    public static boolean enabled = true;

    @HotReloadUnsupported
    @ConfigInfo(name = "cpu-threads", comments = """
            CPU 计算层的线程数。
            用于寻路、批量数学等纯计算任务。设为 0 则自动取 CPU 核心数的一半（最少 2）。""")
    public static int cpuThreads = 0;

    @HotReloadUnsupported
    @ConfigInfo(name = "io-threads", comments = """
            阻塞 IO 层的线程数。
            用于磁盘读写、区域文件刷盘、远端 HTTP 等。
            阻塞时不占 CPU，因此可比 CPU 层多一些。设为 0 则自动取 min(核心数, 8)（最少 2）。""")
    public static int ioThreads = 0;

    @HotReloadUnsupported
    @ConfigInfo(name = "background-threads", comments = """
            后台巡检层（共享定时池）的线程数。
            绝大多数 "每 N 秒看一眼" 的组件共享这一个池即可，
            不需要各自 newSingleThreadScheduledExecutor。设为 0 则自动取 1。""")
    public static int backgroundThreads = 0;

    @ConfigInfo(name = "max-managed-threads", comments = """
            全局线程预算（软上限）。
            累计创建的受管线程数超过此值时打印告警，但不会拒绝创建 ——
            因为拒绝线程会让功能直接失效，比超预算更糟。
            设为 0 表示不限制。""")
    public static int maxManagedThreads = 64;

    @HotReloadUnsupported
    @ConfigInfo(name = "queue-capacity", comments = """
            共享池的排队容量。
            刻意保持较短：后台任务是辅助性的，一旦持续积压说明下游已经出问题，
            此时快速失败并把背压传导回调用方，好过无限堆积直到内存耗尽。""")
    public static int queueCapacity = 256;

    @ConfigInfo(name = "shutdown-timeout-ms", comments = """
            关闭线程池时等待存量任务的超时（毫秒）。
            超时后强制 shutdownNow 并记录丢弃量，保证停机过程本身不会挂死。""")
    public static long shutdownTimeoutMs = 3000L;

    @ConfigInfo(name = "slow-task-warn-ms", comments = """
            周期任务的耗时告警阈值（毫秒）。
            单次执行超过此值时打印 WARN。设为 0 关闭该告警。""")
    public static long slowTaskWarnMs = 1000L;

    @ConfigInfo(name = "max-consecutive-failures", comments = """
            周期任务连续失败多少次后自动停用。
            低于该阈值的偶发失败只记日志并继续，避免一次抖动就永久停掉巡检任务。
            建议不小于 3。""")
    public static int maxConsecutiveFailures = 3;

    public static int resolveCpuThreads() {
        return cpuThreads > 0 ? cpuThreads : Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    }

    public static int resolveIoThreads() {
        return ioThreads > 0 ? ioThreads : Math.max(2, Math.min(Runtime.getRuntime().availableProcessors(), 8));
    }

    public static int resolveBackgroundThreads() {
        return backgroundThreads > 0 ? backgroundThreads : 1;
    }

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
        if (threadsCommand == null) {
            threadsCommand = new MiliThreadsCommand();
        }
        threadsCommand.register();
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
        if (threadsCommand != null) {
            threadsCommand.unregister();
        }
    }
}
