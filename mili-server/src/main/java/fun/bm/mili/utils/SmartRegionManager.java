package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import fun.bm.mili.scheduler.MiliScheduler;
import fun.bm.mili.scheduler.RecurringTask;
import org.jetbrains.annotations.Nullable;
import com.mojang.logging.LogUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class SmartRegionManager {

    private SmartRegionManager() {}

    private static volatile boolean initialized = false;

    private static final ConcurrentHashMap<Integer, RegionProfile> regionProfiles = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<RegionMigrationTask> migrationQueue = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<RegionMigrationTask> taskPool = new ConcurrentLinkedQueue<>();
    private static final int maxPoolSize = 50;

    private static final AtomicLong totalMigrations = new AtomicLong(0);
    private static final AtomicLong successfulMigrations = new AtomicLong(0);
    private static final AtomicLong failedMigrations = new AtomicLong(0);

    private static ScheduledExecutorService scheduler;
    private static volatile RecurringTask.Handle analyzeTask;
    private static volatile RecurringTask.Handle migrateTask;

    public static void init() {
        if (!RegionBalancerConfig.enabled || initialized) return;

        // Mili start - 保留独占定时池：processMigrations 是 50ms 高频任务，若进共享
        // BACKGROUND 单线程会把其他巡检长期挤到饿死。这里承接原有的 NORM_PRIORITY + 2，
        // 避免"纳管"变成"降级"；同时改用 RecurringTask 以获得异常隔离，池仍归治理层托管。
        // 两个任务原本都是 scheduleAtFixedRate，故沿用默认 FIXED_RATE，不改成 FIXED_DELAY。
        scheduler = MiliScheduler.namedScheduledPool("smart-region", 1, Thread.NORM_PRIORITY + 2);

        analyzeTask = RecurringTask.startOn(scheduler, "smart-region-analyze",
                0L, RegionBalancerConfig.analysisIntervalMs, SmartRegionManager::analyzeRegions);

        migrateTask = RecurringTask.startOn(scheduler, "smart-region-migrate",
                100L, 50L, SmartRegionManager::processMigrations);
        // Mili end

        initialized = true;
        LogUtils.getLogger().info("[Mili] SmartRegionManager v2.0 initialized");
    }

    public static void shutdown() {
        if (!initialized) return;
        initialized = false;

        // Mili start - 池已由线程治理层托管，此处只取消任务句柄并释放引用
        if (analyzeTask != null) {
            analyzeTask.cancel();
            analyzeTask = null;
        }
        if (migrateTask != null) {
            migrateTask.cancel();
            migrateTask = null;
        }
        scheduler = null;
        // Mili end

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
        } catch (Exception e) {
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
            } catch (Exception e) {
                LogUtils.getLogger().warn(
                        "[Mili] Migration failed for region: {}", task.regionKey, e
                );
                failedMigrations.incrementAndGet();
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

        RegionMigrationTask(Integer regionKey, RegionProfile profile) {
            this.regionKey = regionKey;
            this.profile = profile;
        }

        void reset(Integer regionKey, RegionProfile profile) {
            this.regionKey = regionKey;
            this.profile = profile;
        }

        boolean execute() {
            try {
                RegionLoadMonitor.RegionLoadSnapshot snap = profile.getCurrentSnapshot();
                if (snap == null) return false;

                LogUtils.getLogger().info(
                        "[Mili] Processing migration for region with load={}%, trend={}",
                        (int)(snap.loadFactor() * 100),
                        String.format("%.3f", profile.getTrendSlope())
                );

                TimeUnit.MILLISECONDS.sleep(10);

                profile.recordMigrationResult(true);
                return true;
            } catch (Exception e) {
                profile.recordMigrationResult(false);
                return false;
            }
        }
    }
}