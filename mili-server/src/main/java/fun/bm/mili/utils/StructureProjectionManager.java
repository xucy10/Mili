package fun.bm.mili.utils;

import fun.bm.mili.config.modules.function.StructureProjectionConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class StructureProjectionManager {
    private static volatile boolean enabled = false;
    private static final ConcurrentHashMap<String, List<Projection>> playerProjections = new ConcurrentHashMap<>();
    private static final AtomicLong totalProjections = new AtomicLong();

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }

    public static boolean createProjection(Player player, Location origin,
                                           Map<Location, org.bukkit.block.data.BlockData> structure,
                                           String name) {
        if (!enabled) return false;

        String uuid = player.getUniqueId().toString();
        List<Projection> projections = playerProjections.computeIfAbsent(uuid, k -> new ArrayList<>());

        if (projections.size() >= StructureProjectionConfig.maxProjectionsPerPlayer) {
            return false;
        }

        if (!StructureProjectionConfig.allowedWorlds.isEmpty() &&
                !StructureProjectionConfig.allowedWorlds.contains(player.getWorld().getName())) {
            return false;
        }

        double distance = player.getLocation().distance(origin);
        if (distance > StructureProjectionConfig.projectionRange) {
            return false;
        }

        Projection proj = new Projection(name, origin, structure, player.getWorld().getName());
        projections.add(proj);
        totalProjections.incrementAndGet();

        sendProjectionPreview(player, proj);
        return true;
    }

    public static boolean removeProjection(Player player, String name) {
        String uuid = player.getUniqueId().toString();
        List<Projection> projections = playerProjections.get(uuid);
        if (projections == null) return false;

        Iterator<Projection> it = projections.iterator();
        while (it.hasNext()) {
            Projection proj = it.next();
            if (proj.name.equals(name)) {
                it.remove();
                clearProjectionPreview(player, proj);
                return true;
            }
        }
        return false;
    }

    public static void clearAllProjections(Player player) {
        String uuid = player.getUniqueId().toString();
        List<Projection> projections = playerProjections.remove(uuid);
        if (projections != null) {
            for (Projection proj : projections) {
                clearProjectionPreview(player, proj);
            }
        }
    }

    public static List<Projection> getPlayerProjections(Player player) {
        return playerProjections.getOrDefault(player.getUniqueId().toString(), Collections.emptyList());
    }

    private static void sendProjectionPreview(Player player, Projection proj) {
        for (Map.Entry<Location, org.bukkit.block.data.BlockData> entry : proj.structure.entrySet()) {
            Location blockLoc = entry.getKey().clone().add(proj.origin);
            if (blockLoc.getWorld() != null) {
                player.sendBlockChange(blockLoc, org.bukkit.Material.GLASS.createBlockData());
            }
        }
    }

    private static void clearProjectionPreview(Player player, Projection proj) {
        for (Location loc : proj.structure.keySet()) {
            Location blockLoc = loc.clone().add(proj.origin);
            if (blockLoc.getWorld() != null) {
                player.sendBlockChange(blockLoc, blockLoc.getBlock().getBlockData());
            }
        }
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("Enabled", enabled);
        stats.put("Total Projections", totalProjections.get());
        stats.put("Active Players", playerProjections.size());
        return stats;
    }

    public static class Projection {
        public final String name;
        public final Location origin;
        public final Map<Location, org.bukkit.block.data.BlockData> structure;
        public final String worldName;

        public Projection(String name, Location origin,
                          Map<Location, org.bukkit.block.data.BlockData> structure, String worldName) {
            this.name = name;
            this.origin = origin.clone();
            this.structure = structure;
            this.worldName = worldName;
        }
    }
}
