package fun.bm.mili.utils;

import com.mojang.logging.LogUtils;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public final class MemoryOptimizer {

    private MemoryOptimizer() {}

    // Mili start - fix: use AtomicBoolean for atomic init guard to prevent duplicate scheduler creation
    private static final AtomicBoolean running = new AtomicBoolean(false);
    // Mili end
    private static ScheduledExecutorService scheduler;
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    private static final LongAdder gcCount = new LongAdder();
    private static final LongAdder totalFreedBytes = new LongAdder();
    private static final AtomicInteger logCounter = new AtomicInteger(0);

    private static long maxMemoryBytes = 0;
    // Mili start - fix: declare thresholds as volatile for visibility across threads
    private static volatile double gcThreshold = 0.85;
    private static volatile double aggressiveGcThreshold = 0.95;
    // Mili end
    private static boolean autoMemoryTuning = true;

    public static void init() {
        // Mili start - fix: use compareAndSet for atomic init guard to prevent duplicate scheduler
        if (!running.compareAndSet(false, true)) {
            return;
        }
        // Mili end

        maxMemoryBytes = Runtime.getRuntime().maxMemory();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Mili-MemoryOptimizer");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                MemoryOptimizer::monitorMemory,
                5_000,
                5_000,
                TimeUnit.MILLISECONDS
        );

        LogUtils.getLogger().info("[Mili] MemoryOptimizer initialized");
    }

    public static void shutdown() {
        // Mili start - fix: use compareAndSet for atomic shutdown guard
        if (!running.compareAndSet(true, false)) {
            return;
        }
        // Mili end

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void monitorMemory() {
        try {
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            long used = heapUsage.getUsed();
            long committed = heapUsage.getCommitted();
            // Mili start - fix: getMax() may return -1, fallback to Runtime.maxMemory()
            long max = heapUsage.getMax();
            if (max <= 0) {
                max = Runtime.getRuntime().maxMemory();
            }
            if (max <= 0) return;
            // Mili end

            double usageRatio = (double) used / max;

            if (usageRatio > aggressiveGcThreshold) {
                performMemoryCleanup(used, true);
            } else if (usageRatio > gcThreshold) {
                performMemoryCleanup(used, false);
            }

            if (autoMemoryTuning && usageRatio > 0.7) {
                logHighMemoryWarning(used, max);
            }

            logMemoryStatus(used, committed, max, usageRatio);

        // Mili start - fix: catch Throwable instead of Exception to handle Errors (OOM, StackOverflow)
        // that would otherwise propagate and silently kill the memory monitor scheduler thread
        } catch (Throwable e) {
            LogUtils.getLogger().error("[Mili] Memory monitor error", e);
        }
        // Mili end
    }

    private static void performMemoryCleanup(long currentUsed, boolean aggressive) {
        long before = getUsedMemory();

        try {
            TimeUnit.MILLISECONDS.sleep(aggressive ? 150 : 100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long after = getUsedMemory();
        long freed = before - after;
        if (freed > 0) {
            totalFreedBytes.add(freed);
        }

        gcCount.increment();

        if (aggressive) {
            LogUtils.getLogger().warn(
                    "[Mili] High memory pressure detected: {} MB freed", freed / (1024 * 1024)
            );
        } else {
            LogUtils.getLogger().debug(
                    "[Mili] Memory pressure detected: {} MB freed", freed / (1024 * 1024)
            );
        }
    }

    private static void logHighMemoryWarning(long used, long max) {
        double ratio = (double) used / max;
        LogUtils.getLogger().warn(
                "[Mili] High memory usage: {}% ({}/{} MB)",
                (int)(ratio * 100),
                used / (1024 * 1024),
                max / (1024 * 1024)
        );
    }

    private static void logMemoryStatus(long used, long committed, long max, double ratio) {
        if (logCounter.incrementAndGet() % 12 == 0) {
            LogUtils.getLogger().info(
                    "[Mili] Memory: {}% used ({}/{} MB), cleanup count: {}, Total freed: {} MB",
                    (int)(ratio * 100),
                    used / (1024 * 1024),
                    max / (1024 * 1024),
                    gcCount.sum(),
                    totalFreedBytes.sum() / (1024 * 1024)
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

    public static long getCleanupCount() {
        return gcCount.sum();
    }

    public static long getTotalFreedBytes() {
        return totalFreedBytes.sum();
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("used_memory_mb", getUsedMemory() / (1024 * 1024));
        stats.put("max_memory_mb", getMaxMemory() / (1024 * 1024));
        stats.put("usage_percent", (int)(getMemoryUsageRatio() * 100));
        stats.put("cleanup_count", gcCount.sum());
        stats.put("total_freed_mb", totalFreedBytes.sum() / (1024 * 1024));
        // Mili start - fix: use AtomicBoolean.get() since running is now AtomicBoolean
        stats.put("running", running.get());
        // Mili end

        return stats;
    }

    public static void notifyHighMemory() {
        LogUtils.getLogger().warn("[Mili] External high memory notification");
    }

    public static void setGcThreshold(double threshold) {
        gcThreshold = Math.max(0.5, Math.min(0.99, threshold));
    }

    public static void setAggressiveGcThreshold(double threshold) {
        aggressiveGcThreshold = Math.max(gcThreshold + 0.05, Math.min(0.99, threshold));
    }
}
