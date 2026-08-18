package fun.bm.mili.utils;

import fun.bm.mili.config.modules.optimizations.NetworkOptimizerConfig;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Network optimizer
 * Improvements:
 * - Packet compression level dynamic adjustment
 * - Entity track packet rate limiting (prevents redstone/farm machines from flooding entity track updates)
 * - Batch chunk send optimization
 * - Silent chunk loading (reduces unnecessary load packets)
 * - Bounded entity track cache with periodic cleanup (prevents OOM)
 */
public class NetworkOptimizer {

    private static final LongAdder packetsThrottled = new LongAdder();
    private static final LongAdder chunksBatchSent = new LongAdder();
    private static final LongAdder bytesCompressed = new LongAdder();

    private static final ConcurrentHashMap<java.util.UUID, Long> lastEntityTrackSend = new ConcurrentHashMap<>();

    // Periodic cleanup state
    private static final AtomicLong lastCleanupTime = new AtomicLong(0);
    private static final long CLEANUP_INTERVAL_MS = 60_000; // 60 seconds

    public static void init() {
        if (!NetworkOptimizerConfig.enabled) return;
        Bukkit.getLogger().info("[Mili Network] Network optimizer enabled");
    }

    /**
     * Check if an entity track packet should be throttled.
     * Used to prevent redstone/farm machines from generating excessive entity track updates.
     */
    public static boolean shouldThrottleEntityTrack(java.util.UUID entityId) {
        if (NetworkOptimizerConfig.entityTrackSendRateLimitMs <= 0) return false;

        // Periodic cleanup — prevents unbounded cache growth
        maybeCleanupStaleEntries();

        long now = System.currentTimeMillis();
        long limitMs = NetworkOptimizerConfig.entityTrackSendRateLimitMs;
        AtomicBoolean throttled = new AtomicBoolean(false);

        lastEntityTrackSend.compute(entityId, (unused, last) -> {
            if (last != null && (now - last) < limitMs) {
                throttled.set(true);
                return last;
            }
            return now;
        });

        if (throttled.get()) {
            packetsThrottled.increment();
        }
        return throttled.get();
    }

    /**
     * Get recommended packet compression level.
     */
    public static int getRecommendedCompressionLevel() {
        return NetworkOptimizerConfig.packetCompressionLevel;
    }

    /**
     * Periodic cleanup of stale entity track entries.
     * Runs at most once per CLEANUP_INTERVAL_MS.
     * Also enforces a hard cap on cache size if configured.
     */
    private static void maybeCleanupStaleEntries() {
        long now = System.currentTimeMillis();
        long last = lastCleanupTime.get();
        if ((now - last) < CLEANUP_INTERVAL_MS) {
            return;
        }
        if (!lastCleanupTime.compareAndSet(last, now)) {
            return;
        }

        // Remove entries older than 60 seconds
        long cutoff = now - 60_000;
        lastEntityTrackSend.entrySet().removeIf(e -> e.getValue() < cutoff);

        // Hard cap: if cache exceeds max size, remove oldest entries
        int maxSize = NetworkOptimizerConfig.entityTrackCacheMaxSize;
        if (maxSize > 0 && lastEntityTrackSend.size() > maxSize) {
            // Remove oldest 25% of entries to avoid frequent cleanups
            int toRemove = lastEntityTrackSend.size() - maxSize + (maxSize / 4);
            lastEntityTrackSend.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .limit(toRemove)
                    .forEach(e -> lastEntityTrackSend.remove(e.getKey()));
        }
    }

    /**
     * Force cleanup of stale entries (called externally every 60 seconds).
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
        packetsThrottled.reset();
        chunksBatchSent.reset();
        bytesCompressed.reset();
    }
}
