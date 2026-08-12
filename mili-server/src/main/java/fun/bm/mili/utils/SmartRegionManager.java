package fun.bm.mili.utils;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class SmartRegionManager {

    private SmartRegionManager() {}

    // Mili start - fix: use AtomicBoolean for thread-safe init/shutdown
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    // Mili end

    private static final ConcurrentHashMap<Integer, RegionProfile> regionProfiles = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<RegionMigrationTask> migrationQueue = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<RegionMigrationTask> taskPool = new ConcurrentLinkedQueue<>();
    private static final int maxPoolSize = 50;

    private static final AtomicLong totalMigrations = new AtomicLong(0);
    private static final AtomicLong successfulMigrations = new AtomicLong(0);
    private static final AtomicLong failedMigrations = new AtomicLong(0);

    private static ScheduledExecutorService scheduler;

    public static void init() {
        // Mili start - fix: CAS-based init to prevent double initialization race
        if (!RegionBalancerConfig.enabled) return;
        if (!initialized.compareAndSet(false, true)) return;
        // Mili end

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Mili-SmartRegion");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY + 2);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                SmartRegionManager::analyzeRegions,
                0,
                RegionBalancerConfig.analysisIntervalMs,
                TimeUnit.MILLISECONDS
        );

        scheduler.scheduleAtFixedRate(
                SmartRegionManager::processMigrations,
                100,
                50,
                TimeUnit.MILLISECONDS
        );

        LogUtils.getLogger().info("[Mili] SmartRegionManager v2.0 initialized");
    }

    public static void shutdown() {
        // Mili start - fix: CAS-based shutdown to prevent double shutdown race
        if (!initialized.compareAndSet(true, false)) return;
        // Mili end

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                // Mili start - fix: restore interrupt flag
                Thread.currentThread().interrupt();
                // Mili end
            }
            scheduler = null;
        }

        regionProfiles.clear();
        migrationQueue.clear();
        taskPool.clear();

        LogUtils.getLogger().info("[Mili] SmartRegionManager shutdown");
    }

    private static void analyzeRegions() {
        try {
            for (java.util.Map.Entry<Integer, RegionLoadMonitor.RegionLoadSnapshot> entry
                    : RegionLoadMonitor.getAllSnapshotMap().entrySet()) {
                Integer regionKey = entry.getKey();
                RegionLoadMonitor.RegionLoadSnapshot snapshot = entry.getValue();

                RegionProfile profile = regionProfiles.computeIfAbsent(
                        regionKey, k -> new RegionProfile(k)
                );

                profile.updateSnapshot(snapshot);
                profile.analyzeTrends();

                if (profile.shouldMigrate()) {
                    scheduleMigration(regionKey, profile);
                }
            }
        } catch (Throwable e) {
            // Mili start - fix: catch Throwable to prevent scheduler thread death on Error
            LogUtils.getLogger().error("[Mili] Region analysis error", e);
        }
    }

    private static void processMigrations() {
        int processed = 0;
        long deadline = System.nanoTime() + 5_000_000L;

        while (processed < 5 && System.nanoTime() < deadline) {
            RegionMigrationTask task = migrationQueue.poll();
            if (task == null) break;

            try {
                boolean success = task.execute();
                totalMigrations.incrementAndGet();
                if (success) {
                    successfulMigrations.incrementAndGet();
                } else {
                    failedMigrations.incrementAndGet();
                }
                processed++;

                if (taskPool.size() < maxPoolSize) {
                    task.reset(null, null);
                    taskPool.offer(task);
                }
            } catch (Throwable e) {
                // Mili start - fix: catch Throwable (not just Exception) + ensure task is recycled to pool even on error
                LogUtils.getLogger().warn(
                        "[Mili] Migration failed for region: {}", task.regionKey, e
                );
                failedMigrations.incrementAndGet();
                // Recycle the task to pool to prevent object leak
                if (taskPool.size() < maxPoolSize) {
                    task.reset(null, null);
                    taskPool.offer(task);
                }
                // Mili end
            }
        }
    }

    private static void scheduleMigration(Integer regionKey, RegionProfile profile) {
        if (migrationQueue.size() > 50) return;

        RegionMigrationTask task = taskPool.poll();
        if (task != null) {
            task.reset(regionKey, profile);
        } else {
            task = new RegionMigrationTask(regionKey, profile);
        }
        migrationQueue.add(task);
    }

    public static void registerRegion(Integer regionKey) {
        regionProfiles.computeIfAbsent(regionKey, k -> new RegionProfile(k));
    }

    public static void unregisterRegion(Integer regionKey) {
        regionProfiles.remove(regionKey);
    }

    @Nullable
    public static RegionProfile getProfile(Integer regionKey) {
        return regionProfiles.get(regionKey);
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_migrations", totalMigrations.get());
        stats.put("successful_migrations", successfulMigrations.get());
        stats.put("failed_migrations", failedMigrations.get());
        stats.put("tracked_regions", regionProfiles.size());
        stats.put("pending_migrations", migrationQueue.size());

        int overloadedCount = 0;
        int underloadedCount = 0;
        for (RegionProfile profile : regionProfiles.values()) {
            if (profile.isOverloaded()) overloadedCount++;
            else if (profile.isUnderloaded()) underloadedCount++;
        }
        stats.put("overloaded_regions", overloadedCount);
        stats.put("underloaded_regions", underloadedCount);

        return stats;
    }

    public static final class RegionProfile {
        final Integer regionKey;
        final AtomicReference<RegionLoadMonitor.RegionLoadSnapshot> currentSnapshot =
                new AtomicReference<>(new RegionLoadMonitor.RegionLoadSnapshot(0, 0, 0, 0.0, false, true));

        final double[] loadHistory = new double[20];
        int historyPos = 0;
        int historyCount = 0;

        volatile double trendSlope = 0.0;
        volatile long lastMigrationAttempt = 0;
        volatile int consecutiveFailures = 0;

        RegionProfile(Integer regionKey) {
            this.regionKey = regionKey;
        }

        void updateSnapshot(RegionLoadMonitor.RegionLoadSnapshot snapshot) {
            this.currentSnapshot.set(snapshot);

            loadHistory[historyPos] = snapshot.loadFactor();
            historyPos = (historyPos + 1) % loadHistory.length;
            if (historyCount < loadHistory.length) historyCount++;
        }

        void analyzeTrends() {
            if (historyCount < 5) return;

            int n = Math.min(historyCount, loadHistory.length);
            double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;

            for (int i = 0; i < n; i++) {
                double x = i;
                double y = loadHistory[i];
                sumX += x;
                sumY += y;
                sumXY += x * y;
                sumXX += x * x;
            }

            double denom = n * sumXX - sumX * sumX;
            this.trendSlope = (denom != 0) ? (n * sumXY - sumX * sumY) / denom : 0.0;
        }

        boolean shouldMigrate() {
            RegionLoadMonitor.RegionLoadSnapshot snap = currentSnapshot.get();
            if (snap == null) return false;

            long now = System.nanoTime();
            if (now - lastMigrationAttempt < 30_000_000_000L) return false;
            if (consecutiveFailures >= 3) return false;

            boolean overloaded = snap.isHighLoad() && trendSlope > 0.01;
            boolean severelyUnderloaded = snap.loadFactor() < 0.1 && historyCount >= 15;

            return overloaded || severelyUnderloaded;
        }

        boolean isOverloaded() {
            RegionLoadMonitor.RegionLoadSnapshot snap = currentSnapshot.get();
            return snap != null && snap.isHighLoad();
        }

        boolean isUnderloaded() {
            RegionLoadMonitor.RegionLoadSnapshot snap = currentSnapshot.get();
            return snap != null && snap.isLowLoad() && snap.loadFactor() < 0.15;
        }

        RegionLoadMonitor.RegionLoadSnapshot getCurrentSnapshot() {
            return currentSnapshot.get();
        }

        double getTrendSlope() {
            return trendSlope;
        }

        double getAverageLoad() {
            if (historyCount == 0) return 0.0;
            double sum = 0;
            for (int i = 0; i < historyCount; i++) {
                sum += loadHistory[i];
            }
            return sum / historyCount;
        }

        void recordMigrationResult(boolean success) {
            lastMigrationAttempt = System.nanoTime();
            if (success) {
                consecutiveFailures = 0;
            } else {
                consecutiveFailures++;
            }
        }
    }

    private static class RegionMigrationTask {
        Integer regionKey;
        RegionProfile profile;
        // Mili start - task UUID for cross-region migration tracking
        UUID taskUuid;
        // Mili end

        RegionMigrationTask(Integer regionKey, RegionProfile profile) {
            this.regionKey = regionKey;
            this.profile = profile;
            // Mili start - allocate UUID on creation
            this.taskUuid = RegionTaskIdRegistry.allocateAndRegister("region-migration", regionKey);
            // Mili end
        }

        void reset(Integer regionKey, RegionProfile profile) {
            // Mili start - unregister old UUID before reusing the task object
            if (this.taskUuid != null) {
                RegionTaskIdRegistry.unregister(this.taskUuid);
            }
            // Mili end
            this.regionKey = regionKey;
            this.profile = profile;
            // Mili start - allocate new UUID on reset (no UUID reuse — prevents stale references)
            this.taskUuid = (regionKey != null)
                    ? RegionTaskIdRegistry.allocateAndRegister("region-migration", regionKey)
                    : null;
            // Mili end
        }

        boolean execute() {
            try {
                RegionLoadMonitor.RegionLoadSnapshot snap = profile.getCurrentSnapshot();
                if (snap == null) return false;

                // Mili start - update task state to running
                if (taskUuid != null) {
                    RegionTaskIdRegistry.updateState(taskUuid, "running");
                }
                // Mili end

                LogUtils.getLogger().info(
                        "[Mili] Processing migration for region with load={}%, trend={}, taskUuid={}",
                        (int)(snap.loadFactor() * 100),
                        String.format("%.3f", profile.getTrendSlope()),
                        taskUuid
                );

                TimeUnit.MILLISECONDS.sleep(10);

                profile.recordMigrationResult(true);
                // Mili start - unregister on successful completion
                if (taskUuid != null) {
                    RegionTaskIdRegistry.updateState(taskUuid, "completed");
                    RegionTaskIdRegistry.unregister(taskUuid);
                }
                // Mili end
                return true;
            } catch (Throwable e) {
                // Mili start - fix: catch Throwable (not just Exception) to prevent UUID leak on Error
                profile.recordMigrationResult(false);
                // Mili start - unregister on failure
                if (taskUuid != null) {
                    RegionTaskIdRegistry.updateState(taskUuid, "failed");
                    RegionTaskIdRegistry.unregister(taskUuid);
                }
                // Mili end
                return false;
            }
        }
    }
}