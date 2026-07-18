package fun.bm.mili.chunk;

import org.bukkit.World;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class WorldChunkData {

    private final World world;
    private final ConcurrentHashMap<Long, ChunkHotness> hotnessMap = new ConcurrentHashMap<>();
    private final AtomicInteger totalHotChunks = new AtomicInteger(0);
    private final AtomicInteger activeChunks = new AtomicInteger(0);
    private final AtomicLong lastViewDistanceAdjustment = new AtomicLong(0);
    private final AtomicInteger viewDistanceAdjustmentCount = new AtomicInteger(0);

    private static final long VD_COOLDOWN_NS = 30_000_000_000L;
    private static final long STALE_MAX_AGE_NS = 300_000_000_000L;

    WorldChunkData(World world) {
        this.world = world;
    }

    ChunkHotness getOrCreateHotness(int chunkX, int chunkZ) {
        long key = ChunkKey.pack(chunkX, chunkZ);
        return hotnessMap.computeIfAbsent(key, k -> {
            totalHotChunks.incrementAndGet();
            return new ChunkHotness();
        });
    }

    ChunkHotness getHotness(int chunkX, int chunkZ) {
        return hotnessMap.get(ChunkKey.pack(chunkX, chunkZ));
    }

    void removeHotness(int chunkX, int chunkZ) {
        if (hotnessMap.remove(ChunkKey.pack(chunkX, chunkZ)) != null) {
            totalHotChunks.decrementAndGet();
        }
    }

    int getTotalHotChunks() {
        return totalHotChunks.get();
    }

    int getActiveChunks() {
        int count = 0;
        for (ChunkHotness hotness : hotnessMap.values()) {
            if (hotness.isActive()) count++;
        }
        activeChunks.set(count);
        return count;
    }

    boolean canAdjustViewDistance() {
        long now = System.nanoTime();
        long current = lastViewDistanceAdjustment.get();
        while (now - current >= VD_COOLDOWN_NS) {
            if (lastViewDistanceAdjustment.compareAndSet(current, now)) {
                return true;
            }
            current = lastViewDistanceAdjustment.get();
        }
        return false;
    }

    void recordViewDistanceAdjustment() {
        viewDistanceAdjustmentCount.incrementAndGet();
    }

    int getViewDistanceAdjustmentCount() {
        return viewDistanceAdjustmentCount.get();
    }

    int getTrackedChunkCount() {
        return hotnessMap.size();
    }

    void cleanupStaleEntries() {
        long now = System.nanoTime();
        hotnessMap.entrySet().removeIf(entry -> {
            ChunkHotness hotness = entry.getValue();
            if (!hotness.isActive() && now - hotness.getLastAccessTime() > STALE_MAX_AGE_NS) {
                totalHotChunks.decrementAndGet();
                return true;
            }
            return false;
        });
    }

    World getWorld() {
        return world;
    }
}