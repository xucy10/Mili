package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.RegionBalancerConfig;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import org.jetbrains.annotations.Nullable;
import com.mojang.logging.LogUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class SmartRegionManager {

    private SmartRegionManager() {}

    private static volatile boolean initialized = false;

    private static final ConcurrentHashMap<Object, RegionProfile> REGION_PROFILES = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<RegionMigrationTask> MIGRATION_QUEUE = new ConcurrentLinkedQueue<>();

    private static final AtomicLong TOTAL_MIGRATIONS = new AtomicLong(0);
    private static final AtomicLong SUCCESSFUL_MIGRATIONS = new AtomicLong(0);
    private static final AtomicLong FAILED_MIGRATIONS = new AtomicLong(0);

    private static ScheduledExecutorService scheduler;

    public static void init() {
        if (!RegionBalancerConfig.enabled || initialized) return;

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

        initialized = true;
        LogUtils.getLogger().info("[Mili] SmartRegionManager v2.0 initialized");
    }

    public static void shutdown() {
        if (!initialized) return;
        initialized = false;

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }

        REGION_PROFILES.clear();
        MIGRATION_QUEUE.clear();

        LogUtils.getLogger().info("[Mili] SmartRegionManager shutdown");
    }

    private static void analyzeRegions() {
        try {
            for (Map.Entry<Object, RegionProfile> entry : REGION_PROFILES.entrySet()) {
                Object regionKey = entry.getKey();
                RegionProfile profile = entry.getValue();

                RegionLoadMonitor.RegionLoadSnapshot snapshot =
                        RegionLoadMonitor.getSnapshot(regionKey);

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
            RegionMigrationTask task = MIGRATION_QUEUE.poll();
            if (task == null) break;

            try {
                boolean success = task.execute();
                TOTAL_MIGRATIONS.incrementAndGet();
                if (success) {
                    SUCCESSFUL_MIGRATIONS.incrementAndGet();
                } else {
                    FAILED_MIGRATIONS.incrementAndGet();
                }
                processed++;
            } catch (Exception e) {
                LogUtils.getLogger().warn(
                        "[Mili] Migration failed for region: {}", task.regionKey, e
                );
                FAILED_MIGRATIONS.incrementAndGet();
            }
        }
    }

    private static void scheduleMigration(Object regionKey, RegionProfile profile) {
        if (MIGRATION_QUEUE.size() > 50) return;

        MIGRATION_QUEUE.add(new RegionMigrationTask(regionKey, profile));
    }

    public static void registerRegion(Object regionKey) {
        REGION_PROFILES.computeIfAbsent(regionKey, k -> new RegionProfile(k));
    }

    public static void unregisterRegion(Object regionKey) {
        REGION_PROFILES.remove(regionKey);
    }

    @Nullable
    public static RegionProfile getProfile(Object regionKey) {
        return REGION_PROFILES.get(regionKey);
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_migrations", TOTAL_MIGRATIONS.get());
        stats.put("successful_migrations", SUCCESSFUL_MIGRATIONS.get());
        stats.put("failed_migrations", FAILED_MIGRATIONS.get());
        stats.put("tracked_regions", REGION_PROFILES.size());
        stats.put("pending_migrations", MIGRATION_QUEUE.size());

        int overloadedCount = 0;
        int underloadedCount = 0;
        for (RegionProfile profile : REGION_PROFILES.values()) {
            if (profile.isOverloaded()) overloadedCount++;
            else if (profile.isUnderloaded()) underloadedCount++;
        }
        stats.put("overloaded_regions", overloadedCount);
        stats.put("underloaded_regions", underloadedCount);

        return stats;
    }

    public static final class RegionProfile {
        final Object regionKey;
        final AtomicReference<RegionLoadMonitor.RegionLoadSnapshot> currentSnapshot =
                new AtomicReference<>(new RegionLoadMonitor.RegionLoadSnapshot(0, 0, 0, 0.0, false, true));

        final double[] loadHistory = new double[20];
        int historyPos = 0;
        int historyCount = 0;

        volatile double trendSlope = 0.0;
        volatile long lastMigrationAttempt = 0;
        volatile int consecutiveFailures = 0;

        RegionProfile(Object regionKey) {
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
        final Object regionKey;
        final RegionProfile profile;

        RegionMigrationTask(Object regionKey, RegionProfile profile) {
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

                Thread.sleep(10);

                profile.recordMigrationResult(true);
                return true;
            } catch (Exception e) {
                profile.recordMigrationResult(false);
                return false;
            }
        }
    }
}