package fun.bm.mili.utils;

import fun.bm.mili.config.modules.optimizations.DynamicViewDistanceConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class DynamicViewDistanceManager {
    private static volatile boolean enabled = false;
    private static final ConcurrentHashMap<String, PlayerVDState> playerStates = new ConcurrentHashMap<>();
    private static final AtomicLong totalAdjustments = new AtomicLong();
    private static long lastAdjustTime = 0;

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }

    // Mili start - fix: add method to clean up offline player entries to prevent map growth OOM
    public static void onPlayerQuit(String uuid) {
        playerStates.remove(uuid);
    }
    // Mili end

    public static void tick() {
        if (!enabled) return;

        long now = System.currentTimeMillis();
        long intervalMs = DynamicViewDistanceConfig.adjustIntervalSeconds * 1000L;
        if (now - lastAdjustTime < intervalMs) return;
        lastAdjustTime = now;

        double currentTps = getCurrentTps();

        for (World world : Bukkit.getWorlds()) {
            for (Player player : world.getPlayers()) {
                adjustPlayerViewDistance(player, currentTps);
            }
        }
    }

    private static void adjustPlayerViewDistance(Player player, double currentTps) {
        String uuid = player.getUniqueId().toString();
        PlayerVDState state = playerStates.computeIfAbsent(uuid, k -> new PlayerVDState());

        int currentVD = player.getViewDistance();
        int targetVD = currentVD;

        if (currentTps > DynamicViewDistanceConfig.tpsHighThreshold) {
            targetVD = Math.min(currentVD + 1, DynamicViewDistanceConfig.maxViewDistance);
        } else if (currentTps < DynamicViewDistanceConfig.tpsLowThreshold) {
            targetVD = Math.max(currentVD - 1, DynamicViewDistanceConfig.minViewDistance);
        }

        int nearbyPlayers = countNearbyPlayers(player, targetVD);
        if (nearbyPlayers > 10) {
            double densityPenalty = nearbyPlayers * DynamicViewDistanceConfig.playerDensityWeight;
            targetVD = Math.max(DynamicViewDistanceConfig.minViewDistance,
                    (int)(targetVD - densityPenalty));
        }

        if (targetVD != currentVD) {
            state.adjustments++;
            totalAdjustments.incrementAndGet();
            player.setViewDistance(targetVD);
        }
    }

    private static int countNearbyPlayers(Player player, int viewDistance) {
        int count = 0;
        // Mili start - fix: call getLocation() once to prevent race condition
        org.bukkit.Location playerLoc = player.getLocation();
        double thresholdSq = (viewDistance * 16L) * (viewDistance * 16L);
        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player)) continue;
            if (playerLoc.distanceSquared(other.getLocation()) < thresholdSq) {
                count++;
            }
        }
        // Mili end
        return count;
    }

    private static double getCurrentTps() {
        try {
            org.bukkit.scoreboard.Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
            if (main != null) {
                var criteria = main.getObjective("mili_tps");
                if (criteria != null) {
                    var entry = main.getEntries().stream().findFirst();
                    if (entry.isPresent()) {
                        var score = criteria.getScore(entry.get());
                        return score.getScore() / 20.0;
                    }
                }
            }
        // Mili start - fix: catch Throwable instead of Exception to handle Errors
        } catch (Throwable ignored) {}
        // Mili end

        return 20.0;
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("Enabled", enabled);
        stats.put("Tracked Players", playerStates.size());
        stats.put("Total Adjustments", totalAdjustments.get());
        return stats;
    }

    private static class PlayerVDState {
        int adjustments = 0;
    }
}
