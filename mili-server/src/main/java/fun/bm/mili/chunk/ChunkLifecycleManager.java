package fun.bm.mili.chunk;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.optimizations.ChunkSystemConfig;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

final class ChunkLifecycleManager {

    private ChunkLifecycleManager() {}

    /**
     * Mili start - fix: Folia migration.
     * <p>Candidate selection stays on the calling (global) thread: every read it performs —
     * {@code World#getLoadedChunks()} and the {@link WorldChunkData} hotness lookups — is a plain
     * structure scan with no Folia thread assertion. The unload itself does assert region
     * ownership ({@code CraftWorld.unloadChunk0} calls {@code TickThread.ensureTickThread}), and
     * the keep-alive re-check reads {@code Chunk#getEntities()}, so both are handed to the
     * scheduler of the region that owns the chunk.
     * <p>Exactly {@code toUnload} regions are dispatched, matching the previous behaviour of
     * releasing at most {@code toUnload} chunks per pass; a candidate that turns out to be
     * keep-alive is simply skipped and picked up by the next pass.
     * Mili end
     */
    static void manage(World world, WorldChunkData data, AtomicLong totalUnloads, Plugin plugin) {
        Chunk[] loadedChunks = world.getLoadedChunks();
        int loadedCount = loadedChunks.length;
        int maxLoaded = ChunkSystemConfig.maxLoadedChunks;

        if (loadedCount <= maxLoaded) return;

        List<CandidateChunk> candidates = new ArrayList<>();

        for (Chunk chunk : loadedChunks) {
            ChunkHotness hotness = data.getHotness(chunk.getX(), chunk.getZ());
            if (hotness == null) continue;
            candidates.add(new CandidateChunk(chunk, hotness));
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
            final Chunk chunk = candidate.chunk;
            try {
                Bukkit.getRegionScheduler().run(plugin, world, chunk.getX(), chunk.getZ(), task -> {
                    if (isChunkKeepAlive(chunk)) return;
                    unloadChunkSafely(chunk);
                    totalUnloads.incrementAndGet();
                });
            } catch (Throwable ignored) {
                // Mili start - fix: world unloaded between selection and dispatch
            }
        }
        // Mili end
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
