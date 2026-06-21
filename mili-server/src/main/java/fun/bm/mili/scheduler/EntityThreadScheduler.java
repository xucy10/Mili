package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.misc.UnifiedSchedulerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * mili - 实体独立线程调度器 / Entity independent thread scheduler.
 *
 * <p>将实体 tick 从区域线程分离到独立线程池，避免实体异常导致区域卡死 /
 * Separates entity ticking from region threads to dedicated thread pool,
 * preventing entity issues from causing region freezes.
 *
 * <p>高实体区域保护机制：当区域内实体数超过阈值时，自动降级回区域线程 /
 * High entity count protection: falls back to region thread when entity
 * count exceeds threshold (e.g., mob farms).
 *
 * @author Mili Team
 * @since 1.21.11
 */
@ThreadSafe("Uses concurrent collections and atomic operations")
public final class EntityThreadScheduler extends SchedulerBase {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ServerLevel, EntityThreadScheduler> INSTANCES = new ConcurrentHashMap<>();

    private final ServerLevel level;
    private final ExecutorService entityPool;
    private final AtomicLong processedEntities = new AtomicLong(0);
    private final AtomicLong fallbackCount = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Config snapshot
    private volatile int workerThreads;
    private volatile int highEntityThreshold;
    private volatile boolean enabled;

    public EntityThreadScheduler(ServerLevel level) {
        this.level = level;

        UnifiedSchedulerConfig cfg = UnifiedSchedulerConfig.getInstance();
        this.enabled = cfg.enabled;
        this.workerThreads = cfg.workerThreads > 0 ? cfg.workerThreads : Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        this.highEntityThreshold = cfg.highEntityThreshold;

        this.entityPool = Executors.newFixedThreadPool(workerThreads, task -> {
            Thread t = new Thread(task, "mili-entity-worker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });

        if (enabled) {
            INSTANCES.put(level, this);
            LOGGER.info("EntityThreadScheduler started: {} workers, threshold {} on dim {}",
                    workerThreads, highEntityThreshold, level.dimension().toString());
        }
    }

    /**
     * 获取调度器实例 / Get scheduler instance.
     */
    public static EntityThreadScheduler getInstance(ServerLevel level) {
        return INSTANCES.get(level);
    }

    /**
     * 检查是否应该使用独立线程 / Check if should use dedicated thread.
     *
     * @param regionEntityCount 区域内当前实体数 / Current entity count in region
     * @return true 使用独立线程 / true = use dedicated thread
     */
    public boolean shouldUseDedicatedThread(int regionEntityCount) {
        if (!enabled || !running.get()) {
            return false;
        }
        // 高实体区域降级回区域线程 / Fallback to region thread for high entity counts
        return regionEntityCount < highEntityThreshold;
    }

    /**
     * 提交实体 tick 任务 / Submit entity tick task.
     *
     * @param entity 要 tick 的实体 / Entity to tick
     * @param tickTask tick 任务 / Tick task
     * @return true 如果已提交 / true if submitted
     */
    public boolean submitEntityTick(Entity entity, Runnable tickTask) {
        if (!enabled || !running.get()) {
            return false;
        }

        entityPool.submit(() -> {
            try {
                tickTask.run();
                processedEntities.incrementAndGet();
            } catch (Throwable t) {
                LOGGER.debug("Entity tick failed for {}: {}", entity.getType().getName(), t.getMessage());
            }
        });

        return true;
    }

    /**
     * 记录降级事件 / Record fallback event.
     */
    public void recordFallback(int entityCount) {
        fallbackCount.incrementAndGet();
        if (fallbackCount.get() % 100 == 1) {
            LOGGER.debug("EntityThreadScheduler: {} fallbacks due to high entity count ({} entities)",
                    fallbackCount.get(), entityCount);
        }
    }

    /**
     * 启动调度器 / Start scheduler.
     */
    public void start() {
        running.set(true);
    }

    /**
     * 停止调度器 / Stop scheduler.
     */
    public void stop() {
        running.set(false);
        entityPool.shutdown();
        try {
            if (!entityPool.awaitTermination(5, TimeUnit.SECONDS)) {
                entityPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            entityPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        INSTANCES.remove(level);
        LOGGER.info("EntityThreadScheduler stopped. Processed: {}, Fallbacks: {}",
                processedEntities.get(), fallbackCount.get());
    }

    /**
     * 获取统计信息 / Get statistics.
     */
    public String getStats() {
        return String.format("processed=%d, fallbacks=%d, enabled=%s",
                processedEntities.get(), fallbackCount.get(), enabled);
    }

    /**
     * 检查是否运行中 / Check if running.
     */
    public boolean isRunning() {
        return running.get() && enabled;
    }
}
