package fun.bm.mili.kaiiju;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;
import org.slf4j.Logger;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

public final class AsyncPathfindingExecutor {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Deque<PathfindingResult> resultQueue = new ConcurrentLinkedDeque<>();
    private static volatile boolean enabled = false;

    private AsyncPathfindingExecutor() {}

    public static void setEnabled(boolean e) { enabled = e; }
    public static boolean isEnabled() { return enabled; }

    public static void submitPathfind(Mob mob, BlockPos goal, int range, Consumer<Path> onComplete) {
        if (!enabled || mob == null || mob.isRemoved()) return;

        Path path = null;
        try {
            path = mob.getNavigation().createPath(goal, range);
            if (path != null && !path.canReach()) {
                path = null;
            }
        } catch (Exception e) {
            LOGGER.debug("Pathfinding failed for {}: {}", mob, e.getMessage());
        }

        final Path resultPath = path;
        resultQueue.add(new PathfindingResult(mob, resultPath, onComplete));
    }

    public static void drainResults() {
        if (!enabled || resultQueue.isEmpty()) return;

        PathfindingResult result;
        int drained = 0;
        while ((result = resultQueue.poll()) != null) {
            try {
                final PathfindingResult captured = result;
                Mob mob = captured.mob;
                if (mob == null || mob.isRemoved()) continue;

                mob.getBukkitEntity().getScheduler().runDelayed(
                    MinecraftInternalPlugin.INSTANCE,
                    (io.papermc.paper.threadedregions.scheduler.ScheduledTask st) -> {
                        if (!mob.isRemoved() && captured.onComplete != null) {
                            captured.onComplete.accept(captured.path);
                        }
                    },
                    null,
                    1L
                );
                drained++;
            } catch (Exception e) {
                LOGGER.debug("Failed to apply pathfinding result: {}", e.getMessage());
            }
        }
        if (drained > 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("Drained {} pathfinding results", drained);
        }
    }

    public static void shutdown() {
        resultQueue.clear();
    }

    private record PathfindingResult(Mob mob, Path path, Consumer<Path> onComplete) {}
}
