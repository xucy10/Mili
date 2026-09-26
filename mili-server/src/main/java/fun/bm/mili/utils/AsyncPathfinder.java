package fun.bm.mili.utils;

import fun.bm.mili.config.modules.optimizations.AsyncPathfindingConfig;
import fun.bm.mili.scheduler.WorkerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Snapshot-based asynchronous pathfinding.
 * <p>
 * fix.md §7: the previous implementation did
 * {@code supplyAsync(() -> mob.getNavigation().createPath(target, 0))}, which hands the
 * live {@code Mob}, {@code Navigation}, {@code Level} and block state to a worker thread —
 * a direct Region Ownership violation.
 * <p>
 * The pipeline is now explicit:
 * <pre>
 *     Region Thread  -> PathSnapshot.capture()   (legal: captured on the owning thread)
 *                    -> PathRequest (immutable)
 *                    -> Async Worker -> pure A* over the snapshot only
 *                    -> PathResult
 *     Region Thread  -> applyPath()              (mutates Minecraft state)
 * </pre>
 * <b>Worker rule:</b> the worker only reads {@link PathSnapshot}. It must never touch
 * {@code Mob}, {@code Level}, {@code Chunk}, {@code Entity} or {@code Region}.
 */
public class AsyncPathfinder {

    private static volatile boolean enabled = false;

    private static final AtomicInteger queuedTasks = new AtomicInteger();
    private static final AtomicInteger completedTasks = new AtomicInteger();
    private static final AtomicInteger failedTasks = new AtomicInteger();
    private static final AtomicInteger rejectedOffThread = new AtomicInteger();
    private static final AtomicLong totalComputeTime = new AtomicLong();

    /** Horizontal/vertical radius of the captured volume, in blocks. */
    private static final int DEFAULT_SNAPSHOT_RADIUS = 12;
    /** Upper bound on A* expansions; keeps the worker bounded. */
    private static final int DEFAULT_MAX_EXPANSIONS = 4096;

    // ---------- Immutable data model ----------

    /**
     * Immutable block-collision snapshot captured on the region thread.
     * Holds no reference to {@code Level}, {@code Chunk} or any Minecraft object.
     */
    public static final class PathSnapshot {
        private final BlockPos origin;
        private final int size;
        /** 1 = solid, 0 = free. Indexed {@code (dy * size + dz) * size + dx}. */
        private final byte[] solid;

        private PathSnapshot(BlockPos origin, int size, byte[] solid) {
            this.origin = origin;
            this.size = size;
            this.solid = solid;
        }

        /**
         * Capture the collision volume around {@code center}.
         * <b>Must be called on the region thread that owns {@code level}.</b>
         */
        public static PathSnapshot capture(Level level, BlockPos center) {
            return capture(level, center, DEFAULT_SNAPSHOT_RADIUS);
        }

        public static PathSnapshot capture(Level level, BlockPos center, int radius) {
            if (level == null || center == null) return null;
            int size = radius * 2 + 1;
            byte[] solid = new byte[size * size * size];
            CollisionContext context = CollisionContext.empty();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

            int baseX = center.getX() - radius;
            int baseY = center.getY() - radius;
            int baseZ = center.getZ() - radius;

            for (int dy = 0; dy < size; dy++) {
                for (int dz = 0; dz < size; dz++) {
                    for (int dx = 0; dx < size; dx++) {
                        cursor.set(baseX + dx, baseY + dy, baseZ + dz);
                        boolean blocking = !level.getBlockState(cursor)
                                .getCollisionShape(level, cursor, context)
                                .isEmpty();
                        solid[(dy * size + dz) * size + dx] = (byte) (blocking ? 1 : 0);
                    }
                }
            }
            return new PathSnapshot(new BlockPos(baseX, baseY, baseZ), size, solid);
        }

        public BlockPos origin() { return origin; }
        public int size() { return size; }

        boolean isSolid(int lx, int ly, int lz) {
            if (lx < 0 || ly < 0 || lz < 0 || lx >= size || ly >= size || lz >= size) return true;
            return solid[(ly * size + lz) * size + lx] != 0;
        }

        /** Two blocks of clearance is required for most mobs. */
        boolean isWalkable(int lx, int ly, int lz) {
            return !isSolid(lx, ly, lz) && !isSolid(lx, ly + 1, lz);
        }

        boolean isSupported(int lx, int ly, int lz) {
            return isSolid(lx, ly - 1, lz);
        }
    }

    /** Immutable request handed to the worker. */
    public record PathRequest(
            BlockPos start,
            BlockPos target,
            PathSnapshot snapshot,
            int maxExpansions
    ) {
        public PathRequest {
            if (snapshot == null) throw new IllegalArgumentException("snapshot required");
        }
    }

    /** Immutable result produced by the worker. */
    public record PathResult(List<BlockPos> waypoints, boolean reached, long computeNanos) {

        public PathResult {
            waypoints = waypoints == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(waypoints));
        }

        public boolean isEmpty() { return waypoints.isEmpty(); }

        /**
         * Materialise a vanilla {@link Path} from the waypoints.
         * <b>Must be called on the region thread.</b>
         */
        public Path toVanillaPath(BlockPos target) {
            List<Node> nodes = new ArrayList<>(waypoints.size());
            for (BlockPos pos : waypoints) {
                nodes.add(new Node(pos.getX(), pos.getY(), pos.getZ()));
            }
            return new Path(nodes, target == null
                    ? (nodes.isEmpty() ? BlockPos.ZERO : waypoints.get(waypoints.size() - 1))
                    : target, reached);
        }
    }

    // ---------- Capturing (region thread) ----------

    /**
     * Capture a request for {@code mob} on the region thread and dispatch the pure
     * computation to a worker.
     * <p>
     * Returns {@code null} when the caller is not the owning thread: capturing off-thread
     * would read {@code Level} state we do not own.
     *
     * @return future of the pure computation, or {@code null} if not schedulable
     */
    public static CompletableFuture<PathResult> findPathAsync(Mob mob, BlockPos target) {
        return findPathAsync(mob, target, DEFAULT_SNAPSHOT_RADIUS);
    }

    public static CompletableFuture<PathResult> findPathAsync(Mob mob, BlockPos target, int radius) {
        if (!enabled || mob == null || target == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Capturing reads live level state -> only legal on the owning thread.
        if (!isOwnedByCurrentThread(mob)) {
            rejectedOffThread.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        if (queuedTasks.get() >= AsyncPathfindingConfig.maxQueueSize) {
            return CompletableFuture.completedFuture(null);
        }

        Level level = mob.level();
        BlockPos start = mob.blockPosition();

        // Distance guard: outside the snapshot volume there is nothing to compute on.
        if (start.distManhattan(target) > radius * 2) {
            return CompletableFuture.completedFuture(null);
        }

        PathSnapshot snapshot = PathSnapshot.capture(level, start, radius);
        if (snapshot == null) {
            return CompletableFuture.completedFuture(null);
        }

        PathRequest request = new PathRequest(start, target.immutable(), snapshot, DEFAULT_MAX_EXPANSIONS);
        queuedTasks.incrementAndGet();

        CompletableFuture<PathResult> future = WorkerRuntime.submitPure(() -> compute(request));
        return future.whenComplete((result, error) -> {
            queuedTasks.decrementAndGet();
            if (error != null) {
                failedTasks.incrementAndGet();
            } else {
                completedTasks.incrementAndGet();
                if (result != null) totalComputeTime.addAndGet(result.computeNanos() / 1_000_000L);
            }
        });
    }

    /**
     * Apply a computed result to a mob. <b>Must be called on the region thread.</b>
     *
     * @return {@code true} if the mob's navigation accepted the path
     */
    public static boolean applyPath(Mob mob, PathResult result, double speed) {
        if (mob == null || result == null || result.isEmpty()) return false;
        if (!isOwnedByCurrentThread(mob)) return false;
        Path path = result.toVanillaPath(mob.blockPosition());
        return mob.getNavigation().moveTo(path, speed);
    }

    private static boolean isOwnedByCurrentThread(Mob mob) {
        try {
            return ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(mob);
        } catch (Throwable t) {
            // Ownership cannot be proven -> refuse rather than risk an illegal access.
            return false;
        }
    }

    // ---------- Pure computation (worker thread) ----------

    /**
     * A* over the snapshot. Pure: reads only {@link PathSnapshot}, touches no Minecraft state.
     */
    static PathResult compute(PathRequest request) {
        long begin = System.nanoTime();
        PathSnapshot snap = request.snapshot();
        int size = snap.size();
        BlockPos origin = snap.origin();

        int sx = clamp(request.start().getX() - origin.getX(), 0, size - 1);
        int sy = clamp(request.start().getY() - origin.getY(), 0, size - 1);
        int sz = clamp(request.start().getZ() - origin.getZ(), 0, size - 1);
        int tx = clamp(request.target().getX() - origin.getX(), 0, size - 1);
        int ty = clamp(request.target().getY() - origin.getY(), 0, size - 1);
        int tz = clamp(request.target().getZ() - origin.getZ(), 0, size - 1);

        if (!snap.isWalkable(sx, sy, sz) || !snap.isWalkable(tx, ty, tz)) {
            return new PathResult(List.of(), false, System.nanoTime() - begin);
        }

        int maxExpansions = request.maxExpansions() <= 0 ? DEFAULT_MAX_EXPANSIONS : request.maxExpansions();

        // Flat arrays keep the hot loop allocation-light.
        int capacity = size * size * size;
        double[] gScore = new double[capacity];
        java.util.Arrays.fill(gScore, Double.MAX_VALUE);
        int[] cameFrom = new int[capacity];
        java.util.Arrays.fill(cameFrom, -1);
        boolean[] closed = new boolean[capacity];

        int start = index(sx, sy, sz, size);
        int goal = index(tx, ty, tz, size);
        gScore[start] = 0.0;

        // Encode f-score alongside coordinates; a small object per push is acceptable
        // because the worker is already off the tick thread.
        PriorityQueue<NodeRef> frontier = new PriorityQueue<>(64);
        frontier.add(new NodeRef(start, sx, sy, sz, heuristic(sx, sy, sz, tx, ty, tz)));

        int expansions = 0;
        boolean reached = false;

        while (!frontier.isEmpty() && expansions < maxExpansions) {
            NodeRef current = frontier.poll();
            int ci = current.index;
            if (closed[ci]) continue;
            closed[ci] = true;
            expansions++;

            if (ci == goal) {
                reached = true;
                break;
            }

            for (int d = 0; d < 6; d++) {
                int nx = current.x + DX[d];
                int ny = current.y + DY[d];
                int nz = current.z + DZ[d];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= size || ny >= size || nz >= size) continue;
                if (!snap.isWalkable(nx, ny, nz)) continue;

                int ni = index(nx, ny, nz, size);
                if (closed[ni]) continue;

                // Vertical moves and unsupported walking are allowed but penalised, so a
                // normal walking route is preferred over a climbing/flying one.
                double step = 1.0;
                if (DY[d] != 0) step += 1.5;
                if (!snap.isSupported(nx, ny, nz)) step += 2.0;

                double tentative = gScore[ci] + step;
                if (tentative < gScore[ni]) {
                    gScore[ni] = tentative;
                    cameFrom[ni] = ci;
                    frontier.add(new NodeRef(ni, nx, ny, nz, tentative + heuristic(nx, ny, nz, tx, ty, tz)));
                }
            }
        }

        if (!reached) {
            return new PathResult(List.of(), false, System.nanoTime() - begin);
        }

        List<BlockPos> waypoints = new ArrayList<>();
        int cursor = goal;
        while (cursor != -1) {
            int x = cursor % size;
            int z = (cursor / size) % size;
            int y = cursor / (size * size);
            waypoints.add(new BlockPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z));
            if (cursor == start) break;
            cursor = cameFrom[cursor];
        }
        Collections.reverse(waypoints);
        return new PathResult(waypoints, true, System.nanoTime() - begin);
    }

    private record NodeRef(int index, int x, int y, int z, double f) implements Comparable<NodeRef> {
        @Override
        public int compareTo(NodeRef o) {
            return Double.compare(this.f, o.f);
        }
    }

    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1, 0, 0};
    private static final int[] DY = {0, 0, 0, 0, 1, -1};

    private static int index(int x, int y, int z, int size) {
        return (y * size + z) * size + x;
    }

    private static double heuristic(int x, int y, int z, int tx, int ty, int tz) {
        return Math.abs(x - tx) + Math.abs(y - ty) + Math.abs(z - tz);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---------- Config / stats ----------

    public static synchronized void setEnabled(boolean v) {
        enabled = v;
        if (v) {
            WorkerRuntime.init(Math.max(1, AsyncPathfindingConfig.threadCount));
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("Enabled", enabled);
        stats.put("Queued", queuedTasks.get());
        stats.put("Completed", completedTasks.get());
        stats.put("Failed", failedTasks.get());
        stats.put("RejectedOffThread", rejectedOffThread.get());
        int total = completedTasks.get() + failedTasks.get();
        stats.put("Avg Compute (ms)", total > 0
                ? String.format("%.1f", (double) totalComputeTime.get() / total) : "0");
        return stats;
    }
}
