package fun.bm.mili.utils;

import fun.bm.mili.config.modules.optimizations.RedstoneDirtyTrackingConfig;
import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RedstoneDirtyTracker {
    private static volatile boolean enabled = false;
    private static final ConcurrentHashMap<Long, DirtyBlock> dirtyBlocks = new ConcurrentHashMap<>();
    private static final AtomicInteger totalUpdates = new AtomicInteger();
    private static final AtomicInteger coalescedUpdates = new AtomicInteger();
    private static final AtomicInteger processedBatches = new AtomicInteger();

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }

    public static void markDirty(BlockPos pos, String worldName) {
        if (!enabled) return;

        long key = pack(pos);
        int radius = RedstoneDirtyTrackingConfig.coalesceRadius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long nearbyKey = pack(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    DirtyBlock existing = dirtyBlocks.get(nearbyKey);
                    if (existing != null) {
                        existing.coalescedCount.incrementAndGet();
                        coalescedUpdates.incrementAndGet();
                        return;
                    }
                }
            }
        }

        dirtyBlocks.put(key, new DirtyBlock(pos, worldName));
        totalUpdates.incrementAndGet();
    }

    public static List<DirtyBlock> drainDirtyBlocks() {
        if (!enabled) return Collections.emptyList();

        int batchSize = RedstoneDirtyTrackingConfig.batchSize;
        List<DirtyBlock> result = new ArrayList<>(batchSize);

        Iterator<Map.Entry<Long, DirtyBlock>> it = dirtyBlocks.entrySet().iterator();
        while (it.hasNext() && result.size() < batchSize) {
            result.add(it.next().getValue());
            it.remove();
        }

        processedBatches.incrementAndGet();
        return result;
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("Enabled", enabled);
        stats.put("Pending Dirty Blocks", dirtyBlocks.size());
        stats.put("Total Updates", totalUpdates.get());
        stats.put("Coalesced Updates", coalescedUpdates.get());
        stats.put("Batches Processed", processedBatches.get());
        return stats;
    }

    // Mili start - fix: use BlockPos.asLong() to avoid hash collisions from bitmask truncation
    private static long pack(BlockPos pos) {
        return pos.asLong();
    }

    private static long pack(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }
    // Mili end

    public static class DirtyBlock {
        public final BlockPos pos;
        public final String worldName;
        public final AtomicInteger coalescedCount = new AtomicInteger(1);

        public DirtyBlock(BlockPos pos, String worldName) {
            this.pos = pos;
            this.worldName = worldName;
        }
    }
}
