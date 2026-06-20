package fun.bm.mili.config.modules.misc;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

/**
 * 内存优化配置 / Memory optimization configuration.
 *
 * <p>周期性清理死亡/断开玩家的追踪状态，并在堆使用率过高时建议 GC /
 * Periodically cleans stale player tracking states and hints GC when heap is high.
 */
@ConfigClassInfo(category = EnumConfigCategory.MISC, name = "memory-optimizer")
public class MemoryOptConfig implements IConfigModule {

    @ConfigInfo(name = "enabled", comments = """
            启用内存优化器 / Enable memory optimizer.
            周期性清理断开玩家的追踪状态并管理 GC 压力 / Periodically cleans
            disconnected player states and manages GC pressure.
            注意: 默认关闭，开启后 System.gc() 可能导致实体卡顿 / Note: disabled by
            default, System.gc() can cause entity stuttering.""")
    public static boolean enabled = false;

    @ConfigInfo(name = "gc-hint-enabled", comments = """
            启用 GC 提示 / Enable GC hint.
            当堆使用率超过阈值时建议 JVM 执行 GC / Hints JVM to GC when heap
            usage exceeds threshold.
            警告: System.gc() 触发 STW 停顿，会导致实体瞬移/打不到 / WARNING:
            System.gc() triggers STW pauses causing entity teleport/desync.
            默认关闭 / Disabled by default.""")
    public static boolean gcHintEnabled = false;

    @ConfigInfo(name = "gc-hint-interval-seconds", comments = """
            GC 提示最小间隔 (秒) / Minimum GC hint interval (seconds).
            防止过于频繁地触发 GC / Prevents too-frequent GC triggers.
            推荐 / Recommended: 60-300.""")
    public static long gcHintIntervalSeconds = 120;

    @ConfigInfo(name = "gc-hint-heap-threshold", comments = """
            GC 触发堆使用率阈值 (%) / Heap usage threshold for GC hint (%).
            超过此百分比时建议 GC / Hints GC when usage exceeds this percentage.
            推荐 / Recommended: 75-90.""")
    public static double gcHintHeapThreshold = 80.0;

    @ConfigInfo(name = "debug", comments = """
            调试日志 / Enable debug logging.
            输出内存使用量和清理事件 / Logs memory usage and cleanup events.""")
    public static boolean debug = false;
}
