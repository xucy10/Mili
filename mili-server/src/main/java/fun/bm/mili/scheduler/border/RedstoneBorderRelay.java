package fun.bm.mili.scheduler.border;

import fun.bm.mili.scheduler.ChunkBorderCache;
import fun.bm.mili.scheduler.ChunkIndependentScheduler;
import fun.bm.mili.scheduler.ChunkWorker;
import fun.bm.mili.scheduler.CrossChunkBus;
import net.minecraft.server.level.ServerLevel;

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
     * to neighboring chunks. The actual injection is deferred by 1 tick
     * via CrossChunkBus.
     */
    public void flushBorderUpdates(ChunkWorker worker) {
        if (worker == null || worker.getBorderCache() == null) return;
        // Only low-interaction chunks need border relay (high-interaction uses Folia region fallback)
        if (worker.isHighInteraction()) return;

        ChunkBorderCache cache = worker.getBorderCache();
        if (cache == null) return;

        ServerLevel level = worker.getLevel();
        if (level == null) return;

        // Broadcast to all 4 neighbors
        for (ChunkBorderCache.BorderFace face : ChunkBorderCache.BorderFace.values()) {
            int nx = worker.getChunkX() + face.getDirection().getStepX();
            int nz = worker.getChunkZ() + face.getDirection().getStepZ();

            ChunkWorker neighbor = ChunkIndependentScheduler.getInstance(level).getWorker(nx, nz);
            if (neighbor == null || neighbor.isReleased()) continue;

            long neighborKey = ((long) nx << 32) | (nz & 0xFFFFFFFFL);

            bus.enqueueBorderUpdate(worker, neighborKey, () -> {
                // This runs on CrossChunkBus coordinator thread, 1 tick later
                cache.injectBorderUpdates(neighbor, face);
            });
        }
    }
}
