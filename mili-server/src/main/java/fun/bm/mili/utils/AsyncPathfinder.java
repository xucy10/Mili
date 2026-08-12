package fun.bm.mili.utils;

import fun.bm.mili.config.modules.optimizations.AsyncPathfindingConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AsyncPathfinder {
    private static volatile boolean enabled = false;
    // Mili start - fix: declare executor as volatile for visibility across threads
    private static volatile ExecutorService executor;
    // Mili end
    private static final AtomicInteger queuedTasks = new AtomicInteger();
    private static final AtomicInteger completedTasks = new AtomicInteger();
    private static final AtomicInteger failedTasks = new AtomicInteger();
    private static final AtomicLong totalComputeTime = new AtomicLong();

    // Mili start - fix: synchronize setEnabled to prevent check-then-act race condition
    public static synchronized void setEnabled(boolean v) {
        enabled = v;
        if (v && executor == null) {
            executor = Executors.newFixedThreadPool(AsyncPathfindingConfig.threadCount, r -> {
                Thread t = new Thread(r, "Mili-AsyncPathfinder");
                t.setDaemon(true);
                return t;
            });
        } else if (!v && executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
    // Mili end

    public static boolean isEnabled() { return enabled; }

    public static CompletableFuture<Path> findPathAsync(Mob mob, BlockPos target) {
        if (!enabled || executor == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (queuedTasks.get() >= AsyncPathfindingConfig.maxQueueSize) {
            return CompletableFuture.completedFuture(null);
        }

        queuedTasks.incrementAndGet();
        long startTime = System.nanoTime();

        return CompletableFuture.supplyAsync(() -> {
            try {
                long start = System.nanoTime();
                Path path = mob.getNavigation().createPath(target, 0);
                long elapsed = System.nanoTime() - start;
                totalComputeTime.addAndGet(elapsed / 1_000_000);
                completedTasks.incrementAndGet();
                return path;
            // Mili start - fix: catch Throwable instead of Exception to handle Errors (OOM, StackOverflow)
            // that would otherwise propagate and silently kill the async pathfinding thread
            } catch (Throwable e) {
                failedTasks.incrementAndGet();
                return null;
            } finally {
                queuedTasks.decrementAndGet();
            }
        }, executor);
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("Enabled", enabled);
        stats.put("Queued", queuedTasks.get());
        stats.put("Completed", completedTasks.get());
        stats.put("Failed", failedTasks.get());
        int total = completedTasks.get() + failedTasks.get();
        stats.put("Avg Compute (ms)", total > 0 ?
                String.format("%.1f", (double) totalComputeTime.get() / total) : "0");
        return stats;
    }
}
