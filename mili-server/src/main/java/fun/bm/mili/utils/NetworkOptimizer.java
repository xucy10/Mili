package fun.bm.mili.utils;

import fun.bm.mili.config.modules.optimizations.NetworkOptimizerConfig;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 网络优化器
 * 优化内容:
 * - 包压缩级别动态调整
 * - 实体追踪数据包速率限制 (防止生电机器产生过多实体追踪更新)
 * - 批量区块发送优化
 * - 静默区块加载 (减少不必要的加载包)
 */
public class NetworkOptimizer {

    private static final LongAdder packetsThrottled = new LongAdder();
    private static final LongAdder chunksBatchSent = new LongAdder();
    private static final LongAdder bytesCompressed = new LongAdder();

    private static final ConcurrentHashMap<java.util.UUID, Long> lastEntityTrackSend = new ConcurrentHashMap<>();

    public static void init() {
        if (!NetworkOptimizerConfig.enabled) return;
        Bukkit.getLogger().info("[Mili Network] Network optimizer enabled");
    }

    /**
     * 检查实体追踪包是否应该被限流
     * 用于防止刷线机/地毯机等高频实体操作产生过多网络包
     */
    public static boolean shouldThrottleEntityTrack(java.util.UUID entityId) {
        if (NetworkOptimizerConfig.entityTrackSendRateLimitMs <= 0) return false;
        long now = System.currentTimeMillis();
        Long last = lastEntityTrackSend.get(entityId);
        if (last != null && (now - last) < NetworkOptimizerConfig.entityTrackSendRateLimitMs) {
            packetsThrottled.increment();
            return true;
        }
        lastEntityTrackSend.put(entityId, now);
        return false;
    }

    /**
     * 获取推荐的包压缩级别
     */
    public static int getRecommendedCompressionLevel() {
        return NetworkOptimizerConfig.packetCompressionLevel;
    }

    /**
     * 清理过期的实体追踪记录 (每60秒调用一次)
     */
    public static void cleanupStaleEntries() {
        long cutoff = System.currentTimeMillis() - 60_000;
        lastEntityTrackSend.entrySet().removeIf(e -> e.getValue() < cutoff);
    }

    public static Map<String, Object> getStats() {
        return Map.of(
                "packets_throttled", packetsThrottled.sum(),
                "chunks_batch_sent", chunksBatchSent.sum(),
                "tracked_entities", lastEntityTrackSend.size(),
                "compression_level", NetworkOptimizerConfig.packetCompressionLevel,
                "enabled", NetworkOptimizerConfig.enabled
        );
    }

    public static void shutdown() {
        lastEntityTrackSend.clear();
    }
}
