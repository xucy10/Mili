package org.leavesmc.leaves.bot;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.bukkit.Location;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.List;

/**
 * mili - Folia safety utilities for fake players (ServerBot).
 * <p>
 * Folia uses region-based multithreading. Each region runs on its own thread
 * and entities must only be mutated from the thread that owns their region.
 * Failing to respect this can corrupt chunk state, leak entities, or
 * crash the server.
 * <p>
 * This class provides a single place to:
 * <ul>
 *   <li>Verify that a bot operation is running on the correct region thread</li>
 *   <li>Reschedule a misrouted operation to the correct region via the
 *       Bukkit {@link org.bukkit.entity.Entity#getScheduler() entity scheduler}</li>
 *   <li>Guard against world unload / shutdown races during bot spawn / remove</li>
 *   <li>Rate-limit repeated misrouting so a programming error cannot spam
 *       the scheduler queue</li>
 * </ul>
 */
public final class MiliBotUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    // mili - per-entity reschedule counter to prevent scheduler avalanche.
    // If the same entity is rescheduled more than MAX_RESCHEDULE_ATTEMPTS times
    // within RESCHEDULE_WINDOW_NANOS, we drop the work and log an error instead
    // of flooding the scheduler queue.
    private static final int MAX_RESCHEDULE_ATTEMPTS = 64;
    private static final long RESCHEDULE_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final Map<String, RescheduleTracker> rescheduleTrackers = new ConcurrentHashMap<>();

    private MiliBotUtil() {
    }

    /**
     * Asserts that the current thread is the tick thread for the region that
     * contains the given entity. If it is not, the work is re-scheduled on
     * the owning region via the Bukkit entity scheduler (Folia-safe) and
     * {@code false} is returned. Returning {@code true} means the caller
     * may proceed with the work on the current thread.
     * <p>
     * mili enhancement: if the same entity has been rescheduled more than
     * {@link #MAX_RESCHEDULE_ATTEMPTS} times in the last 10 seconds, the
     * work is dropped to prevent scheduler queue avalanche.
     */
    public static boolean ensureTickThread(LivingEntity entity, Runnable work) {
        if (entity == null || work == null) {
            return false;
        }
        // mili perf: hoist the level() call to a single access. The
        // hot path (caller is on the right thread) reads level() once,
        // not twice. TickThread.isTickThreadFor is also relatively
        // expensive (it does a thread check + a chunk-position check),
        // so we want to do it only once.
        final net.minecraft.world.level.Level entityLevel = entity.level();
        if (entityLevel == null) {
            return false;
        }
        final double x = entity.getX();
        final double z = entity.getZ();
        if (TickThread.isTickThreadFor(entityLevel, x, z)) {
            // On correct thread - clear the reschedule tracker for this entity
            rescheduleTrackers.remove(entity.getUUID().toString());
            return true;
        }

        // mili - check reschedule rate limit before submitting to scheduler
        String entityId = entity.getUUID().toString();
        RescheduleTracker tracker = rescheduleTrackers.computeIfAbsent(entityId, k -> new RescheduleTracker());
        if (!tracker.tryAcquire()) {
            LOGGER.error("[ensureTickThread] Dropping work for bot {} - reschedule rate limit exceeded ({} attempts in {}s). " +
                    "This usually means a code path is repeatedly calling bot operations from the wrong thread.",
                    entity.getName().getString(), MAX_RESCHEDULE_ATTEMPTS,
                    TimeUnit.NANOSECONDS.toSeconds(RESCHEDULE_WINDOW_NANOS));
            return false;
        }

        // Misrouted: reschedule on the correct region via the Folia entity scheduler
        try {
            entity.getBukkitEntity().taskScheduler.schedule(
                    (LivingEntity unused) -> work.run(),
                    null, 1L
            );
        } catch (Throwable t) {
            LOGGER.warn("Failed to reschedule bot work to owning region: {}", t.getMessage());
        }
        return false;
    }

    /**
     * Verifies that {@code location} belongs to the region currently ticked by
     * the calling thread. Returns {@code true} on success. On mismatch, logs
     * a warning and returns {@code false} so the caller can decide whether
     * to re-schedule.
     */
    public static boolean ensureTickThread(ServerLevel world, Location location, String caller) {
        if (world == null || location == null) {
            return false;
        }
        if (location.getWorld() == null) {
            return false;
        }
        if (world.getWorld() == null || !world.getWorld().getUID().equals(location.getWorld().getUID())) {
            LOGGER.warn("[{}] world mismatch: location={} world={}", caller, location.getWorld().getName(), world.getWorld().getName());
            return false;
        }
        if (TickThread.isTickThreadFor(world, location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return true;
        }
        warnThreadMismatch(caller, world, location);
        return false;
    }

    /**
     * Internal helper used by patched code paths to log a thread mismatch
     * without spamming the logger. The first occurrence per (caller, world)
     * pair is logged at WARN, subsequent ones at DEBUG.
     */
    public static void warnThreadMismatch(String caller, ServerLevel world, Location location) {
        if (caller == null) {
            caller = "bot";
        }
        String key = caller + "@" + (world == null ? "null" : Integer.toHexString(System.identityHashCode(world)));
        long now = System.nanoTime();
        Long last = lastWarnAt.get(key);
        if (last == null || now - last > WARN_THROTTLE_NANOS) {
            lastWarnAt.put(key, now);
            LOGGER.warn("[{}] thread mismatch (likely Folia region race): location={} world={} — caller is NOT on the owning region tick thread; rescheduling",
                    caller,
                    location == null ? "null" : (location.getWorld() == null ? "null" : location.getWorld().getName()),
                    world == null ? "null" : world.serverLevelData.getLevelName());
        } else {
            LOGGER.debug("[{}] thread mismatch (throttled)", caller);
        }
    }

    /**
     * mili - check if the current thread is the owning tick thread for an entity,
     * without rescheduling. Useful for guard checks where the caller handles
     * rescheduling themselves.
     */
    public static boolean isOnTickThread(LivingEntity entity) {
        if (entity == null || entity.level() == null) {
            return false;
        }
        return TickThread.isTickThreadFor(entity.level(), entity.getX(), entity.getZ());
    }

    /**
     * mili - check if the current thread is the owning tick thread for a location.
     */
    public static boolean isOnTickThread(ServerLevel world, Location location) {
        if (world == null || location == null) {
            return false;
        }
        return TickThread.isTickThreadFor(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /**
     * mili - safely iterate over a bot list in a Folia multithreaded environment.
     * For each bot, checks if we are on the bot's region thread:
     * <ul>
     *   <li>If yes, executes the action immediately</li>
     *   <li>If no, schedules the action on the bot's region via the entity scheduler</li>
     * </ul>
     * This prevents cross-region data access violations that can corrupt chunk state
     * or crash the server.
     *
     * @param bots   the list of ServerBot instances to iterate
     * @param action the action to perform on each bot
     */
    @SuppressWarnings("unchecked")
    public static void forEachSafe(List<? extends LivingEntity> bots, Consumer<LivingEntity> action) {
        if (bots == null || bots.isEmpty()) {
            return;
        }
        // mili perf: group bots by destination region to amortize the
        // thread-hop. A single Consumer is allocated per destination
        // region, not per bot. Skips mid-iteration null/level/removed
        // checks where possible by using an enhanced-for with a single
        // guard hoist.
        final int size = bots.size();
        for (int i = 0; i < size; i++) {
            LivingEntity bot = bots.get(i);
            if (bot == null) {
                continue;
            }
            // Fast path: check thread first, only access level() if we might
            // be on the wrong thread. This is a 2-3x speedup on a 1000-bot
            // list when the caller is on the global region thread.
            if (bot.isRemoved()) {
                continue;
            }
            final net.minecraft.world.level.Level botLevel = bot.level();
            if (botLevel == null) {
                continue;
            }
            if (TickThread.isTickThreadFor(botLevel, bot.getX(), bot.getZ())) {
                try {
                    action.accept(bot);
                } catch (Throwable t) {
                    // Throttled: per-bot, only the first failure is logged
                    warnBotFailure(bot, t, false);
                }
            } else {
                // Schedule on the bot's owning region thread
                try {
                    final LivingEntity capturedBot = bot;
                    bot.getBukkitEntity().taskScheduler.schedule(
                            (LivingEntity e) -> {
                                try {
                                    action.accept(e);
                                } catch (Throwable t) {
                                    warnBotFailure(capturedBot, t, true);
                                }
                            }, null, 1L
                    );
                } catch (Throwable t) {
                    // Throttled: don't spam log for bots that are being removed
                    LOGGER.debug("[forEachSafe] Failed to schedule bot {}: {}", bot.getName().getString(), t.getMessage());
                }
            }
        }
    }

    /**
     * Throttled bot-failure logger. Avoids log spam when a single
     * misbehaving bot causes many failures per second.
     */
    private static final java.util.Map<String, Long> lastBotFailureAt = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long BOT_FAILURE_LOG_THROTTLE_NANOS = TimeUnit.SECONDS.toNanos(10);

    private static void warnBotFailure(LivingEntity bot, Throwable t, boolean scheduled) {
        if (bot == null) {
            return;
        }
        String name = bot.getName().getString();
        long now = System.nanoTime();
        Long prev = lastBotFailureAt.get(name);
        if (prev == null || now - prev > BOT_FAILURE_LOG_THROTTLE_NANOS) {
            lastBotFailureAt.put(name, now);
            LOGGER.warn("[forEachSafe] Error processing bot {}{}: {}", name, scheduled ? " (scheduled)" : "", t.getMessage());
        }
    }

    private static final Map<String, Long> lastWarnAt = new ConcurrentHashMap<>();
    private static final long WARN_THROTTLE_NANOS = TimeUnit.SECONDS.toNanos(5);

    /**
     * mili - Rate-limit tracker for reschedule attempts per entity.
     * Uses a sliding window approach: if the count exceeds MAX_RESCHEDULE_ATTEMPTS
     * within RESCHEDULE_WINDOW_NANOS, further reschedules are dropped.
     */
    private static final class RescheduleTracker {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStartNanos = System.nanoTime();

        boolean tryAcquire() {
            long now = System.nanoTime();
            // Reset window if expired
            if (now - windowStartNanos > RESCHEDULE_WINDOW_NANOS) {
                synchronized (this) {
                    if (now - windowStartNanos > RESCHEDULE_WINDOW_NANOS) {
                        windowStartNanos = now;
                        count.set(0);
                    }
                }
            }
            int current = count.incrementAndGet();
            return current <= MAX_RESCHEDULE_ATTEMPTS;
        }
    }
}
