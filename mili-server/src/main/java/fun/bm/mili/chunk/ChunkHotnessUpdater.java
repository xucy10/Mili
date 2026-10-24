package fun.bm.mili.chunk;

import fun.bm.mili.config.modules.optimizations.ChunkSystemConfig;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

final class ChunkHotnessUpdater {

    private ChunkHotnessUpdater() {}

    static void update(World world, WorldChunkData data) {
        Collection<? extends Player> players = world.getPlayers();
        if (players.isEmpty()) return;

        Set<Player> playerSet = players instanceof Set ? (Set<Player>) players : new HashSet<>(players);
        Chunk[] loadedChunks = world.getLoadedChunks();

        double radiusSq = (double) ChunkSystemConfig.hotChunkRadius * ChunkSystemConfig.hotChunkRadius;

        for (Chunk chunk : loadedChunks) {
            int cx = chunk.getX();
            int cz = chunk.getZ();
            ChunkHotness hotness = data.getOrCreateHotness(cx, cz);

            boolean nearPlayer = false;
            double minDistSq = Double.MAX_VALUE;

            for (Player player : playerSet) {
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