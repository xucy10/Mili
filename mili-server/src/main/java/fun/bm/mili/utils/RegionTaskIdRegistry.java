package fun.bm.mili.utils;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Global registry for region task UUIDs.
 * <p>
 * Provides thread-safe UUID allocation, registration, lookup, and cleanup
 * for all tasks dispatched through the region scheduling system
 * (RegionBalancer, SmartRegionManager, CrossRegionHelper).
 * <p>
 * <b>Design guarantees:</b>
 * <ul>
 *   <li><b>No collision:</b> UUIDs are generated via {@link UUID#randomUUID()}
 *       and registered atomically via {@link ConcurrentHashMap#putIfAbsent}.
 *       If a collision occurs (probability ~ 0), a new UUID is regenerated
 *       up to {@link #MAX_REGEN_ATTEMPTS} times.</li>
 *   <li><b>No duplicate registration:</b> {@link #register(UUID, TaskMeta)}
 *       returns {@code false} if the UUID is already registered, preventing
 *       silent data corruption. {@link #registerOrThrow} throws for hard failures.</li>
 *   <li><b>Bounded memory:</b> A scheduled cleaner evicts entries whose
 *       {@link TaskMeta#updatedAt last update} is older than {@link #ENTRY_TTL_MS},
 *       every {@link #CLEANUP_INTERVAL_MS}, preventing leaks from tasks that
 *       completed without calling {@link #unregister}.</li>
 *   <li><b>Shutdown-safe:</b> {@link #shutdown()} stops the cleaner and clears
 *       all state. Subsequent {@link #allocateAndRegister} calls return a
 *       fresh UUID but skip registration (no-op).</li>
 * </ul>
 *
 * <p><b>Capacity is a soft limit, not a hard cap.</b> {@link #MAX_ACTIVE_ENTRIES}
 * is checked against {@code REGISTRY.size()} and then inserted — a classic
 * check-then-act, which several threads can pass simultaneously. Making it strict
 * would require a lock on the hottest registration path to defend against an
 * overshoot bounded by the number of concurrent callers, which is not a trade
 * worth making. The limit therefore exists to bound memory and to surface a
 * pathological producer via {@code total_rejected_full}, not to be an exact
 * ceiling. Anything that needs an exact guarantee must not rely on this.
 */
public final class RegionTaskIdRegistry {

    private RegionTaskIdRegistry() {}

    // -------------------- Constants --------------------

    private static final int MAX_REGEN_ATTEMPTS = 8;
    private static final long ENTRY_TTL_MS = 120_000;       // 2 minutes
    private static final long CLEANUP_INTERVAL_MS = 30_000;  // 30 seconds
    /**
     * Soft limit on active entries. See the class javadoc: this bounds memory,
     * it is not an exact ceiling.
     */
    private static final int MAX_ACTIVE_ENTRIES = 50_000;

    /** Minimum spacing between "registry is full" warnings, so a full registry cannot flood the log. */
    private static final long FULL_WARN_INTERVAL_MS = 10_000;

    // -------------------- State --------------------

    /**
     * Single source of truth for registered tasks.
     * <p>
     * This used to be two maps — {@code REGISTRY} plus a parallel
     * {@code CREATION_TIME} — updated separately. Nothing made the pair atomic, so
     * a reader could see an entry in one and not the other, and the eviction pass
     * only ever walked the second map. The creation timestamp now lives on
     * {@link TaskMeta}, which removes the whole class of inconsistency rather than
     * guarding against it.
     */
    private static final ConcurrentHashMap<UUID, TaskMeta> REGISTRY = new ConcurrentHashMap<>();

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

    private static ScheduledExecutorService cleanupScheduler;

    private static final AtomicLong totalAllocated = new AtomicLong(0);
    private static final AtomicLong totalRegistered = new AtomicLong(0);
    private static final AtomicLong totalUnregistered = new AtomicLong(0);
    private static final AtomicLong totalEvicted = new AtomicLong(0);
    private static final AtomicLong totalCollisions = new AtomicLong(0);
    private static final AtomicLong totalRejectedFull = new AtomicLong(0);
    private static final AtomicLong totalRejectedDuplicate = new AtomicLong(0);
    private static volatile long lastFullWarnAt = 0L;

    // -------------------- Lifecycle --------------------

    /**
     * Initialize the registry and start the cleanup scheduler.
     * Safe to call multiple times; idempotent.
     */
    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        SHUTDOWN.set(false);

        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Mili-TaskIdRegistry-Cleanup");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

        cleanupScheduler.scheduleAtFixedRate(
                RegionTaskIdRegistry::evictStaleEntries,
                CLEANUP_INTERVAL_MS,
                CLEANUP_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        LogUtils.getLogger().info("[Mili] RegionTaskIdRegistry initialized (TTL={}ms, cleanup={}ms)",
                ENTRY_TTL_MS, CLEANUP_INTERVAL_MS);
    }

    /**
     * Shutdown the registry: stop the cleaner and clear all state.
     */
    public static void shutdown() {
        if (!INITIALIZED.compareAndSet(true, false)) return;
        SHUTDOWN.set(true);

        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        int cleared = REGISTRY.size();
        REGISTRY.clear();

        LogUtils.getLogger().info("[Mili] RegionTaskIdRegistry shutdown (cleared {} entries)", cleared);
    }

    // -------------------- Core API --------------------

    /**
     * Allocate a fresh UUID and register it atomically.
     * <p>
     * This method is the primary entry point for task UUID assignment.
     * It guarantees:
     * <ul>
     *   <li>The returned UUID is unique (no collision with any active entry)</li>
     *   <li>The registration is atomic (putIfAbsent)</li>
     *   <li>If the registry is full, returns a UUID without registration
     *       (graceful degradation — the task can still run, just without tracking)</li>
     * </ul>
     *
     * @param taskType   task type label (e.g. "region-tick", "migration", "cross-region-event")
     * @param scheduleRef opaque region schedule reference (nullable)
     * @return the allocated UUID
     */
    public static @NotNull UUID allocateAndRegister(@NotNull String taskType, @Nullable Object scheduleRef) {
        if (SHUTDOWN.get()) {
            // Registry is shutting down — return a UUID without registration.
            return UUID.randomUUID();
        }

        // Soft limit: bound memory and surface a runaway producer. See the class
        // javadoc for why this is deliberately not an exact ceiling.
        if (REGISTRY.size() >= MAX_ACTIVE_ENTRIES) {
            totalRejectedFull.incrementAndGet();
            warnFull(taskType);
            return UUID.randomUUID();
        }

        long now = System.currentTimeMillis();

        // Single loop for the happy path and the collision path.
        //
        // The previous version handled collisions with a separate retry block that
        // broke out of the loop on success and then re-tested
        // `REGISTRY.containsKey(uuid)` — which was, of course, now true — so a
        // successful retry fell into the "give up" branch and returned before
        // recording the entry's creation time. Every recovered collision therefore
        // produced an entry that the eviction pass could never see: a small but
        // permanent leak on a path whose entire purpose is recovery.
        for (int attempt = 0; attempt <= MAX_REGEN_ATTEMPTS; attempt++) {
            UUID uuid = UUID.randomUUID();
            if (attempt > 0) {
                totalCollisions.incrementAndGet();
            }
            TaskMeta meta = new TaskMeta(uuid, taskType, scheduleRef, now);
            if (REGISTRY.putIfAbsent(uuid, meta) == null) {
                totalAllocated.incrementAndGet();
                totalRegistered.incrementAndGet();
                return uuid;
            }
        }

        LogUtils.getLogger().error(
                "[Mili] Failed to register UUID after {} attempts, task will run untracked",
                MAX_REGEN_ATTEMPTS);
        return UUID.randomUUID();
    }

    private static void warnFull(String taskType) {
        long now = System.currentTimeMillis();
        long last = lastFullWarnAt;
        if (now - last < FULL_WARN_INTERVAL_MS) return;
        lastFullWarnAt = now;
        LogUtils.getLogger().warn(
                "[Mili] RegionTaskIdRegistry at its soft limit ({}), skipping registration for '{}'",
                MAX_ACTIVE_ENTRIES, taskType);
    }

    /**
     * Register a specific UUID with metadata.
     * <p>
     * Use this when the caller already has a UUID (e.g. retry, cross-region passthrough)
     * and needs to (re-)register it.
     *
     * @return {@code true} if registered successfully, {@code false} if the UUID
     *         is already active (duplicate — caller should handle this)
     */
    public static boolean register(@NotNull UUID uuid, @NotNull String taskType, @Nullable Object scheduleRef) {
        if (SHUTDOWN.get()) return false;

        if (REGISTRY.size() >= MAX_ACTIVE_ENTRIES) {
            totalRejectedFull.incrementAndGet();
            return false;
        }

        TaskMeta meta = new TaskMeta(uuid, taskType, scheduleRef, System.currentTimeMillis());
        if (REGISTRY.putIfAbsent(uuid, meta) != null) {
            totalRejectedDuplicate.incrementAndGet();
            return false; // already registered — duplicate
        }
        totalRegistered.incrementAndGet();
        return true;
    }

    /**
     * Register a specific UUID, or throw if it's already active.
     * Use this when duplicate registration indicates a bug that should not be silently ignored.
     *
     * @throws IllegalStateException if the UUID is already registered
     */
    public static void registerOrThrow(@NotNull UUID uuid, @NotNull String taskType, @Nullable Object scheduleRef) {
        if (!register(uuid, taskType, scheduleRef)) {
            TaskMeta existing = REGISTRY.get(uuid);
            throw new IllegalStateException(String.format(
                    "Duplicate task UUID registration: %s (existing type='%s', attempted type='%s')",
                    uuid, existing != null ? existing.taskType : "null", taskType));
        }
    }

    /**
     * Unregister a task UUID.
     * Safe to call multiple times; returns false if not found.
     */
    public static boolean unregister(@NotNull UUID uuid) {
        TaskMeta removed = REGISTRY.remove(uuid);
        if (removed != null) {
            totalUnregistered.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Unregister every task belonging to {@code scheduleRef}.
     * <p>
     * This is the region-destroy hook (fix.md §15): when a region goes away, the
     * tasks it owned must go with it. Without this, a region churning in and out
     * leaves its task entries to expire on the TTL, so a busy server holds
     * thousands of entries for regions that no longer exist.
     *
     * @return how many entries were removed
     */
    public static int unregisterByScheduleRef(@Nullable Object scheduleRef) {
        if (scheduleRef == null) return 0;
        int removed = 0;
        for (Map.Entry<UUID, TaskMeta> entry : REGISTRY.entrySet()) {
            if (scheduleRef.equals(entry.getValue().scheduleRef)) {
                if (REGISTRY.remove(entry.getKey(), entry.getValue())) {
                    removed++;
                }
            }
        }
        if (removed > 0) {
            totalUnregistered.addAndGet(removed);
        }
        return removed;
    }

    /**
     * Look up task metadata by UUID.
     *
     * @return the metadata, or {@code null} if not registered
     */
    @Nullable
    public static TaskMeta get(@NotNull UUID uuid) {
        return REGISTRY.get(uuid);
    }

    /**
     * Check if a task UUID is currently registered (active).
     */
    public static boolean isActive(@NotNull UUID uuid) {
        return REGISTRY.containsKey(uuid);
    }

    /**
     * Update the state of a registered task.
     * No-op if the UUID is not registered.
     */
    public static void updateState(@NotNull UUID uuid, @NotNull String state) {
        TaskMeta meta = REGISTRY.get(uuid);
        if (meta != null) {
            meta.state = state;
            meta.updatedAt = System.currentTimeMillis();
        }
    }

    /**
     * Get the schedule reference associated with a task UUID.
     * Useful for region schedulers to look up which region owns a task.
     */
    @Nullable
    public static Object getScheduleRef(@NotNull UUID uuid) {
        TaskMeta meta = REGISTRY.get(uuid);
        return meta != null ? meta.scheduleRef : null;
    }

    /**
     * Find all active task UUIDs for a given schedule reference.
     * Useful for region unload/cleanup to discover pending tasks.
     */
    @NotNull
    public static List<UUID> findByScheduleRef(@Nullable Object scheduleRef) {
        if (scheduleRef == null) return Collections.emptyList();
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, TaskMeta> entry : REGISTRY.entrySet()) {
            if (scheduleRef.equals(entry.getValue().scheduleRef)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    // -------------------- Internal --------------------

    /**
     * Evict entries that have not been touched for {@link #ENTRY_TTL_MS}.
     * <p>
     * Two corrections over the previous version, both of which caused live tasks
     * to be evicted:
     * <ul>
     *   <li>the age is measured from {@link TaskMeta#updatedAt}, not from creation.
     *       A long-running task that keeps reporting progress is not stale, and
     *       treating it as stale dropped tracking for a task that was still
     *       running. The {@code updatedAt} field already existed for exactly this
     *       purpose and was simply never read.</li>
     *   <li>only clearly-finished states are evictable. Matching on
     *       {@code !"running"} meant any other in-progress label — {@code queued},
     *       {@code retrying}, a future state nobody has thought of yet — was
     *       eligible for eviction while still live.</li>
     * </ul>
     */
    private static void evictStaleEntries() {
        try {
            long now = System.currentTimeMillis();
            int evicted = 0;

            for (Map.Entry<UUID, TaskMeta> entry : REGISTRY.entrySet()) {
                TaskMeta meta = entry.getValue();
                if (!isEvictable(meta.state)) continue;

                long lastTouched = Math.max(meta.updatedAt, meta.createdAt);
                if (now - lastTouched <= ENTRY_TTL_MS) continue;

                if (REGISTRY.remove(entry.getKey(), meta)) {
                    evicted++;
                }
            }

            if (evicted > 0) {
                totalEvicted.addAndGet(evicted);
                LogUtils.getLogger().debug("[Mili] Evicted {} stale task UUIDs (active={})",
                        evicted, REGISTRY.size());
            }
        // Mili start - fix: catch Throwable to prevent Error from silently cancelling all future scheduling
        } catch (Throwable t) {
            LogUtils.getLogger().error("[Mili] RegionTaskIdRegistry cleanup error", t);
        }
        // Mili end
    }

    /**
     * Whether a task in {@code state} may be evicted on TTL.
     * <p>
     * Allow-list rather than deny-list on purpose: an unknown state must be
     * treated as still live, so that adding a state later cannot silently start
     * dropping running tasks.
     */
    private static boolean isEvictable(String state) {
        if (state == null) return false;
        return switch (state) {
            case "completed", "failed", "cancelled", "timed_out", "merged" -> true;
            default -> false;
        };
    }

    // -------------------- Stats --------------------

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("active_entries", REGISTRY.size());
        stats.put("initialized", INITIALIZED.get());
        stats.put("shutdown", SHUTDOWN.get());
        stats.put("total_allocated", totalAllocated.get());
        stats.put("total_registered", totalRegistered.get());
        stats.put("total_unregistered", totalUnregistered.get());
        stats.put("total_evicted", totalEvicted.get());
        stats.put("total_collisions", totalCollisions.get());
        stats.put("total_rejected_full", totalRejectedFull.get());
        stats.put("total_rejected_duplicate", totalRejectedDuplicate.get());
        return stats;
    }

    public static int activeCount() {
        return REGISTRY.size();
    }

    // -------------------- TaskMeta --------------------

    /**
     * Metadata associated with a registered task UUID.
     */
    public static final class TaskMeta {
        public final UUID uuid;
        public final String taskType;
        public final Object scheduleRef;
        public final long createdAt;
        public volatile String state;
        public volatile long updatedAt;

        TaskMeta(UUID uuid, String taskType, Object scheduleRef, long createdAt) {
            this.uuid = uuid;
            this.taskType = taskType;
            this.scheduleRef = scheduleRef;
            this.createdAt = createdAt;
            this.state = "queued";
            this.updatedAt = createdAt;
        }

        @Override
        public String toString() {
            return "TaskMeta{uuid=" + uuid +
                    ", type='" + taskType + '\'' +
                    ", state='" + state + '\'' +
                    ", age=" + (System.currentTimeMillis() - createdAt) + "ms" +
                    '}';
        }
    }
}
