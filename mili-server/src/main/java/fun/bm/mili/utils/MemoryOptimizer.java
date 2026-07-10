package fun.bm.mili.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class MemoryOptimizer {

    private MemoryOptimizer() {}

    private static volatile boolean running = false;
    private static ScheduledExecutorService scheduler;
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    private static final AtomicLong lastGCTime = new AtomicLong(0);
    private static final AtomicInteger gcCount = new AtomicInteger(0);
    private static final AtomicLong totalFreedBytes = new AtomicLong(0);

    private static long maxMemoryBytes = 0;
    private static double gcThreshold = 0.85;
    private static double aggressiveGcThreshold = 0.95;
    private static long minGcIntervalMs = 30_000;
    private static boolean autoMemoryTuning = true;

    public static void init() {
        if (running) return;

        maxMemoryBytes = Runtime.getRuntime().maxMemory();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Mili-MemoryOptimizer");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                MemoryOptimizer::monitor,
                5_000,
                5_000,
                TimeUnit.MILLISECONDS
        );

        running = true;
        org.mojang.logging.LogUtils.getLogger().info("[Mili] MemoryOptimizer initialized");
    }

    public static void shutdown() {
        if (!running) return;
        running = false;

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
    }

    private static void monitor() {
        try {
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            long used = heapUsage.getUsed();
            long committed = heapUsage.getCommitted();
            long max = heapUsage.getMax();

            double usageRatio = (double) used / max;

            if (usageRatio > aggressiveGcThreshold) {
                performAggressiveGC(used);
            } else if (usageRatio > gcThreshold) {
                performNormalGC(used);
            }

            if (autoMemoryTuning && usageRatio > 0.7) {
                suggestCleanup();
            }

            logMemoryStatus(used, committed, max, usageRatio);

        } catch (Exception e) {
            org.mojang.logging.LogUtils.getLogger().error("[Mili] Memory monitor error", e);
        }
    }

    private static void performNormalGC(long currentUsed) {
        long now = System.currentTimeMillis();
        long lastGc = lastGCTime.get();

        if (now - lastGc < minGcIntervalMs) return;

        if (lastGCTime.compareAndSet(lastGc, now)) {
            long before = getUsedMemory();

            System.gc();

            try { Thread.sleep(100); } catch (InterruptedException ignored) {}

            long after = getUsedMemory();
            long freed = before - after;
            if (freed > 0) {
                totalFreedBytes.addAndGet(freed);
            }

            gcCount.incrementAndGet();

            org.mojang.logging.LogUtils.getLogger().debug(
                    "[Mili] Normal GC: freed {} MB", freed / (1024 * 1024)
            );
        }
    }

    private static void performAggressiveGC(long currentUsed) {
        long before = getUsedMemory();

        for (int i = 0; i < 3; i++) {
            System.gc();
            try { Thread.sleep(50); } catch (InterruptedException ignored) }
        }

        long after = getUsedMemory();
        long freed = before - after;
        if (freed > 0) {
            totalFreedBytes.addAndGet(freed);
        }

        gcCount.incrementAndGet();
        lastGCTime.set(System.currentTimeMillis());

        org.mojang.logging.LogUtils.getLogger().warn(
                "[Mili] Aggressive GC triggered: freed {} MB", freed / (1024 * 1024)
        );
    }

    private static void suggestCleanup() {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;

        double ratio = (double) usedMemory / maxMemory;

        if (ratio > 0.8) {
            org.mojang.logging.LogUtils.getLogger().warn(
                    "[Mili] High memory usage: {}% ({}/{} MB)",
                    (int)(ratio * 100),
                    usedMemory / (1024 * 1024),
                    maxMemory / (1024 * 1024)
            );

            System.runFinalization();
        }
    }

    private static void logMemoryStatus(long used, long committed, long max, double ratio) {
        if (gcCount.get() % 12 == 0) {
            org.mojang.logging.LogUtils.getLogger().info(
                    "[Mili] Memory: {}% used ({}/{} MB), GC count: {}, Total freed: {} MB",
                    (int)(ratio * 100),
                    used / (1024 * 1024),
                    max / (1024 * 1024),
                    gcCount.get(),
                    totalFreedBytes.get() / (1024 * 1024)
            );
        }
    }

    public static long getUsedMemory() {
        return memoryBean.getHeapMemoryUsage().getUsed();
    }

    public static long getMaxMemory() {
        return memoryBean.getHeapMemoryUsage().getMax();
    }

    public static double getMemoryUsageRatio() {
        MemoryUsage usage = memoryBean.getHeapMemoryUsage();
        return (double) usage.getUsed() / usage.getMax();
    }

    public static int getGCCount() {
        return gcCount.get();
    }

    public static long getTotalFreedBytes() {
        return totalFreedBytes.get();
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("used_memory_mb", getUsedMemory() / (1024 * 1024));
        stats.put("max_memory_mb", getMaxMemory() / (1024 * 1024));
        stats.put("usage_percent", (int)(getMemoryUsageRatio() * 100));
        stats.put("gc_count", gcCount.get());
        stats.put("total_freed_mb", totalFreedBytes.get() / (1024 * 1024));
        stats.put("running", running);

        return stats;
    }

    public static void requestGC() {
        long now = System.currentTimeMillis();
        if (now - lastGCTime.get() >= minGcIntervalMs / 2) {
            performNormalGC(getUsedMemory());
        }
    }

    public static void setGcThreshold(double threshold) {
        gcThreshold = Math.max(0.5, Math.min(0.99, threshold));
    }

    public static void setAggressiveGcThreshold(double threshold) {
        aggressiveGcThreshold = Math.max(gcThreshold + 0.05, Math.min(0.99, threshold));
    }

    public static void setMinGcIntervalMs(long intervalMs) {
        minGcIntervalMs = Math.max(5_000, intervalMs);
    }
}