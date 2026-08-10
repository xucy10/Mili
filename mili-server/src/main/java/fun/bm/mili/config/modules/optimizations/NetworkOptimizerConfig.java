package fun.bm.mili.config.modules.optimizations;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "network-optimizer")
public class NetworkOptimizerConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = "启用网络优化器（默认禁用以保留原版网络行为）")
    public static boolean enabled = false;

    @ConfigInfo(name = "packet-compression-level", comments = "包压缩级别 (1-22, 越高压缩率越大但更慢)")
    public static int packetCompressionLevel = 3;

    @ConfigInfo(name = "max-packets-per-tick-per-player", comments = "每个玩家每tick最大发送包数 (0=无限)")
    public static int maxPacketsPerTickPerPlayer = 0;

    @ConfigInfo(name = "chunk-send-batch-size", comments = "区块发送批次大小 (增大可减少网络开销)")
    public static int chunkSendBatchSize = 5;

    @ConfigInfo(name = "entity-track-send-rate-limit", comments = "实体追踪数据包发送频率限制 (ms, 0=禁用)")
    public static long entityTrackSendRateLimitMs = 0;

    @ConfigInfo(name = "compress-batch-threshold", comments = "批量压缩包阈值 (字节, 超过此大小的批次启用压缩)")
    public static int compressBatchThreshold = 256;

    @ConfigInfo(name = "view-distance-optimization", comments = "为生电玩家优化视距发送策略")
    public static boolean viewDistanceOptimization = true;

    @ConfigInfo(name = "silent-chunk-loads", comments = "静默区块加载 (不发送多余的加载动画包)")
    public static boolean silentChunkLoads = true;
}
