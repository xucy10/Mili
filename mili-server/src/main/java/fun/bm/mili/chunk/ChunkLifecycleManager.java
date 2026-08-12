package fun.bm.mili.chunk;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.optimizations.ChunkSystemConfig;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

final class ChunkLifecycleManager {

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
            if (!isChunkKeepAlive(chunk)) {
                candidates.add(new CandidateChunk(chunk, hotness));
            }
        }

        if (candidates.isEmpty()) return;

        candidates.sort(Comparator.comparingDouble(c -> c.hotness.getScore()));

        int toUnload = Math.min(
                candidates.size(),
                loadedCount - (int) (maxLoaded * ChunkSystemConfig.unloadSafetyMargin)
        );

        for (int i = 0; i < toUnload; i++) {
            CandidateChunk candidate = candidates.get(i);
            unloadChunkSafely(candidate.chunk);
            totalUnloads.incrementAndGet();
        }
    }

    private static boolean isChunkKeepAlive(Chunk chunk) {
        if (chunk.getEntities().length > 0) return true;

        for (Player player : chunk.getWorld().getPlayers()) {
            Location eyeLoc = player.getEyeLocation();
            int dx = (eyeLoc.getBlockX() >> 4) - chunk.getX();
            int dz = (eyeLoc.getBlockZ() >> 4) - chunk.getZ();
            if (dx * dx + dz * dz <= 256) {
                return true;
            }
        }
        return false;
    }

    static void unloadChunkSafely(Chunk chunk) {
        try {
            if (chunk.isForceLoaded() || chunk.isLoaded()) {
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