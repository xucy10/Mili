package fun.bm.mili.kaiiju;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async Pathfinding executor (ported from Kaiiju concept).
 *
 * Offloads pathfinding computations to a dedicated thread pool, then
 * applies results on the owning region thread via the entity scheduler.
 *
 * Threading model:
 * - Pathfinding runs on async worker threads (no Folia region restrictions)
 * - Results are submitted back via entity.getScheduler().run()
 * - Each entity has at most one pending async pathfinding task
 */
public final class AsyncPathfindingExecutor {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final AtomicInteger TASK_ID_GEN = new AtomicInteger(0);
    private static final ExecutorService PATHFINDING_POOL = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
        task -> {
            Thread t = new Thread(task, "mili-async-pathfinder-" + TASK_ID_GEN.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    );

    // Result queue drained on global region tick
    private static final Deque<PathfindingResult> resultQueue = new ConcurrentLinkedDeque<>();

    private static volatile boolean enabled = false;

    private AsyncPathfindingExecutor() {}

    public static void setEnabled(boolean e) { enabled = e; }
    public static boolean isEnabled() { return enabled; }

    /**
     * Submit an async pathfinding task.
     * @param mob the mob requesting a path
     * @param goal the target position
     * @param range the max pathfinding range
     * @param onComplete callback to apply the path on the mob's region thread
     */
    public static void submitPathfind(Mob mob, BlockPos goal, int range, PathConsumer onComplete) {
        if (!enabled || mob == null || mob.isRemoved()) return;

        ServerLevel level = (ServerLevel) mob.level();
        BlockPos start = mob.blockPosition();
        PathFinder finder = mob.getNavigation().getPathFinder();

        PATHFINDING_POOL.submit(() -> {
            try {
                long startNanos = System.nanoTime();
                Path path = finder.findPath(level, mob, goal, range, 0, mob.getMaxFallDistance());
                long elapsed = System.nanoTime() - startNanos;

                if (path != null && !path.canReach()) {
                    path = null;
                }

                resultQueue.add(new PathfindingResult(mob, path, onComplete, elapsed));
            } catch (Exception e) {
                LOGGER.debug("Async pathfinding failed for {}: {}", mob, e.getMessage());
            }
        });
    }

    /** Drain results (call from global region tick) */
    public static void drainResults() {
        if (!enabled || resultQueue.isEmpty()) return;

        PathfindingResult result;
        int drained = 0;
        while ((result = resultQueue.poll()) != null) {
            try {
                Mob mob = result.mob;
                if (mob == null || mob.isRemoved()) continue;

                // Apply path on the mob's owning region thread
                mob.getBukkitEntity().getScheduler().run(
                    (org.bukkit.entity.Mob e) -> {
                        if (!mob.isRemoved()) {
                            result.onComplete.accept(result.path);
                        }
                    },
                    null, 1L
                );
                drained++;
            } catch (Exception e) {
                LOGGER.debug("Failed to apply async pathfinding result: {}", e.getMessage());
            }
        }
        if (drained > 0) {
            LOGGER.debug("Drained {} async pathfinding results", drained);
        }
    }

    public static void shutdown() {
        PATHFINDING_POOL.shutdown();
        try {
            if (!PATHFINDING_POOL.awaitTermination(5, TimeUnit.SECONDS)) {
                PATHFINDING_POOL.shutdownNow();
            }
        } catch (InterruptedException e) {
            PATHFINDING_POOL.shutdownNow();
            Thread.currentThread().interrupt();
        }
        resultQueue.clear();
    }

    @FunctionalInterface
    public interface PathConsumer {
        void accept(Path path);
    }

    private record PathfindingResult(Mob mob, Path path, PathConsumer onComplete, long elapsedNanos) {}
}
