package fun.bm.mili.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RedstoneStats {
    private static volatile boolean enabled = false;

    private static final AtomicLong pistonExtend = new AtomicLong();
    private static final AtomicLong pistonRetract = new AtomicLong();
    private static final AtomicLong pistonStickyExtend = new AtomicLong();
    private static final AtomicLong pistonStickyRetract = new AtomicLong();

    private static final AtomicLong blockUpdate = new AtomicLong();
    private static final AtomicLong neighborUpdate = new AtomicLong();

    private static final AtomicLong budTrigger = new AtomicLong();

    private static final AtomicLong redstoneWireChange = new AtomicLong();

    private static final ConcurrentHashMap<String, AtomicLong> pistonByDirection = new ConcurrentHashMap<>();

    private static final AtomicLong totalTicks = new AtomicLong();
    private static long lastTickNanos = System.nanoTime();

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }

    public static void onPistonExtend(boolean sticky) {
        if (!enabled) return;
        if (sticky) pistonStickyExtend.incrementAndGet();
        else pistonExtend.incrementAndGet();
    }

    public static void onPistonRetract(boolean sticky) {
        if (!enabled) return;
        if (sticky) pistonStickyRetract.incrementAndGet();
        else pistonRetract.incrementAndGet();
    }

    public static void onPistonByDirection(String direction) {
        if (!enabled) return;
        pistonByDirection.computeIfAbsent(direction, k -> new AtomicLong()).incrementAndGet();
    }

    public static void onBlockUpdate() {
        if (!enabled) return;
        blockUpdate.incrementAndGet();
    }

    public static void onNeighborUpdate() {
        if (!enabled) return;
        neighborUpdate.incrementAndGet();
    }

    public static void onBudTrigger() {
        if (!enabled) return;
        budTrigger.incrementAndGet();
    }

    public static void onRedstoneWireChange() {
        if (!enabled) return;
        redstoneWireChange.incrementAndGet();
    }

    public static void onTick() {
        if (!enabled) return;
        totalTicks.incrementAndGet();
        long now = System.nanoTime();
        lastTickNanos = now;
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("Total Ticks", totalTicks.get());
        stats.put("Piston Extend", pistonExtend.get());
        stats.put("Piston Retract", pistonRetract.get());
        stats.put("Sticky Piston Extend", pistonStickyExtend.get());
        stats.put("Sticky Piston Retract", pistonStickyRetract.get());
        stats.put("Block Updates", blockUpdate.get());
        stats.put("Neighbor Updates", neighborUpdate.get());
        stats.put("BUD Triggers", budTrigger.get());
        stats.put("Redstone Wire Changes", redstoneWireChange.get());

        if (!pistonByDirection.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            pistonByDirection.forEach((dir, count) -> {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(dir).append(": ").append(count.get());
            });
            stats.put("Pistons by Direction", sb.toString());
        }

        long ticks = totalTicks.get();
        if (ticks > 0) {
            stats.put("Avg Updates/Tick",
                    String.format("%.2f", (double)(blockUpdate.get() + neighborUpdate.get()) / ticks));
            stats.put("Avg Pistons/Tick",
                    String.format("%.2f", (double)(pistonExtend.get() + pistonRetract.get()
                            + pistonStickyExtend.get() + pistonStickyRetract.get()) / ticks));
        }

        return stats;
    }

    public static void reset() {
        pistonExtend.set(0);
        pistonRetract.set(0);
        pistonStickyExtend.set(0);
        pistonStickyRetract.set(0);
        blockUpdate.set(0);
        neighborUpdate.set(0);
        budTrigger.set(0);
        redstoneWireChange.set(0);
        totalTicks.set(0);
        pistonByDirection.clear();
    }
}
