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

    WorldChunkData(World world) {
        this.world = world;
    }

    ChunkHotness getOrCreateHotness(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);

        return hotnessMap.computeIfAbsent(key, k -> {
            totalHotChunks.incrementAndGet();
            return new ChunkHotness();
        });
    }

    ChunkHotness getHotness(int chunkX, int chunkZ) {
        return hotnessMap.get(chunkKey(chunkX, chunkZ));
    }

    void removeHotness(int chunkX, int chunkZ) {
        Long key = chunkKey(chunkX, chunkZ);
        if (hotnessMap.remove(key) != null) {
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
        long last = lastViewDistanceAdjustment.get();
        long cooldownNs = 30_000_000_000L;

        if (now - last < cooldownNs) return false;

        return lastViewDistanceAdjustment.compareAndSet(last, now);
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
        long maxAgeNs = 300_000_000_000L;

        hotnessMap.entrySet().removeIf(entry -> {
            ChunkHotness hotness = entry.getValue();
            if (!hotness.isActive() && now - hotness.lastAccessTime > maxAgeNs) {
                totalHotChunks.decrementAndGet();
                return true;
            }
            return false;
        });
    }

    World getWorld() {
        return world;
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}