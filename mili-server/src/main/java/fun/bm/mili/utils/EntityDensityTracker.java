package fun.bm.mili.utils;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class EntityDensityTracker {
    private static volatile boolean enabled = false;
    private static final ConcurrentHashMap<String, WorldDensity> worldDensities = new ConcurrentHashMap<>();
    private static final AtomicLong totalUpdates = new AtomicLong();

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }

    public static void update() {
        if (!enabled) return;

        for (World world : Bukkit.getWorlds()) {
            WorldDensity density = worldDensities.computeIfAbsent(world.getName(), k -> new WorldDensity());
            density.update(world);
        }
        totalUpdates.incrementAndGet();
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("Enabled", enabled);
        stats.put("Total Updates", totalUpdates.get());
        for (Map.Entry<String, WorldDensity> entry : worldDensities.entrySet()) {
            WorldDensity d = entry.getValue();
            stats.put(entry.getKey() + " - Total Entities", d.totalEntities.get());
            stats.put(entry.getKey() + " - Living Entities", d.livingEntities.get());
            stats.put(entry.getKey() + " - Max Density", d.maxDensity.get());
            stats.put(entry.getKey() + " - Hot Cells", d.hotCells.get());
        }
        return stats;
    }

    public static int getDensityAt(World world, int blockX, int blockZ) {
        WorldDensity density = worldDensities.get(world.getName());
        if (density == null) return 0;
        int cellSize = fun.bm.mili.config.modules.optimizations.EntityDensityHeatmapConfig.cellSize;
        int cellX = blockX / cellSize;
        int cellZ = blockZ / cellSize;
        long key = pack(cellX, cellZ);
        return density.cells.getOrDefault(key, 0);
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static class WorldDensity {
        final ConcurrentHashMap<Long, Integer> cells = new ConcurrentHashMap<>();
        final AtomicInteger totalEntities = new AtomicInteger();
        final AtomicInteger livingEntities = new AtomicInteger();
        final AtomicInteger maxDensity = new AtomicInteger();
        final AtomicInteger hotCells = new AtomicInteger();

        void update(World world) {
            cells.clear();
            totalEntities.set(0);
            livingEntities.set(0);
            maxDensity.set(0);
            hotCells.set(0);

            int cellSize = fun.bm.mili.config.modules.optimizations.EntityDensityHeatmapConfig.cellSize;
            int threshold = fun.bm.mili.config.modules.optimizations.EntityDensityHeatmapConfig.maxDensityThreshold;

            for (Entity entity : world.getEntities()) {
                totalEntities.incrementAndGet();
                if (entity instanceof LivingEntity) livingEntities.incrementAndGet();

                // Mili start - fix: call getLocation() once to prevent race condition between two calls
                org.bukkit.Location loc = entity.getLocation();
                int cellX = loc.getBlockX() / cellSize;
                int cellZ = loc.getBlockZ() / cellSize;
                // Mili end
                long key = pack(cellX, cellZ);
                cells.merge(key, 1, Integer::sum);
            }

            for (int count : cells.values()) {
                if (count > maxDensity.get()) maxDensity.set(count);
                if (count > threshold) hotCells.incrementAndGet();
            }
        }
    }
}
