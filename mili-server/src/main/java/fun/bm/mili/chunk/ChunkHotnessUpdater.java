package fun.bm.mili.chunk;

import fun.bm.mili.config.modules.optimizations.ChunkSystemConfig;
import fun.bm.mili.rust.RustAnalyticsHelper;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;

final class ChunkHotnessUpdater {

    private ChunkHotnessUpdater() {}

    static void update(World world, WorldChunkData data) {
        Chunk[] loadedChunks = world.getLoadedChunks();
        if (loadedChunks.length == 0) {
            return;
        }

        Collection<? extends Player> players = world.getPlayers();
        if (players.isEmpty()) {
            clearPlayerProximity(data, loadedChunks);
            return;
        }

        double radiusSq = (double) ChunkSystemConfig.hotChunkRadius * ChunkSystemConfig.hotChunkRadius;

        double[] rustResults = RustAnalyticsHelper.analyzeChunkHotnessBatch(loadedChunks, players, world, radiusSq);
        if (rustResults != null && rustResults.length >= loadedChunks.length * 2) {
            applyRustResults(loadedChunks, data, rustResults);
            return;
        }

        updateInJava(world, data, loadedChunks, players, radiusSq);
    }

    private static void applyRustResults(Chunk[] loadedChunks, WorldChunkData data, double[] rustResults) {
        for (int i = 0; i < loadedChunks.length; i++) {
            Chunk chunk = loadedChunks[i];
            ChunkHotness hotness = data.getOrCreateHotness(chunk.getX(), chunk.getZ());
            boolean nearPlayer = rustResults[i * 2] != 0.0;
            double minDistSq = rustResults[i * 2 + 1];
            hotness.update(nearPlayer, minDistSq);
        }
    }

    private static void clearPlayerProximity(WorldChunkData data, Chunk[] loadedChunks) {
        for (Chunk chunk : loadedChunks) {
            data.getOrCreateHotness(chunk.getX(), chunk.getZ()).update(false, Double.MAX_VALUE);
        }
    }

    private static void updateInJava(
            World world,
            WorldChunkData data,
            Chunk[] loadedChunks,
            Collection<? extends Player> players,
            double radiusSq
    ) {
        for (Chunk chunk : loadedChunks) {
            int cx = chunk.getX();
            int cz = chunk.getZ();
            ChunkHotness hotness = data.getOrCreateHotness(cx, cz);

            boolean nearPlayer = false;
            double minDistSq = Double.MAX_VALUE;

            for (Player player : players) {
                if (!player.isOnline()) continue;
                if (player.getWorld() != world) continue;

                Location pLoc = player.getLocation();
                double dx = (pLoc.getBlockX() >> 4) - cx;
                double dz = (pLoc.getBlockZ() >> 4) - cz;
                double distSq = dx * dx + dz * dz;

                if (distSq < minDistSq) {
                    minDistSq = distSq;
                }
                if (distSq <= radiusSq) {
                    nearPlayer = true;
                    break;
                }
            }

            hotness.update(nearPlayer, minDistSq);
        }
    }
}
