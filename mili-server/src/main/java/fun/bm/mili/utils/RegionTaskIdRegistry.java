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
import java.util.concurrent.atomic.AtomicInteger;
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
 *   <li><b>Bounded memory:</b> A scheduled cleaner evicts entries older than
 *       {@link #ENTRY_TTL_MS} every {@link #CLEANUP_INTERVAL_MS}, preventing
 *       leaks from tasks that completed without calling {@link #unregister}.</li>
 *   <li><b>Shutdown-safe:</b> {@link #shutdown()} stops the cleaner and clears
 *       all state. Subsequent {@link #allocateAndRegister} calls return a
 *       fresh UUID but skip registration (no-op).</li>
 * </ul>
 */
public final class RegionTaskIdRegistry {

    private RegionTaskIdRegistry() {}

    // -------------------- Constants --------------------

    private static final int MAX_REGEN_ATTEMPTS = 8;
    private static final long ENTRY_TTL_MS = 120_000;       // 2 minutes
    private static final long CLEANUP_INTERVAL_MS = 30_000;  // 30 seconds
    private static final int MAX_ACTIVE_ENTRIES = 50_000;

    // -------------------- State --------------------

    private static final ConcurrentHashMap<UUID, TaskMeta> REGISTRY = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> CREATION_TIME = new ConcurrentHashMap<>();

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

    private static ScheduledExecutorService cleanupScheduler;

    private static final AtomicInteger ACTIVE_ENTRIES = new AtomicInteger(0);
    private static final AtomicLong totalAllocated = new AtomicLong(0);
    private static final AtomicLong totalRegistered = new AtomicLong(0);
    private static final AtomicLong totalUnregistered = new AtomicLong(0);
    private static final AtomicLong totalEvicted = new AtomicLong(0);
    private static final AtomicLong totalCollisions = new AtomicLong(0);
    private static final AtomicLong totalRejectedFull = new AtomicLong(0);
    private static final AtomicLong totalRejectedDuplicate = new AtomicLong(0);

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

        int cleared = ACTIVE_ENTRIES.getAndSet(0);
        REGISTRY.clear();
        CREATION_TIME.clear();

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
        UUID uuid = allocateUniqueUuid();

        if (SHUTDOWN.get()) {
            // Registry is shutting down — return UUID without registration
            return uuid;
        }

        if (!tryReserveActiveSlot()) {
            totalRejectedFull.incrementAndGet();
            LogUtils.getLogger().warn("[Mili] RegionTaskIdRegistry full ({}), skipping registration for {}",
                    MAX_ACTIVE_ENTRIES, uuid);
            return uuid;
        }
        if (SHUTDOWN.get()) {
            releaseActiveSlot();
            return uuid;
        }

        boolean registered = false;
        try {
            TaskMeta meta = new TaskMeta(uuid, taskType, scheduleRef, System.currentTimeMillis());
            TaskMeta existing = REGISTRY.putIfAbsent(uuid, meta);
            if (existing != null) {
                // Collision — should be astronomically rare, but handle it
                totalCollisions.incrementAndGet();
                LogUtils.getLogger().warn("[Mili] UUID collision detected: {} (already registered as '{}')",
                        uuid, existing.taskType);
                // Regenerate and retry
                for (int i = 0; i < MAX_REGEN_ATTEMPTS; i++) {
                    uuid = allocateUniqueUuid();
                    meta = new TaskMeta(uuid, taskType, scheduleRef, System.currentTimeMillis());
                    if (REGISTRY.putIfAbsent(uuid, meta) == null) {
                        registered = true;
                        break;
                    }
                    totalCollisions.incrementAndGet();
                }
                // If still colliding after retries, use the last UUID without registration
                // (effectively untracked, but the task can still run)
                if (!registered) {
                    LogUtils.getLogger().error("[Mili] Failed to register UUID after {} attempts, task will run untracked", MAX_REGEN_ATTEMPTS);
                    return uuid;
                }
            } else {
                registered = true;
            }

            CREATION_TIME.put(uuid, System.currentTimeMillis());
            totalAllocated.incrementAndGet();
            totalRegistered.incrementAndGet();
            return uuid;
        } finally {
            if (!registered) {
                releaseActiveSlot();
            }
        }
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

        if (!tryReserveActiveSlot()) {
            totalRejectedFull.incrementAndGet();
            return false;
        }
        if (SHUTDOWN.get()) {
            releaseActiveSlot();
            return false;
        }

        TaskMeta meta = new TaskMeta(uuid, taskType, scheduleRef, System.currentTimeMillis());
        if (REGISTRY.putIfAbsent(uuid, meta) != null) {
            totalRejectedDuplicate.incrementAndGet();
            releaseActiveSlot();
            return false; // already registered — duplicate
        }
        CREATION_TIME.put(uuid, System.currentTimeMillis());
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
        CREATION_TIME.remove(uuid);
        if (removed != null) {
            releaseActiveSlot();
            totalUnregistered.incrementAndGet();
            return true;
        }
        return false;
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

    private static boolean tryReserveActiveSlot() {
        while (true) {
            int current = ACTIVE_ENTRIES.get();
            if (current >= MAX_ACTIVE_ENTRIES) {
                return false;
            }
            if (ACTIVE_ENTRIES.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private static void releaseActiveSlot() {
        ACTIVE_ENTRIES.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    private static UUID allocateUniqueUuid() {
        return UUID.randomUUID();
    }

    private static void evictStaleEntries() {
        try {
            long now = System.currentTimeMillis();
            int evicted = 0;

            Iterator<Map.Entry<UUID, Long>> it = CREATION_TIME.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                if (now - entry.getValue() > ENTRY_TTL_MS) {
                    UUID uuid = entry.getKey();
                    TaskMeta meta = REGISTRY.get(uuid);
                    if (meta == null) {
                        it.remove();
                        continue;
                    }
                    // Don't evict running tasks — only stale queued/completed ones
                    if (!"running".equals(meta.state)) {
                        if (REGISTRY.remove(uuid, meta)) {
                            releaseActiveSlot();
                            evicted++;
                        }
                        it.remove();
                    }
                }
            }

            if (evicted > 0) {
                totalEvicted.addAndGet(evicted);
                LogUtils.getLogger().debug("[Mili] Evicted {} stale task UUIDs (active={})",
                        evicted, ACTIVE_ENTRIES.get());
            }
        // Mili start - fix: catch Throwable to prevent Error from silently cancelling all future scheduling
        } catch (Throwable t) {
            LogUtils.getLogger().error("[Mili] RegionTaskIdRegistry cleanup error", t);
        }
        // Mili end
    }

    // -------------------- Stats --------------------

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("active_entries", ACTIVE_ENTRIES.get());
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
        return ACTIVE_ENTRIES.get();
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
