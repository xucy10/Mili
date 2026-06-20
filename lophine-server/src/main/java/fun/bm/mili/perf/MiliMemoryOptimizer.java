package fun.bm.mili.perf;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.misc.MemoryOptConfig;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

/**
 * mili - Folia 服务器内存优化器 / Folia server memory optimizer.
 *
 * <p>在全局 tick 中周期性执行轻量级内存清理 / Periodically performs lightweight
 * memory cleanup during global tick:
 * <ul>
 *   <li>清理死亡/断开玩家的追踪状态 / Clean stale player tracking states</li>
 *   <li>软引用缓存压力释放 / Soft-reference cache pressure release</li>
 *   <li>周期性 GC 提示 (可配置间隔) / Periodic GC hint (configurable interval)</li>
 * </ul>
 *
 * <p>设计原则: 零分配、低开销、不阻塞 tick / Design: zero-alloc, low overhead, non-blocking.
 */
public final class MiliMemoryOptimizer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MiliMemoryOptimizer() {}

    /** 上次 GC 提示的时间 / Last GC hint timestamp. */
    private static final AtomicLong LAST_GC_HINT_NS = new AtomicLong(0);

    /** 上次状态清理的时间 / Last state cleanup timestamp. */
    private static final AtomicLong LAST_STATE_CLEANUP_NS = new AtomicLong(0);

    /**
     * 在全局 tick 中调用 / Called during global tick.
     *
     * <p>每 200 ticks (10 秒) 执行一次状态清理 / Runs state cleanup every 200 ticks (10s).
     * GC 提示按配置间隔触发 / GC hint fires at configured interval.
     */
    public static void onGlobalTick() {
        if (!MemoryOptConfig.enabled) return;

        final long now = System.nanoTime();

        // 状态清理: 每 10 秒 / State cleanup: every 10 seconds
        if (now - LAST_STATE_CLEANUP_NS.get() > 10_000_000_000L) {
            LAST_STATE_CLEANUP_NS.set(now);
            cleanupStaleStates();
        }

        // GC 提示: 按配置间隔 / GC hint: at configured interval
        final long gcIntervalNs = MemoryOptConfig.gcHintIntervalSeconds * 1_000_000_000L;
        if (gcIntervalNs > 0 && now - LAST_GC_HINT_NS.get() > gcIntervalNs) {
            LAST_GC_HINT_NS.set(now);
            hintGC();
        }
    }

    /**
     * 清理死亡/断开玩家的追踪状态 / Clean tracking states of dead/disconnected players.
     *
     * <p>遍历 {@link MiliChunkPreloader} 的追踪状态表，移除不再在线的玩家 /
     * Iterates MiliChunkPreloader's tracking table, removing offline players.
     */
    private static void cleanupStaleStates() {
        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;

        try {
            // 清理 MiliChunkPreloader 中断开玩家的状态 / Clean disconnected player states
            int removed = MiliChunkPreloader.cleanupOfflinePlayers();

            if (MemoryOptConfig.debug) {
                long usedMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
                long maxMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);
                LOGGER.debug("[MemOpt] Memory: {}MB / {}MB | ChunkPreloader cleaned {} entries",
                        usedMB, maxMB, removed);
            }
        } catch (Throwable t) {
            LOGGER.debug("[MemOpt] State cleanup failed: {}", t.getMessage());
        }
    }

    /**
     * GC 提示 — 已禁用 System.gc()，仅记录日志 / GC hint — System.gc() disabled, log only.
     *
     * <p>不再调用 System.gc() 或分配临时对象，因为 STW 停顿会导致实体追踪去同步 /
     * No longer calls System.gc() or allocates temp objects, as STW pauses
     * cause entity tracking desynchronization.
     */
    private static void hintGC() {
        if (!MemoryOptConfig.gcHintEnabled) return;

        final long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        final long max = Runtime.getRuntime().maxMemory();
        final double usagePct = (double) used / max * 100.0;

        if (usagePct > MemoryOptConfig.gcHintHeapThreshold) {
            // 不再调用 System.gc() — STW 停顿会导致实体瞬移/打不到
            // No longer calls System.gc() — STW pauses cause entity teleport/desync
            if (MemoryOptConfig.debug) {
                LOGGER.debug("[MemOpt] Heap usage high: {}% (GC hint suppressed to avoid entity stutter)",
                        String.format("%.1f", usagePct));
            }
        }
    }

    /**
     * 获取当前内存使用摘要 / Get current memory usage summary.
     *
     * @return 格式化字符串 / Formatted string: "usedMB/maxMB (pct%)"
     */
    public static String getMemorySummary() {
        final long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        final long max = Runtime.getRuntime().maxMemory();
        final long usedMB = used / (1024 * 1024);
        final long maxMB = max / (1024 * 1024);
        final double pct = (double) used / max * 100.0;
        return String.format("%dMB / %dMB (%.1f%%)", usedMB, maxMB, pct);
    }
}
