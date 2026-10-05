package fun.bm.mili.config.modules.optimizations;

import fun.bm.mili.rust.TomlConfigData;
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

    // --- Network stability settings ---

    @ConfigInfo(name = "connection-read-timeout-seconds", comments = "Read timeout for player connections in seconds (vanilla=30)")
    public static int connectionReadTimeoutSeconds = 30;

    @ConfigInfo(name = "write-buffer-low-water-mark", comments = "Netty write buffer low water mark in bytes (backpressure)")
    public static int writeBufferLowWaterMark = 32768;

    @ConfigInfo(name = "write-buffer-high-water-mark", comments = "Netty write buffer high water mark in bytes (backpressure, 0=disable)")
    public static int writeBufferHighWaterMark = 65536;

    @ConfigInfo(name = "reuse-address", comments = "Enable SO_REUSEADDR for the server socket (quick restart)")
    public static boolean reuseAddress = true;

    @ConfigInfo(name = "io-event-loop-threads", comments = """
            Netty IO 线程数。0 = 保持 Netty 默认（CPU 逻辑核数 x2）不改。
            每个连接绑定到一个 event loop，同一 loop 上的连接互相争抢处理时间：
            只要有个别连接正在大量拉区块/实体数据，挂在同一个 loop 上的其他人都会被一起拖慢。
            人多时建议提高，经验取值 = 并存连接数 / 3，但不要超过物理核数的 4 倍（再多只会增加上下文切换）。
            注意：Netty 的线程数在 event loop group 首次初始化时确定，因此此项必须在服务端建监听之前生效；
            如果 Mili 的配置加载时机偏晚而未能生效，请改用 JVM 启动参数 -Dio.netty.eventLoopThreads=N。
            是否在启动参数里已经指定过，以启动参数为准，此项不会覆盖。""")
    public static int ioEventLoopThreads = 0;

    @ConfigInfo(name = "entity-track-cache-max-size", comments = "Max entries in entity track cache before forced cleanup (0=unlimited)")
    public static int entityTrackCacheMaxSize = 5000;

    @Override
    public void onLoaded(fun.bm.mili.rust.TomlConfigData configInstance, @org.jetbrains.annotations.Nullable java.util.Set<Exception> exs) {
        applyIoEventLoopThreads();
    }

    private static void applyIoEventLoopThreads() {
        if (ioEventLoopThreads <= 0) {
            return;
        }
        final String property = "io.netty.eventLoopThreads";
        String existing = System.getProperty(property);
        if (existing != null) {
            org.slf4j.LoggerFactory.getLogger("Mili Network")
                    .info("启动参数已指定 {}={}，保留该值不覆盖", property, existing);
            return;
        }
        System.setProperty(property, Integer.toString(ioEventLoopThreads));
        org.slf4j.LoggerFactory.getLogger("Mili Network")
                .info("已请求 Netty IO 线程数={}（仅当 Netty 尚未初始化时生效；"
                        + "可用 jcmd <pid> Thread.print | grep -c 'Netty Server IO' 核对）", ioEventLoopThreads);
    }
}
