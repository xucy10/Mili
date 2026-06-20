package fun.bm.mili.kaiiju;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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

    private static final Deque<PathfindingResult> resultQueue = new ConcurrentLinkedDeque<>();
    private static volatile boolean enabled = false;

    private AsyncPathfindingExecutor() {}

    public static void setEnabled(boolean e) { enabled = e; }
    public static boolean isEnabled() { return enabled; }

    public static void submitPathfind(Mob mob, BlockPos goal, int range, Consumer<Path> onComplete) {
        if (!enabled || mob == null || mob.isRemoved()) return;

        ServerLevel level = (ServerLevel) mob.level();
        BlockPos start = mob.blockPosition();

        PATHFINDING_POOL.submit(() -> {
            try {
                // Use Minecraft's Mob navigation to compute path on async thread.
                // findPath returns null if unreachable.
                Path path = mob.getNavigation().createPath(goal, range);
                if (path != null && !path.canReach()) {
                    path = null;
                }
                resultQueue.add(new PathfindingResult(mob, path, onComplete));
            } catch (Exception e) {
                LOGGER.debug("Async pathfinding failed for {}: {}", mob, e.getMessage());
            }
        });
    }

    public static void drainResults() {
        if (!enabled || resultQueue.isEmpty()) return;

        PathfindingResult result;
        int drained = 0;
        while ((result = resultQueue.poll()) != null) {
            try {
                Mob mob = result.mob;
                if (mob == null || mob.isRemoved()) continue;

                mob.getBukkitEntity().getScheduler().runDelayed(
                    MinecraftInternalPlugin.INSTANCE,
                    (io.papermc.paper.threadedregions.scheduler.ScheduledTask st) -> {
                        if (!mob.isRemoved() && result.onComplete != null) {
                            result.onComplete.accept(result.path);
                        }
                    },
                    null,
                    1L
                );
                drained++;
            } catch (Exception e) {
                LOGGER.debug("Failed to apply async pathfinding result: {}", e.getMessage());
            }
        }
        if (drained > 0 && LOGGER.isDebugEnabled()) {
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

    private record PathfindingResult(Mob mob, Path path, Consumer<Path> onComplete) {}
}
