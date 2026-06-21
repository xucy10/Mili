package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.misc.UnifiedSchedulerConfig;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 实体线程调度监控 / Entity thread scheduling monitor.
 *
 * <p>在 Folia 的区域化线程模型中，所有实体状态变更必须在实体所属的区域线程上执行 /
 * In Folia's regionized threading model, ALL entity state mutation MUST happen
 * on the entity's owning region thread.
 *
 * <p>本类是一个监控门面，不创建独立线程 / This class is a monitoring facade,
 * it does NOT spawn separate threads:
 * <ul>
 *   <li>追踪每个区域的实体数量并报告高密度区域 / Tracks entity count per region</li>
 *   <li>为实体相关的延迟诊断提供统计 / Provides statistics for entity-related lag diagnosis</li>
 *   <li>高实体区域自动降级标记 / Auto-marks high-entity regions for fallback</li>
 * </ul>
 */
public final class EntityThreadScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ServerLevel, EntityThreadScheduler> INSTANCES = new ConcurrentHashMap<>();

    private final ServerLevel level;
    private final AtomicLong processedEntities = new AtomicLong(0);
    private final AtomicLong fallbackCount = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final int highEntityThreshold;

    public EntityThreadScheduler(ServerLevel level) {
        this.level = level;
        this.highEntityThreshold = UnifiedSchedulerConfig.entityHighThreshold;

        if (UnifiedSchedulerConfig.entityThreadEnabled) {
            INSTANCES.put(level, this);
            LOGGER.info("EntityThreadScheduler (monitor mode): threshold={} on dim {}",
                    highEntityThreshold, level.dimension().identifier());
        }
    }

    public static EntityThreadScheduler getInstance(ServerLevel level) {
        return INSTANCES.get(level);
    }

    /**
     * 检查区域是否为高实体密度 / Check if region has high entity density.
     *
     * @param regionEntityCount 区域内当前实体数 / Current entity count in region
     * @return true 如果超过阈值 / true if above threshold
     */
    public boolean isHighEntityRegion(int regionEntityCount) {
        return regionEntityCount >= highEntityThreshold;
    }

    /**
     * 记录高实体区域降级事件 / Record a high-entity region fallback event.
     */
    public void recordFallback(int entityCount) {
        long count = fallbackCount.incrementAndGet();
        if (count % 100 == 1) {
            LOGGER.debug("EntityThreadScheduler: {} high-entity regions detected ({} entities)",
                    fallbackCount.get(), entityCount);
        }
    }

    /** 记录一次实体处理 / Record one entity processed. */
    public void recordProcessed() {
        processedEntities.incrementAndGet();
    }

    public void start() {
        running.set(true);
    }

    public void stop() {
        running.set(false);
        INSTANCES.remove(level);
        LOGGER.info("EntityThreadScheduler stopped. High-entity events: {}",
                fallbackCount.get());
    }

    public String getStats() {
        return String.format("EntityThreadScheduler[processed=%d, highEntityEvents=%d, threshold=%d]",
                processedEntities.get(), fallbackCount.get(), highEntityThreshold);
    }

    public boolean isRunning() {
        return running.get();
    }
}
