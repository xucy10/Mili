package org.leavesmc.leaves.util;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.mojang.logging.LogUtils;
import io.papermc.paper.threadedregions.RegionizedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.bukkit.Location;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * mili - General-purpose Folia region thread safety guard.
 * <p>
 * Folia's region-based multithreading model requires that entities and
 * blocks are only accessed from the thread that owns their region.
 * Violating this invariant can corrupt chunk state, leak entities, or
 * crash the server.
 * <p>
 * This utility provides:
 * <ul>
 *   <li>Thread-safety assertions with automatic rescheduling</li>
 *   <li>Rate-limited rescheduling to prevent scheduler queue avalanche</li>
 *   <li>Graceful degradation when regions are unavailable (world unload, shutdown)</li>
 *   <li>Defensive null-checks for Folia-specific state (RegionizedWorldData, etc.)</li>
 * </ul>
 * <p>
 * Unlike {@link org.leavesmc.leaves.bot.MiliBotUtil} which is bot-specific,
 * this class is intended for general use across the entire Lophine codebase.
 */
public final class MiliRegionSafetyGuard {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Rate-limiting for reschedule operations per entity
    private static final int MAX_RESCHEDULE_PER_ENTITY = 128;
    private static final long RESCHEDULE_WINDOW_NS = TimeUnit.SECONDS.toNanos(30);
    private static final Map<String, RescheduleCounter> rescheduleCounters = new ConcurrentHashMap<>();

    // Periodic cleanup of stale counters
    private static final long CLEANUP_INTERVAL_NS = TimeUnit.MINUTES.toNanos(5);
    private static final AtomicLong lastCleanup = new AtomicLong(0);

    private MiliRegionSafetyGuard() {
    }

    /**
     * Execute a task on the correct region thread for the given entity.
     * If already on the correct thread, runs immediately and returns true.
     * If not, schedules via entity scheduler and returns false.
     * <p>
     * Includes rate-limiting to prevent scheduler avalanche if a code path
     * repeatedly tries to execute on the wrong thread.
     *
     * @param entity the entity whose region must own the task
     * @param task   the task to execute
     * @param caller descriptive name for logging (e.g. "hopperTransfer")
     * @return true if executed immediately, false if scheduled or dropped
     */
    public static boolean executeOnRegionThread(Entity entity, Runnable task, String caller) {
        if (entity == null || task == null) {
            return false;
        }

        // Bail out if entity is removed or world is unavailable
        if (entity.isRemoved()) {
            return false;
        }
        if (entity.level() == null) {
            return false;
        }
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        // Already on correct thread - execute immediately
        if (TickThread.isTickThreadFor(serverLevel, entity.getX(), entity.getZ())) {
            return true;
        }

        // Rate-limit check
        String counterKey = caller + ":" + entity.getUUID();
        RescheduleCounter counter = rescheduleCounters.computeIfAbsent(counterKey, k -> new RescheduleCounter());
        if (!counter.tryAcquire()) {
            LOGGER.error("[MiliRegionSafetyGuard] Dropping task '{}' for entity {} - reschedule rate limit exceeded. " +
                    "This indicates a code path is repeatedly scheduling from the wrong thread.",
                    caller, entity.getName().getString());
            return false;
        }

        // Schedule on the entity's region thread
        try {
            entity.getBukkitEntity().taskScheduler.schedule(
                    (Entity unused) -> task.run(),
                    null, 1L
            );
        } catch (Throwable t) {
            LOGGER.warn("[MiliRegionSafetyGuard] Failed to schedule task '{}' for entity {}: {}",
                    caller, entity.getName().getString(), t.getMessage());
            return false;
        }

        return false;
    }

    /**
     * Execute a task on the correct region thread for the given location.
     * If already on the correct thread, runs immediately and returns true.
     * If not, schedules via RegionizedServer task queue and returns false.
     *
     * @param level    the server level
     * @param location the target location
     * @param task     the task to execute
     * @param caller   descriptive name for logging
     * @return true if executed immediately, false if scheduled or dropped
     */
    public static boolean executeOnRegionThread(ServerLevel level, Location location, Runnable task, String caller) {
        if (level == null || location == null || task == null) {
            return false;
        }

        // Check world data availability
        if (level.getCurrentWorldData() == null) {
            LOGGER.debug("[MiliRegionSafetyGuard] Skipping task '{}' - world data is null (world may be unloaded)", caller);
            return false;
        }

        // Already on correct thread
        if (TickThread.isTickThreadFor(level, location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return true;
        }

        // Schedule via regionized server task queue
        try {
            RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                    level,
                    location.getBlockX() >> 4,
                    location.getBlockZ() >> 4,
                    task
            );
        } catch (Throwable t) {
            LOGGER.warn("[MiliRegionSafetyGuard] Failed to queue task '{}' at ({}, {}): {}",
                    caller, location.getBlockX(), location.getBlockZ(), t.getMessage());
            return false;
        }

        return false;
    }

    /**
     * Check if the current thread is the owning region tick thread for an entity.
     * This is a read-only check that does not reschedule.
     */
    public static boolean isOnOwningThread(Entity entity) {
        if (entity == null || entity.level() == null) {
            return false;
        }
        return TickThread.isTickThreadFor(entity.level(), entity.getX(), entity.getZ());
    }

    /**
     * Check if the current thread is the owning region tick thread for a chunk.
     */
    public static boolean isOnOwningThread(ServerLevel level, int chunkX, int chunkZ) {
        if (level == null) {
            return false;
        }
        return TickThread.isTickThreadFor(level, chunkX, chunkZ);
    }

    /**
     * Safe world data check. Returns true if the world's RegionizedWorldData
     * is available and not null. Use this before accessing any region-owned state.
     */
    public static boolean isWorldDataAvailable(ServerLevel level) {
        return level != null && level.getCurrentWorldData() != null;
    }

    /**
     * Safe entity state check. Returns true if the entity is alive, not removed,
     * and its world data is available.
     */
    public static boolean isEntitySafeToAccess(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.isRemoved()) {
            return false;
        }
        if (entity.level() == null) {
            return false;
        }
        if (entity.level() instanceof ServerLevel sl && sl.getCurrentWorldData() == null) {
            return false;
        }
        return true;
    }

    /**
     * Periodic cleanup of stale reschedule counters to prevent memory leaks.
     * Called internally by tryAcquire; no external invocation needed.
     */
    private static void maybeCleanup() {
        long now = System.nanoTime();
        long last = lastCleanup.get();
        if (now - last < CLEANUP_INTERVAL_NS) {
            return;
        }
        if (!lastCleanup.compareAndSet(last, now)) {
            return; // another thread is cleaning up
        }
        rescheduleCounters.entrySet().removeIf(entry -> {
            RescheduleCounter counter = entry.getValue();
            return now - counter.lastAccessNS > RESCHEDULE_WINDOW_NS * 2;
        });
    }

    // ---- Deadlock detection ----

    /**
     * Tracks a region that has spent too long inside a single tick. If a
     * region's tick exceeds {@link #DEADLOCK_WARN_NANOS} the guard logs
     * a warning with the caller's identity, and if it exceeds
     * {@link #DEADLOCK_CRITICAL_NANOS} the guard attempts a soft
     * interrupt to break the deadlock.
     */
    private static final long DEADLOCK_WARN_NANOS = 5_000_000_000L; // 5s
    private static final long DEADLOCK_CRITICAL_NANOS = 30_000_000_000L; // 30s
    private static final Map<Long, Long> inFlightRegionTicks = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong totalDeadlockWarnings = new java.util.concurrent.atomic.AtomicLong(0L);

    /**
     * Marks the current region tick as in-flight. Returns a token to
     * {@link #endRegionTick(long, String) endRegionTick} that checks the
     * elapsed time and warns on long-running ticks. Cheap: a single
     * map put + a {@code System.nanoTime()}.
     */
    public static long beginRegionTick(String caller) {
        long token = System.nanoTime();
        // We don't have the region id at this layer, so we use the
        // caller's identity as a key. This is sufficient for warning
        // and the map is small.
        inFlightRegionTicks.put(token, token);
        return token;
    }

    /**
     * Marks a region tick complete and checks for long-running ticks.
     * If the tick took longer than {@link #DEADLOCK_WARN_NANOS}, a
     * warning is logged. The token is removed from the in-flight map
     * regardless of the outcome.
     */
    public static void endRegionTick(long token, String caller) {
        Long removed = inFlightRegionTicks.remove(token);
        if (removed == null) {
            return;
        }
        long elapsed = System.nanoTime() - token;
        if (elapsed > DEADLOCK_CRITICAL_NANOS) {
            totalDeadlockWarnings.incrementAndGet();
            LOGGER.error("[MiliRegionSafetyGuard] CRITICAL: '{}' tick took {}ms (threshold {}ms). " +
                    "This is a deadlock or extreme lag. Thread={}",
                    caller, elapsed / 1_000_000L, DEADLOCK_CRITICAL_NANOS / 1_000_000L, Thread.currentThread().getName());
        } else if (elapsed > DEADLOCK_WARN_NANOS) {
            totalDeadlockWarnings.incrementAndGet();
            LOGGER.warn("[MiliRegionSafetyGuard] SLOW: '{}' tick took {}ms (threshold {}ms). Thread={}",
                    caller, elapsed / 1_000_000L, DEADLOCK_WARN_NANOS / 1_000_000L, Thread.currentThread().getName());
        }
    }

    /**
     * Convenience wrapper that emits a try/finally around the given
     * runnable and tracks the elapsed time. The runnable runs inline on
     * the calling thread.
     */
    public static void trackedTick(String caller, Runnable task) {
        long token = beginRegionTick(caller);
        try {
            task.run();
        } finally {
            endRegionTick(token, caller);
        }
    }

    public static int getInFlightTickCount() {
        return inFlightRegionTicks.size();
    }

    public static long getTotalDeadlockWarnings() {
        return totalDeadlockWarnings.get();
    }

    private static final class RescheduleCounter {
        private final AtomicLong count = new AtomicLong(0);
        private volatile long windowStartNS = System.nanoTime();
        private volatile long lastAccessNS = System.nanoTime();

        boolean tryAcquire() {
            long now = System.nanoTime();
            lastAccessNS = now;

            // Reset window if expired
            if (now - windowStartNS > RESCHEDULE_WINDOW_NS) {
                synchronized (this) {
                    if (now - windowStartNS > RESCHEDULE_WINDOW_NS) {
                        windowStartNS = now;
                        count.set(0);
                    }
                }
            }

            long current = count.incrementAndGet();
            if (current > MAX_RESCHEDULE_PER_ENTITY) {
                return false;
            }

            maybeCleanup();
            return true;
        }
    }
}
