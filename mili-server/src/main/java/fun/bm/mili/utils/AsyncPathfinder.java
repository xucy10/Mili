package fun.bm.mili.utils;

import fun.bm.mili.config.modules.optimizations.AsyncPathfindingConfig;
import fun.bm.mili.scheduler.MiliScheduler;
import fun.bm.mili.scheduler.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AsyncPathfinder {
    private static volatile boolean enabled = false;
    private static ExecutorService executor;
    private static final AtomicInteger queuedTasks = new AtomicInteger();
    private static final AtomicInteger completedTasks = new AtomicInteger();
    private static final AtomicInteger failedTasks = new AtomicInteger();
    private static final AtomicLong totalComputeTime = new AtomicLong();

    public static void setEnabled(boolean v) {
        enabled = v;
        // Mili start - 寻路是纯 CPU 计算，归入 CPU 层；池由治理层托管。
        // 关键改动：关闭开关时**不再** shutdownNow 池。原实现在 toggle 时会销毁整个池，
        // 若该池被多个调用方共享，等于顺手掐断了别人；现在只停止提交任务，
        // 线程随治理层统一回收，顺带避免重复 toggle 反复创建/销毁线程。
        if (v && executor == null) {
            executor = MiliScheduler.namedPool("pathfinder", Tier.CPU,
                    Math.max(1, AsyncPathfindingConfig.threadCount));
        }
        // Mili end
    }

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
            } catch (Exception e) {
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
