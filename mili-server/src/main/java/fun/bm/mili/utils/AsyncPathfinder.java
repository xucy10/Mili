package fun.bm.mili.utils;

import fun.bm.mili.config.modules.optimizations.AsyncPathfindingConfig;
import fun.bm.mili.scheduler.SchedulerLog;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Snapshot-based asynchronous pathfinding (fix.md §7).
 *
 * <h2>What was wrong</h2>
 * The previous implementation handed a live {@code Mob} to a plain thread pool and
 * called {@code mob.getNavigation().createPath(target, 0)} there:
 *
 * <pre>
 *     Executors.newFixedThreadPool(2)
 *     CompletableFuture.supplyAsync(() -&gt; mob.getNavigation().createPath(target, 0), executor)
 * </pre>
 *
 * That call chain reads the mob, its navigation state, the level, block states,
 * chunk data and world data — all of it owned by the region the mob lives in. The
 * worker thread owns none of it. Under Folia this is a cross-region read of live
 * mutable state, and the mob could be ticked or removed while the path was being
 * computed. Nothing in the old code detected either problem.
 *
 * <h2>The model now</h2>
 *
 * <pre>
 *   region thread   PathSnapshot.capture(level, center, radius, halfHeight)
 *                     |  block the mob's own region owns, read on its own thread
 *                     v
 *   region thread   PathRequest  (immutable: coordinates + snapshot)
 *                     |
 *                     v
 *   worker thread   compute()  — pure A* over the snapshot, touches no Minecraft state
 *                     |
 *                     v
 *   region thread   apply the waypoints (caller's responsibility)
 * </pre>
 *
 * <p>Everything crossing the thread boundary is immutable and copied: coordinates
 * are {@code int}s and the block data is a {@code byte[]}. The worker has no
 * reference to the level, the chunk, the mob or the region, so there is nothing it
 * <em>could</em> touch.
 *
 * <p><b>Consequence worth stating:</b> the computed path is only valid for the
 * snapshot it was computed from. If the world changed while the path was being
 * computed, the result is stale — which is the honest situation, and the caller
 * must re-validate before applying. Silently returning a path computed against
 * live state is what this design removes.
 */
public final class AsyncPathfinder {

    /** How long {@link #shutdown()} waits for in-flight computes before forcing them down. */
    private static final long SHUTDOWN_GRACE_MILLIS = 500L;

    private AsyncPathfinder() {}

    // ---------- State ----------

    private static volatile boolean enabled = false;
    private static volatile ExecutorService executor;

    private static final AtomicInteger queuedTasks = new AtomicInteger(0);
    private static final AtomicLong completedTasks = new AtomicLong(0);
    private static final AtomicLong rejectedTasks = new AtomicLong(0);
    private static final AtomicLong failedTasks = new AtomicLong(0);
    private static final AtomicLong totalComputeTime = new AtomicLong(0);
    private static final AtomicLong totalCaptureTime = new AtomicLong(0);

    private static final AtomicBoolean warnedMissingShutdown = new AtomicBoolean(false);

    // ---------- Lifecycle ----------

    /**
     * Enable or disable asynchronous pathfinding, creating or tearing down the
     * worker pool accordingly.
     * <p>
     * Synchronized because it is driven from configuration reload, which can run
     * concurrently with {@link #shutdown()}.
     */
    public static synchronized void setEnabled(boolean v) {
        enabled = v;
        if (v && executor == null) {
            executor = Executors.newFixedThreadPool(
                    Math.max(1, AsyncPathfindingConfig.threadCount),
                    r -> {
                        Thread t = new Thread(r, "Mili-AsyncPathfinder");
                        t.setDaemon(true);
                        // Compute must never outrank a region thread.
                        t.setPriority(Thread.NORM_PRIORITY - 1);
                        return t;
                    });
        } else if (!v && executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Stop the pool.
     * <p>
     * The previous version had no shutdown at all: it relied entirely on
     * {@code AsyncPathfindingConfig.onUnloaded} firing. A configuration reload that
     * did not unload, or a shutdown that bypassed the config layer, leaked the pool
     * and its threads. {@code MiliOptimizations.shutdown()} now calls this
     * explicitly.
     *
     * @return {@code true} if the pool stopped within the grace period
     */
    public static synchronized boolean shutdown() {
        enabled = false;
        ExecutorService pool = executor;
        executor = null;
        if (pool == null) return true;

        pool.shutdown();
        try {
            if (pool.awaitTermination(SHUTDOWN_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                return true;
            }
            pool.shutdownNow();
            return pool.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Warn once if something asks for a path while the subsystem is off. */
    private static void warnDisabled() {
        if (warnedMissingShutdown.compareAndSet(false, true)) {
            SchedulerLog.debug("AsyncPathfinder called while disabled; returning no path");
        }
    }

    // ---------- Capture ----------

    /**
     * Immutable block-collision snapshot of a cubic region.
     * <p>
     * Copied, not referenced: the worker reading this array has no way back to the
     * level it came from.
     */
    public static final class PathSnapshot {

        private final int minX;
        private final int minY;
        private final int minZ;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final byte[] solid;

        private PathSnapshot(int minX, int minY, int minZ,
                             int sizeX, int sizeY, int sizeZ, byte[] solid) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.solid = solid;
        }

        public int sizeX() {
            return sizeX;
        }

        public int sizeY() {
            return sizeY;
        }

        public int sizeZ() {
            return sizeZ;
        }

        public int volume() {
            return sizeX * sizeY * sizeZ;
        }

        public BlockPos min() {
            return new BlockPos(minX, minY, minZ);
        }

        // Accessors for the origin corner. {@code indexOf} flattens world coordinates
        // into the snapshot array and needs the origin, so these are not just convenience.
        public int minX() {
            return minX;
        }

        public int minY() {
            return minY;
        }

        public int minZ() {
            return minZ;
        }

        /** Whether a world block position is inside this snapshot. */
        public boolean contains(int x, int y, int z) {
            return x >= minX && x < minX + sizeX
                    && y >= minY && y < minY + sizeY
                    && z >= minZ && z < minZ + sizeZ;
        }

        /** Whether the block at a world position blocks movement. */
        public boolean isSolid(int x, int y, int z) {
            if (!contains(x, y, z)) return true; // treat outside as blocked: fail closed
            int ix = x - minX;
            int iy = y - minY;
            int iz = z - minZ;
            return solid[(iy * sizeZ + iz) * sizeX + ix] != 0;
        }

        /** Whether the block at a world position is free and passable. */
        public boolean isPassable(int x, int y, int z) {
            return !isSolid(x, y, z);
        }

        /** Approximate heap footprint, for diagnostics. */
        public int byteSize() {
            return solid.length + 32;
        }

        @Override
        public String toString() {
            return "PathSnapshot[" + sizeX + "x" + sizeY + "x" + sizeZ
                    + " @ " + minX + "," + minY + "," + minZ
                    + " (" + solid.length + " bytes)]";
        }
    }

    /**
     * Capture a snapshot of the blocks around {@code center}.
     * <p>
     * <b>Must be called on the thread that owns {@code center}</b> — this is the
     * one step that reads live world state, and it reads it under region
     * ownership. Everything after this point is pure.
     *
     * @param level      the level to read from
     * @param center     centre of the capture volume
     * @param radius     horizontal radius in blocks
     * @param halfHeight vertical half-extent in blocks
     * @return the snapshot, or {@code null} if the level is null or the volume is
     *         too large to be worth capturing
     */
    public static PathSnapshot capture(Level level, BlockPos center, int radius, int halfHeight) {
        if (level == null || center == null) return null;

        int r = Math.max(1, radius);
        int h = Math.max(1, halfHeight);
        int sizeX = r * 2 + 1;
        int sizeY = h * 2 + 1;
        int sizeZ = r * 2 + 1;

        long volume = (long) sizeX * sizeY * sizeZ;
        if (volume > 1 << 20) {
            // A megabyte of block data per path request is a configuration mistake,
            // not a path. Refuse rather than silently allocating it.
            SchedulerLog.warn("AsyncPathfinder capture volume too large (%d blocks); refusing", volume);
            return null;
        }

        long begin = System.nanoTime();
        int minX = center.getX() - r;
        int minY = center.getY() - h;
        int minZ = center.getZ() - r;

        byte[] solid = new byte[(int) volume];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int i = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++, i++) {
                    cursor.set(minX + x, minY + y, minZ + z);
                    // Reads live world state — legal here because the caller owns
                    // the region containing `center`. This is the fix.md §6
                    // "Capture" step; there is no other place in this class that
                    // touches the world.
                    solid[i] = level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()
                            ? (byte) 0
                            : (byte) 1;
                }
            }
        }

        totalCaptureTime.addAndGet(System.nanoTime() - begin);
        return new PathSnapshot(minX, minY, minZ, sizeX, sizeY, sizeZ, solid);
    }

    // ---------- Request / result ----------

    /** An immutable path request. Everything the worker needs, and nothing else. */
    public record PathRequest(
            int startX, int startY, int startZ,
            int targetX, int targetY, int targetZ,
            PathSnapshot snapshot,
            int maxExpansions
    ) {
        public PathRequest {
            if (snapshot == null) throw new IllegalArgumentException("snapshot is required");
            if (maxExpansions <= 0) maxExpansions = 64 * 1024;
        }

        public BlockPos start() {
            return new BlockPos(startX, startY, startZ);
        }

        public BlockPos target() {
            return new BlockPos(targetX, targetY, targetZ);
        }
    }

    /** Result of a pure path computation. */
    public record PathResult(List<BlockPos> waypoints, boolean reached, int expansions, long computeNanos) {
        public boolean isEmpty() {
            return waypoints.isEmpty();
        }
    }

    // ---------- Entry point ----------

    /**
     * Compute a path asynchronously from a snapshot.
     * <p>
     * The returned future completes with the pure result. <b>The caller must apply
     * it back on the region's owning thread</b>, and must re-validate before doing
     * so: the snapshot may be stale by the time the future completes, and a stale
     * path is not the same as a valid one.
     *
     * @return the future, or a future already completed with {@code null} when the
     *         subsystem is disabled or the queue is full
     */
    public static CompletableFuture<PathResult> findPathAsync(Level level, BlockPos start,
                                                              BlockPos target, int radius) {
        if (!enabled || executor == null) {
            warnDisabled();
            return CompletableFuture.completedFuture(null);
        }
        if (level == null || start == null || target == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (queuedTasks.get() >= AsyncPathfindingConfig.maxQueueSize) {
            rejectedTasks.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        // Capture happens here, on the caller's thread — which owns the region.
        PathSnapshot snapshot = capture(level, start, radius, Math.max(4, radius / 2));
        if (snapshot == null) {
            rejectedTasks.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        if (!snapshot.contains(target.getX(), target.getY(), target.getZ())) {
            // Out-of-snapshot targets are not computable. The old code path would
            // have loaded chunks to find out; this design declines instead.
            rejectedTasks.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        PathRequest request = new PathRequest(
                start.getX(), start.getY(), start.getZ(),
                target.getX(), target.getY(), target.getZ(),
                snapshot,
                AsyncPathfindingConfig.maxPathLength * 64);

        queuedTasks.incrementAndGet();
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    long begin = System.nanoTime();
                    PathResult result = compute(request);
                    totalComputeTime.addAndGet(System.nanoTime() - begin);
                    completedTasks.incrementAndGet();
                    return result;
                } catch (Throwable t) {
                    failedTasks.incrementAndGet();
                    SchedulerLog.warn("AsyncPathfinder compute failed: %s", t);
                    return null;
                } finally {
                    queuedTasks.decrementAndGet();
                }
            }, executor);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            queuedTasks.decrementAndGet();
            rejectedTasks.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }
    }

    // ---------- Pure computation ----------

    /** Neighbour offsets, allocated once. A 6-neighbour walk: no diagonals, no corner cutting. */
    private static final int[][] NEIGHBOURS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 0, 1}, {0, 0, -1},
            {0, 1, 0}, {0, -1, 0},
    };

    /**
     * A* over a {@link PathSnapshot}.
     * <p>
     * Pure: reads only the snapshot and the scalar fields of the request. It cannot
     * touch Minecraft state even by accident, because it holds no reference to any
     * of it.
     */
    static PathResult compute(PathRequest request) {
        long begin = System.nanoTime();
        PathSnapshot snapshot = request.snapshot();

        int startIndex = indexOf(snapshot, request.startX(), request.startY(), request.startZ());
        int targetIndex = indexOf(snapshot, request.targetX(), request.targetY(), request.targetZ());
        if (startIndex < 0 || targetIndex < 0) {
            return new PathResult(List.of(), false, 0, System.nanoTime() - begin);
        }
        if (startIndex == targetIndex) {
            return new PathResult(List.of(request.start()), true, 0, System.nanoTime() - begin);
        }

        int volume = snapshot.volume();
        int[] cameFrom = new int[volume];
        int[] gScore = new int[volume];
        Arrays.fill(cameFrom, -1);
        Arrays.fill(gScore, Integer.MAX_VALUE);
        gScore[startIndex] = 0;

        PriorityQueue<long[]> open = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        open.add(new long[]{heuristic(snapshot, startIndex, targetIndex), startIndex});

        int expansions = 0;
        boolean reached = false;

        while (!open.isEmpty() && expansions < request.maxExpansions()) {
            long[] current = open.poll();
            int currentIndex = (int) current[1];

            if (currentIndex == targetIndex) {
                reached = true;
                break;
            }

            expansions++;
            int cx = xOf(snapshot, currentIndex);
            int cy = yOf(snapshot, currentIndex);
            int cz = zOf(snapshot, currentIndex);
            int currentG = gScore[currentIndex];

            for (int[] offset : NEIGHBOURS) {
                int nx = cx + offset[0];
                int ny = cy + offset[1];
                int nz = cz + offset[2];
                if (!snapshot.contains(nx, ny, nz)) continue;
                if (snapshot.isSolid(nx, ny, nz)) continue;

                int neighbourIndex = indexOf(snapshot, nx, ny, nz);
                if (neighbourIndex < 0) continue;

                int tentative = currentG + 1;
                if (tentative >= gScore[neighbourIndex]) continue;

                cameFrom[neighbourIndex] = currentIndex;
                gScore[neighbourIndex] = tentative;
                long f = tentative + heuristic(snapshot, neighbourIndex, targetIndex);
                open.add(new long[]{f, neighbourIndex});
            }
        }

        if (!reached) {
            // No path found within the expansion budget. Returning "no path" is
            // correct and cheap; the caller falls back. The old implementation
            // would have walked the live world with no budget at all.
            return new PathResult(List.of(), false, expansions, System.nanoTime() - begin);
        }

        // Reconstruct, then trim to a walkable list in forward order.
        List<BlockPos> reversed = new ArrayList<>();
        int cursor = targetIndex;
        while (cursor != -1) {
            reversed.add(new BlockPos(
                    snapshot.minX() + xOf(snapshot, cursor),
                    snapshot.minY() + yOf(snapshot, cursor),
                    snapshot.minZ() + zOf(snapshot, cursor)));
            if (cursor == startIndex) break;
            cursor = cameFrom[cursor];
        }
        List<BlockPos> waypoints = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            waypoints.add(reversed.get(i));
        }

        return new PathResult(waypoints, true, expansions, System.nanoTime() - begin);
    }

    private static int indexOf(PathSnapshot s, int x, int y, int z) {
        if (!s.contains(x, y, z)) return -1;
        int ix = x - s.minX();
        int iy = y - s.minY();
        int iz = z - s.minZ();
        return (iy * s.sizeZ() + iz) * s.sizeX() + ix;
    }

    private static int xOf(PathSnapshot s, int index) {
        return index % s.sizeX();
    }

    private static int yOf(PathSnapshot s, int index) {
        return index / (s.sizeX() * s.sizeZ());
    }

    private static int zOf(PathSnapshot s, int index) {
        return (index / s.sizeX()) % s.sizeZ();
    }

    /** Manhattan distance: consistent for a 6-neighbour grid, so A* stays optimal. */
    private static int heuristic(PathSnapshot s, int fromIndex, int toIndex) {
        return Math.abs(xOf(s, fromIndex) - xOf(s, toIndex))
                + Math.abs(yOf(s, fromIndex) - yOf(s, toIndex))
                + Math.abs(zOf(s, fromIndex) - zOf(s, toIndex));
    }

    // ---------- Diagnostics ----------

    public static long completedTasks() {
        return completedTasks.get();
    }

    public static long rejectedTasks() {
        return rejectedTasks.get();
    }

    public static long failedTasks() {
        return failedTasks.get();
    }

    public static int queuedTasks() {
        return queuedTasks.get();
    }

    public static long totalComputeTimeMillis() {
        return totalComputeTime.get() / 1_000_000L;
    }

    public static long totalCaptureTimeMillis() {
        return totalCaptureTime.get() / 1_000_000L;
    }

    /**
     * Snapshot for {@code /mili perf}.
     * <p>
     * Kept even though the individual counters above are public: the perf command
     * prints a labelled table and every other subsystem exposes the same shape, so a
     * missing {@code getStats()} here would mean the async pathfinder silently
     * disappeared from the report.
     */
    public static Map<String, Object> getStats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled);
        out.put("queued_tasks", queuedTasks.get());
        out.put("completed_tasks", completedTasks.get());
        out.put("rejected_tasks", rejectedTasks.get());
        out.put("failed_tasks", failedTasks.get());
        out.put("total_compute_ms", totalComputeTime.get() / 1_000_000L);
        out.put("total_capture_ms", totalCaptureTime.get() / 1_000_000L);
        return out;
    }
}
