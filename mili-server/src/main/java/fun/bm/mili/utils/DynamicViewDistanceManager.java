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
    // Mili start - fix: lastAdjustTime was a plain static long mutated from multiple region
    // threads (check-then-act race), use CAS so exactly one region thread runs the adjustment pass
    private static final AtomicLong lastAdjustTime = new AtomicLong();
    private static final me.earthme.luminol.utils.NullPlugin SCHEDULER_PLUGIN = new me.earthme.luminol.utils.NullPlugin();
    // Mili end - fix: lastAdjustTime race

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }

    public static void tick() {
        if (!enabled) return;

        long now = System.currentTimeMillis();
        long intervalMs = DynamicViewDistanceConfig.adjustIntervalSeconds * 1000L;
        // Mili start - fix: CAS guard instead of unsynchronized check-then-act on a static long
        long last = lastAdjustTime.get();
        if (now - last < intervalMs) return;
        if (!lastAdjustTime.compareAndSet(last, now)) return;
        // Mili end - fix: CAS guard

        double currentTps = getCurrentTps();

        java.util.Set<String> onlineUuids = new java.util.HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (Player player : world.getPlayers()) {
                onlineUuids.add(player.getUniqueId().toString());
                adjustPlayerViewDistance(player, currentTps);
            }
        }

        // Mili start - fix: drop states of offline players (slow leak, onPlayerQuit is never wired)
        playerStates.keySet().retainAll(onlineUuids);
        // Mili end - fix: stale state cleanup
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
            // Mili start - fix: setViewDistance must run on the player's region thread under Folia,
            // schedule it through the entity scheduler instead of calling it from a foreign region thread
            final int newViewDistance = targetVD;
            player.getScheduler().run(
                SCHEDULER_PLUGIN,
                t -> player.setViewDistance(newViewDistance),
                null
            );
            // Mili end - fix: region-thread-safe setViewDistance
        }
    }

    private static int countNearbyPlayers(Player player, int viewDistance) {
        int count = 0;
        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player)) continue;
            if (player.getLocation().distanceSquared(other.getLocation()) <
                    (viewDistance * 16L) * (viewDistance * 16L)) {
                count++;
            }
        }
        return count;
    }

    private static double getCurrentTps() {
        // Mili start - fix: the old implementation read a scoreboard objective "mili_tps" that is
        // never created anywhere, so it always returned 20.0 and the low-TPS protection never fired.
        // Use the Paper global TPS API (5s average) instead.
        try {
            double[] tps = Bukkit.getTPS();
            if (tps != null && tps.length > 0 && tps[0] > 0.0) {
                return tps[0];
            }
        } catch (Throwable ignored) {}
        // Mili end - fix: dead scoreboard TPS source

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
