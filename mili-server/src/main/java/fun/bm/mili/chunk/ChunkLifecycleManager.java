package fun.bm.mili.chunk;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.optimizations.ChunkSystemConfig;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

final class ChunkLifecycleManager {

    private static final double KEEP_ALIVE_DISTANCE_SQ = 256.0D;

    private ChunkLifecycleManager() {}

    static void manage(World world, WorldChunkData data, AtomicLong totalUnloads) {
        Chunk[] loadedChunks = world.getLoadedChunks();
        int loadedCount = loadedChunks.length;
        int maxLoaded = ChunkSystemConfig.maxLoadedChunks;

        if (loadedCount <= maxLoaded) return;

        List<CandidateChunk> candidates = new ArrayList<>();

        for (Chunk chunk : loadedChunks) {
            ChunkHotness hotness = data.getHotness(chunk.getX(), chunk.getZ());
            if (hotness == null) continue;
            if (!isChunkKeepAlive(chunk, hotness)) {
                candidates.add(new CandidateChunk(chunk, hotness));
            }
        }

        if (candidates.isEmpty()) return;

        candidates.sort(Comparator.comparingDouble(c -> c.hotness.getScore()));

        int toUnload = Math.min(
                candidates.size(),
                loadedCount - (int) (maxLoaded * ChunkSystemConfig.unloadSafetyMargin)
        );
        if (toUnload <= 0) return;

        for (int i = 0; i < toUnload; i++) {
            CandidateChunk candidate = candidates.get(i);
            unloadChunkSafely(candidate.chunk);
            totalUnloads.incrementAndGet();
        }
    }

    private static boolean isChunkKeepAlive(Chunk chunk, ChunkHotness hotness) {
        if (chunk.isForceLoaded()) return true;
        if (chunk.getEntities().length > 0) return true;
        return hotness.getNearestPlayerDistanceSq() <= KEEP_ALIVE_DISTANCE_SQ;
    }

    static void unloadChunkSafely(Chunk chunk) {
        if (chunk.isForceLoaded()) {
            return;
        }
        try {
            if (chunk.isLoaded()) {
                chunk.unload(true);
            }
        // Mili start - fix: catch Throwable to prevent silent thread death on Error (StackOverflowError/OOM)
        } catch (Throwable e) {
        // Mili end
            LogUtils.getLogger().debug(
                    "[Mili] Failed to unload chunk ({}, {})",
                    chunk.getX(), chunk.getZ()
            );
        }
    }

    private static class CandidateChunk {
        final Chunk chunk;
        final ChunkHotness hotness;

        CandidateChunk(Chunk chunk, ChunkHotness hotness) {
            this.chunk = chunk;
            this.hotness = hotness;
        }
    }
}