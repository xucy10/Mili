package fun.bm.mili.chunk;

import fun.bm.mili.config.modules.optimizations.ChunkSystemConfig;
import org.bukkit.Chunk;
import org.bukkit.World;

final class ChunkViewDistanceOptimizer {

    private ChunkViewDistanceOptimizer() {}

    static void optimize(World world, WorldChunkData data) {
        if (!ChunkSystemConfig.dynamicViewDistance) return;
        if (world.getPlayers().isEmpty()) return;

        Chunk[] loadedChunks = world.getLoadedChunks();
        double avgLoad = 0;
        int sampleCount = 0;

        for (Chunk chunk : loadedChunks) {
            ChunkHotness hotness = data.getHotness(chunk.getX(), chunk.getZ());
            if (hotness != null && hotness.isActive()) {
                avgLoad += hotness.getAccessCount();
                sampleCount++;
            }
        }

        if (sampleCount == 0) return;
        avgLoad /= sampleCount;

        int currentVD = world.getViewDistance();
        int targetVD = currentVD;

        if (avgLoad > ChunkSystemConfig.vdDecreaseThreshold && currentVD > ChunkSystemConfig.minViewDistance) {
            targetVD = currentVD - 1;
        } else if (avgLoad < ChunkSystemConfig.vdIncreaseThreshold && currentVD < ChunkSystemConfig.maxViewDistance) {
            targetVD = currentVD + 1;
        }

        if (targetVD != currentVD && data.canAdjustViewDistance()) {
            world.setViewDistance(targetVD);
            data.recordViewDistanceAdjustment();
        }
    }
}