package fun.bm.mili.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerHeatmap {
    private static volatile boolean enabled = false;
    private static final ConcurrentHashMap<String, WorldHeatmapData> worldData = new ConcurrentHashMap<>();
    // Mili start - fix: use AtomicLong for thread-safe lastCleanupTime
    private static final AtomicLong lastCleanupTime = new AtomicLong(System.currentTimeMillis());
    // Mili end

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }

    public static void recordTick() {
        if (!enabled) return;
        for (World world : Bukkit.getWorlds()) {
            worldData.computeIfAbsent(world.getName(), k -> new WorldHeatmapData())
                    .recordPlayers(world);
        }
        cleanupOldData();
    }

    private static void cleanupOldData() {
        int maxMinutes = fun.bm.mili.config.modules.function.PlayerHeatmapConfig.maxHistoryMinutes;
        if (maxMinutes <= 0) return;

        // Mili start - fix: use AtomicLong compareAndSet for thread-safe cleanup timing
        long now = System.currentTimeMillis();
        long last = lastCleanupTime.get();
        if (now - last < 60_000) return;
        if (!lastCleanupTime.compareAndSet(last, now)) return;
        // Mili end

        long cutoff = now - (maxMinutes * 60_000L);
        for (WorldHeatmapData data : worldData.values()) {
            data.cleanup(cutoff);
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
        // Mili start - fix: preserve a real sliding time window instead of clearing the whole heat map on cleanup
        private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<Long>> heatMap = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Long> lastSeenTime = new ConcurrentHashMap<>();
        // Mili end
        private final AtomicLong totalRecords = new AtomicLong();

        void recordPlayers(World world) {
            int cellSize = fun.bm.mili.config.modules.function.PlayerHeatmapConfig.cellSizeBlocks >> 4;
            if (cellSize < 1) cellSize = 1;
            long now = System.currentTimeMillis();

            for (Player player : world.getPlayers()) {
                String uuid = player.getUniqueId().toString();
                // Mili start - fix: call getLocation() once to avoid mixed X/Z snapshots under movement
                Location loc = player.getLocation();
                int chunkX = loc.getBlockX() >> 4;
                int chunkZ = loc.getBlockZ() >> 4;
                // Mili end
                int cellX = chunkX / cellSize;
                int cellZ = chunkZ / cellSize;
                long key = pack(cellX, cellZ);

                heatMap.computeIfAbsent(key, unused -> new ConcurrentLinkedQueue<>()).add(now);
                totalRecords.incrementAndGet();
                lastSeenTime.put(uuid, now);
            }
        }

        int getUniquePlayerCount() { return lastSeenTime.size(); }

        int getHotCellCount() {
            int hotCells = 0;
            for (ConcurrentLinkedQueue<Long> visits : heatMap.values()) {
                if (visits.size() > 10) {
                    hotCells++;
                }
            }
            return hotCells;
        }

        long getTotalRecords() { return totalRecords.get(); }

        Map<Long, Integer> getHeatMap() {
            Map<Long, Integer> snapshot = new LinkedHashMap<>();
            for (Map.Entry<Long, ConcurrentLinkedQueue<Long>> entry : heatMap.entrySet()) {
                int visits = entry.getValue().size();
                if (visits > 0) {
                    snapshot.put(entry.getKey(), visits);
                }
            }
            return Collections.unmodifiableMap(snapshot);
        }

        void cleanup(long cutoffMs) {
            lastSeenTime.entrySet().removeIf(e -> e.getValue() < cutoffMs);

            for (Map.Entry<Long, ConcurrentLinkedQueue<Long>> entry : heatMap.entrySet()) {
                ConcurrentLinkedQueue<Long> visits = entry.getValue();
                long removed = 0;

                while (true) {
                    Long ts = visits.peek();
                    if (ts == null || ts >= cutoffMs) {
                        break;
                    }
                    if (visits.poll() != null) {
                        removed++;
                    }
                }

                if (removed != 0) {
                    totalRecords.addAndGet(-removed);
                }
                if (visits.isEmpty()) {
                    heatMap.remove(entry.getKey(), visits);
                }
            }
        }

        private static long pack(int x, int z) {
            return ((long) x << 32) | (z & 0xFFFFFFFFL);
        }
    }
}
