package fun.bm.mili.config.modules.misc;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

/**
 * 统一调度器配置 / Unified scheduler configuration.
 *
 * <p>合并原 UnifiedSchedulerConfig, UnifiedSchedulerConfig, UnifiedSchedulerConfig /
 * Merges original UnifiedSchedulerConfig, UnifiedSchedulerConfig, UnifiedSchedulerConfig.
 *
 * <p>设计原则 / Design principles:
 * <ul>
 *   <li>统一前缀：mili.scheduler.* / Unified prefix</li>
 *   <li>分层启用：master → submodule / Layered enablement</li>
 *   <li>智能默认：根据 CPU 核心数自动调整 / Smart defaults based on CPU cores</li>
 * </ul>
 *
 * @author Mili Team
 * @since 1.21.11
 */
@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "mili-scheduler")
public class UnifiedSchedulerConfig implements IConfigModule {

    // ======================== Master Switch ========================

    @ConfigInfo(name = "master-enabled", comments = """
            主开关 - 禁用后所有调度器功能关闭 / Master switch - disables all scheduler features.
            用于快速回退到原版 Folia 行为 / Use to quickly fallback to vanilla Folia behavior.
            默认启用 / Enabled by default.""")
    public static boolean masterEnabled = true;

    // ======================== Chunk Independent Scheduler ========================

    @ConfigInfo(name = "chunk-independent-enabled", comments = """
            启用区块独立调度 / Enable chunk independent scheduling.
            每个区块在独立线程 tick，通过 CrossChunkBus 协调跨区块交互 /
            Each chunk ticks on dedicated thread, coordinated via CrossChunkBus.
            警告：实验性功能，可能导致红石/流体异常 / Warning: experimental, may cause redstone/fluid issues.
            默认关闭 / Disabled by default.""")
    public static boolean chunkIndependentEnabled = false;

    @ConfigInfo(name = "chunk-worker-threads", comments = """
            区块工作线程数 / Chunk worker thread count.
            0 = CPU 核心数 / 2 / 0 = CPU cores / 2.
            推荐 / Recommended: 2-8.""")
    public static int chunkWorkerThreads = 0;

    @ConfigInfo(name = "chunk-timeout-ms", comments = """
            区块 tick 超时 (毫秒) / Chunk tick timeout (ms).
            超时后回退到区域线程 / Fallback to region thread on timeout.
            推荐 / Recommended: 50-200.""")
    public static long chunkTimeoutMs = 100L;

    @ConfigInfo(name = "cross-chunk-strict-mode", comments = """
            严格模式 - 跨区块操作 0-tick 延迟 / Strict mode - 0-tick delay for cross-chunk ops.
            关闭时有 1-tick 延迟但更稳定 / Disabled = 1-tick delay but more stable.
            默认关闭 / Disabled by default.""")
    public static boolean crossChunkStrictMode = false;

    // ======================== Entity Thread Scheduler ========================

    @ConfigInfo(name = "entity-thread-enabled", comments = """
            启用实体独立线程 / Enable entity independent thread.
            实体 tick 分离到独立线程池，避免卡死区域线程 /
            Entity ticking on separate thread pool, prevents region thread freezes.
            高实体区域自动降级回区域线程 / High-entity regions auto-fallback to region thread.
            默认关闭 / Disabled by default.""")
    public static boolean entityThreadEnabled = true;

    @ConfigInfo(name = "entity-worker-threads", comments = """
            实体工作线程数 / Entity worker thread count.
            0 = CPU 核心数 / 2 / 0 = CPU cores / 2.
            推荐 / Recommended: 2-4.""")
    public static int entityWorkerThreads = 4;

    @ConfigInfo(name = "entity-high-threshold", comments = """
            高实体数量阈值 / High entity count threshold.
            区域内实体超过此值时降级回区域线程（保护刷怪塔性能）/
            When region entity count exceeds this, fallback to region thread (protects mob farm performance).
            推荐 / Recommended: 500-2000.""")
    public static int entityHighThreshold = 1500;

    // ======================== Chunk Preload ========================

    @ConfigInfo(name = "preload-enabled", comments = """
            启用区块预加载 / Enable chunk preloading.
            基于玩家移动方向预测性加载区块 / Predictively loads chunks based on player movement direction.
            默认启用 / Enabled by default.""")
    public static boolean preloadEnabled = true;

    @ConfigInfo(name = "preload-radius", comments = """
            预加载半径（区块）/ Preload radius (chunks).
            推荐 / Recommended: 2-4.""")
    public static int preloadRadius = 3;

    @ConfigInfo(name = "preload-on-teleport", comments = """
            传送时预加载 / Preload on teleport.
            传送到新位置时预加载周围区块 / Preloads chunks around teleport destination.
            默认启用 / Enabled by default.""")
    public static boolean preloadOnTeleport = true;

    // ======================== Advanced ========================

    @ConfigInfo(name = "mixed-mode", comments = """
            混合模式 - 同时使用区域线程和独立线程 / Mixed mode - use both region and dedicated threads.
            仅在 chunk-independent-enabled = true 时生效 / Only effective when chunk-independent-enabled = true.
            默认关闭 / Disabled by default.""")
    public static boolean mixedMode = true;

    @ConfigInfo(name = "debug", comments = """
            启用调试日志 / Enable debug logging.
            输出调度器状态、线程分配、超时事件 / Logs scheduler state, thread assignments, timeout events.
            默认关闭 / Disabled by default.""")
    public static boolean debug = false;
}
