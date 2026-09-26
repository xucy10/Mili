package fun.bm.mili.utils;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * TPS tracker based on LaggRemover's implementation.
 * Provides accurate TPS calculation and formatting.
 * <p>
 * Rust-style optimization: uses AtomicLong running sum to avoid O(n) scan
 * on every TPS calculation, and ring buffer with modulo-free indexing.
 */
public final class TPSTracker {
    // Mili start - fix: TICK_HISTORY_SIZE must be power of 2 for mask-based ring buffer indexing (600 & 599 = 592, not 0)
    private static final int TICK_HISTORY_SIZE = 1024;
    // Mili end
    private static final int TICK_HISTORY_MASK = TICK_HISTORY_SIZE - 1;
    private static final AtomicLongArray ticks = new AtomicLongArray(TICK_HISTORY_SIZE);
    private static final AtomicLong runningSum = new AtomicLong(0);
    private static final AtomicInteger tickCount = new AtomicInteger(0);
    private static volatile double currentTPS = 20.0;

    // Mili start - fix: Folia migration. Paper's legacy scheduler is disabled at runtime under
    // Folia (Bukkit.getScheduler() throws UnsupportedOperationException, and BukkitRunnable is
    // backed by it). The global region ticks at 20 TPS, which is exactly the clock this tracker
    // measures, so the sampler is driven from the global region scheduler instead.
    private static volatile ScheduledTask tickTask;
    // Mili end

    private TPSTracker() {}

    public static void init(Plugin plugin) {
        if (plugin == null) return;
        // Mili start - fix: make init idempotent; the previous code stacked a new timer on
        // every repeated call, each one advancing tickCount and writing the ring buffer.
        ScheduledTask previous = tickTask;
        tickTask = null;
        if (previous != null) {
            previous.cancel();
        }
        // Mili end
        tickTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            int count = tickCount.getAndIncrement();
            int idx = count & TICK_HISTORY_MASK;
            long now = System.currentTimeMillis();
            long old = ticks.getAndSet(idx, now);
            if (old > 0) {
                runningSum.addAndGet(now - old);
            }
            currentTPS = calculateTPS(100);
        }, 1L, 1L);
    }

    // Mili start - fix: expose shutdown so the repeating global task can be released on stop
    public static void shutdown() {
        ScheduledTask task = tickTask;
        tickTask = null;
        if (task != null) {
            task.cancel();
        }
    }
    // Mili end

    public static double getTPS() {
        return currentTPS;
    }

    public static double getTPS(int ticks) {
        return calculateTPS(ticks);
    }

    private static double calculateTPS(int requestedTicks) {
        int count = tickCount.get();
        // Mili start - fix: use <= to correctly handle boundary when count equals requestedTicks
        if (count <= requestedTicks) {
            return 20.0;
        }
        // Mili end
        int target = ((count - 1) - requestedTicks) & TICK_HISTORY_MASK;
        long elapsed = System.currentTimeMillis() - ticks.get(target);
        if (elapsed <= 0) {
            return 20.0;
        }
        return requestedTicks / (elapsed / 1000.0);
    }

    public static String formatTPS() {
        double tps = getTPS();
        String color;
        if (tps > 18.0) {
            color = "\u00a7a";
        } else if (tps > 15.0) {
            color = "\u00a7e";
        } else if (tps > 10.0) {
            color = "\u00a7c";
        } else {
            color = "\u00a74";
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