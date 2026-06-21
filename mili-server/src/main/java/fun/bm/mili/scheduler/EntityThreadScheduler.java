package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.misc.UnifiedSchedulerConfig;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Entity thread scheduling monitor.
 *
 * In Folia's regionized threading model, ALL entity state mutation MUST
 * happen on the entity's owning region thread. Running entity tick on a
 * separate thread pool (the original design) causes:
 * - Entity position corruption (teleporting, flying)
 * - Entity display desync (not showing)
 * - Knockback/pathfinding anomalies
 * - Spawning failures
 *
 * This class is now a monitoring facade that:
 * 1. Tracks entity count per region and reports high-density regions
 * 2. Does NOT spawn separate threads for entity ticking
 * 3. Provides statistics for diagnosing entity-related lag
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
                    highEntityThreshold, level.dimension().toString());
        }
    }

    public static EntityThreadScheduler getInstance(ServerLevel level) {
        return INSTANCES.get(level);
    }

    public boolean isHighEntityRegion(int regionEntityCount) {
        return regionEntityCount >= highEntityThreshold;
    }

    public void recordFallback(int entityCount) {
        long count = fallbackCount.incrementAndGet();
        if (count % 100 == 1) {
            LOGGER.debug("EntityThreadScheduler: {} high-entity regions detected ({} entities)",
                    fallbackCount.get(), entityCount);
        }
    }

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
        return String.format("processed=%d, highEntityEvents=%d",
                processedEntities.get(), fallbackCount.get());
    }

    public boolean isRunning() {
        return running.get();
    }
}
