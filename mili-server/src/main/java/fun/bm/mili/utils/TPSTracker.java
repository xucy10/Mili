package fun.bm.mili.utils;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * TPS tracker based on LaggRemover's implementation.
 * Provides accurate TPS calculation and formatting.
 */
public final class TPSTracker {
    private static final int TICK_HISTORY_SIZE = 600;
    private static final long[] TICKS = new long[TICK_HISTORY_SIZE];
    private static int tickCount = 0;
    private static volatile double currentTPS = 20.0;

    private TPSTracker() {}

    public static void init(org.bukkit.plugin.Plugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                TICKS[tickCount % TICKS.length] = System.currentTimeMillis();
                tickCount++;
                currentTPS = calculateTPS(100);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public static double getTPS() {
        return currentTPS;
    }

    public static double getTPS(int ticks) {
        return calculateTPS(ticks);
    }

    private static double calculateTPS(int ticks) {
        if (tickCount < ticks) {
            return 20.0;
        }
        int target = ((tickCount - 1) - ticks) % TICKS.length;
        long elapsed = System.currentTimeMillis() - TICKS[target];
        if (elapsed <= 0) {
            return 20.0;
        }
        return ticks / (elapsed / 1000.0);
    }

    public static String formatTPS() {
        double tps = getTPS();
        String color;
        if (tps > 18.0) {
            color = "§a";
        } else if (tps > 15.0) {
            color = "§e";
        } else if (tps > 10.0) {
            color = "§c";
        } else {
            color = "§4";
        }
        return color + String.format("%.2f", tps);
    }

    public static boolean isLagging() {
        return getTPS() < 18.0;
    }

    public static boolean isSeverelyLagging() {
        return getTPS() < 15.0;
    }
}