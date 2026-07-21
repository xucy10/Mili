package fun.bm.mili.utils;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerHeatmap {
    private static volatile boolean enabled = false;
    private static final ConcurrentHashMap<String, WorldHeatmapData> worldData = new ConcurrentHashMap<>();

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }

    public static void recordTick() {
        if (!enabled) return;
        for (World world : Bukkit.getWorlds()) {
            worldData.computeIfAbsent(world.getName(), k -> new WorldHeatmapData())
                    .recordPlayers(world);
        }
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        for (Map.Entry<String, WorldHeatmapData> entry : worldData.entrySet()) {
            WorldHeatmapData data = entry.getValue();
            stats.put(entry.getKey() + " - Unique Players", data.getUniquePlayerCount());
            stats.put(entry.getKey() + " - Hot Cells", data.getHotCellCount());
            stats.put(entry.getKey() + " - Total Records", data.getTotalRecords());
        }
        return stats;
    }

    public static Map<String, Map<Long, Integer>> getWorldHeatmaps() {
        Map<String, Map<Long, Integer>> result = new LinkedHashMap<>();
        for (Map.Entry<String, WorldHeatmapData> entry : worldData.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getHeatMap());
        }
        return result;
    }

    public static void exportToFile(String worldName) throws IOException {
        WorldHeatmapData data = worldData.get(worldName);
        if (data == null) return;

        File dir = new File(fun.bm.mili.config.modules.function.PlayerHeatmapConfig.exportPath);
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, worldName + "_heatmap.csv");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            pw.println("chunkX,chunkZ,visits");
            Map<Long, Integer> heat = data.getHeatMap();
            for (Map.Entry<Long, Integer> entry : heat.entrySet()) {
                int x = (int) (entry.getKey() >> 32);
                int z = entry.getKey().intValue();
                pw.println(x + "," + z + "," + entry.getValue());
            }
        }
    }

    public static void reset() {
        worldData.clear();
    }

    private static class WorldHeatmapData {
        private final ConcurrentHashMap<Long, Integer> heatMap = new ConcurrentHashMap<>();
        private final CopyOnWriteArrayList<String> trackedPlayers = new CopyOnWriteArrayList<>();
        private final ConcurrentHashMap<String, Long> lastSeenPositions = new ConcurrentHashMap<>();
        private final AtomicLong totalRecords = new AtomicLong();

        void recordPlayers(World world) {
            int cellSize = fun.bm.mili.config.modules.function.PlayerHeatmapConfig.cellSizeBlocks >> 4;
            if (cellSize < 1) cellSize = 1;

            for (Player player : world.getPlayers()) {
                String uuid = player.getUniqueId().toString();
                int chunkX = player.getLocation().getBlockX() >> 4;
                int chunkZ = player.getLocation().getBlockZ() >> 4;
                int cellX = chunkX / cellSize;
                int cellZ = chunkZ / cellSize;
                long key = pack(cellX, cellZ);

                heatMap.merge(key, 1, Integer::sum);
                totalRecords.incrementAndGet();

                if (!trackedPlayers.contains(uuid)) {
                    trackedPlayers.add(uuid);
                }

                long newKey = pack(chunkX, chunkZ);
                lastSeenPositions.put(uuid, newKey);
            }
        }

        int getUniquePlayerCount() { return trackedPlayers.size(); }

        int getHotCellCount() {
            return (int) heatMap.values().stream().filter(v -> v > 10).count();
        }

        long getTotalRecords() { return totalRecords.get(); }

        Map<Long, Integer> getHeatMap() {
            return Collections.unmodifiableMap(heatMap);
        }

        private static long pack(int x, int z) {
            return ((long) x << 32) | (z & 0xFFFFFFFFL);
        }
    }
}
