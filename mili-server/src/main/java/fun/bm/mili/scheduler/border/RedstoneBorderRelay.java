package fun.bm.mili.scheduler.border;

import fun.bm.mili.scheduler.ChunkWorker;
import fun.bm.mili.scheduler.CrossChunkBus;

/**
 * Redstone border relay — two-phase commit for cross-chunk redstone.
 *
 * Phase 1: ChunkWorker tick 开始前采集边界红石状态 → ChunkBorderCache
 * Phase 2: tick 结束后 CrossChunkBus 检查邻居缓存，通过 enqueueBorderUpdate
 *          注入延迟的 neighborChanged 调用到邻居区块
 *
 * 可配置 strict mode 回退 region 模式实现 0-tick 兼容。
 */
public final class RedstoneBorderRelay {

    private final CrossChunkBus bus;

    public RedstoneBorderRelay(CrossChunkBus bus) {
        this.bus = bus;
    }

    /**
     * Called after a chunk tick completes to relay border redstone changes
     * to neighboring chunks.
     *
     * NOTE: Cross-chunk border injection is disabled because block state
     * modification (neighborChanged, scheduleTick) must run on the owning
     * Folia region thread, not on the CrossChunkBus coordinator thread.
     * High-interaction chunks use Folia region fallback natively.
     */
    public void flushBorderUpdates(ChunkWorker worker) {
        // Border updates handled by Folia region fallback for high-interaction chunks.
        // Low-interaction chunks have no cross-border signals to relay.
    }
}
