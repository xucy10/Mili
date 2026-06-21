package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * mili - 全局调度器注册表 / Global scheduler registry.
 *
 * <p>管理所有世界的调度器实例，提供统一的生命周期控制 /
 * Manages all per-world scheduler instances, provides unified lifecycle control.
 *
 * <p>设计原则 / Design principles:
 * <ul>
 *   <li>单一入口访问所有调度器 / Single entry point for all schedulers</li>
 *   <li>自动注册/注销 / Auto register/unregister on world load/unload</li>
 *   <li>全局统计汇总 / Global statistics aggregation</li>
 * </ul>
 *
 * @author Mili Team
 * @since 1.21.11
 */
public final class SchedulerRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SchedulerRegistry INSTANCE = new SchedulerRegistry();

    // World -> Scheduler mappings
    private final Map<ServerLevel, EntityThreadScheduler> entitySchedulers = new ConcurrentHashMap<>();
    private final Map<ServerLevel, ChunkIndependentScheduler> chunkSchedulers = new ConcurrentHashMap<>();

    private SchedulerRegistry() {}

    /**
     * 获取单例 / Get singleton instance.
     */
    public static SchedulerRegistry getInstance() {
        return INSTANCE;
    }

    // ======================== Entity Thread Scheduler ========================

    /**
     * 注册实体调度器 / Register entity scheduler.
     *
     * @param level 世界 / World
     * @param scheduler 调度器 / Scheduler
     */
    public void registerEntityScheduler(ServerLevel level, EntityThreadScheduler scheduler) {
        entitySchedulers.put(level, scheduler);
        LOGGER.debug("[SchedulerRegistry] Registered EntityThreadScheduler for {}", level.dimension().identifier());
    }

    /**
     * 注销实体调度器 / Unregister entity scheduler.
     *
     * @param level 世界 / World
     */
    public void unregisterEntityScheduler(ServerLevel level) {
        EntityThreadScheduler removed = entitySchedulers.remove(level);
        if (removed != null) {
            removed.stop();
            LOGGER.debug("[SchedulerRegistry] Unregistered EntityThreadScheduler for {}", level.dimension().identifier());
        }
    }

    /**
     * 获取实体调度器 / Get entity scheduler.
     *
     * @param level 世界 / World
     * @return 调度器或 null / Scheduler or null
     */
    public EntityThreadScheduler getEntityScheduler(ServerLevel level) {
        return entitySchedulers.get(level);
    }

    // ======================== Chunk Independent Scheduler ========================

    /**
     * 注册区块调度器 / Register chunk scheduler.
     *
     * @param level 世界 / World
     * @param scheduler 调度器 / Scheduler
     */
    public void registerChunkScheduler(ServerLevel level, ChunkIndependentScheduler scheduler) {
        chunkSchedulers.put(level, scheduler);
        LOGGER.debug("[SchedulerRegistry] Registered ChunkIndependentScheduler for {}", level.dimension().identifier());
    }

    /**
     * 注销区块调度器 / Unregister chunk scheduler.
     *
     * @param level 世界 / World
     */
    public void unregisterChunkScheduler(ServerLevel level) {
        ChunkIndependentScheduler removed = chunkSchedulers.remove(level);
        if (removed != null) {
            removed.stop();
            LOGGER.debug("[SchedulerRegistry] Unregistered ChunkIndependentScheduler for {}", level.dimension().identifier());
        }
    }

    /**
     * 获取区块调度器 / Get chunk scheduler.
     *
     * @param level 世界 / World
     * @return 调度器或 null / Scheduler or null
     */
    public ChunkIndependentScheduler getChunkScheduler(ServerLevel level) {
        return chunkSchedulers.get(level);
    }

    // ======================== Lifecycle Management ========================

    /**
     * 启动所有调度器 / Start all schedulers.
     */
    public void startAll() {
        LOGGER.info("[SchedulerRegistry] Starting all schedulers...");
        entitySchedulers.values().forEach(EntityThreadScheduler::start);
        chunkSchedulers.values().forEach(ChunkIndependentScheduler::start);
        LOGGER.info("[SchedulerRegistry] Started {} entity schedulers, {} chunk schedulers",
                entitySchedulers.size(), chunkSchedulers.size());
    }

    /**
     * 停止所有调度器 / Stop all schedulers.
     */
    public void stopAll() {
        LOGGER.info("[SchedulerRegistry] Stopping all schedulers...");
        entitySchedulers.values().forEach(EntityThreadScheduler::stop);
        chunkSchedulers.values().forEach(ChunkIndependentScheduler::stop);
        entitySchedulers.clear();
        chunkSchedulers.clear();
        LOGGER.info("[SchedulerRegistry] All schedulers stopped");
    }

    // ======================== Statistics ========================

    /**
     * 获取全局统计 / Get global statistics.
     */
    public String getGlobalStats() {
        StringBuilder sb = new StringBuilder("=== Scheduler Registry Stats ===\n");
        sb.append(String.format("Entity Schedulers: %d active\n", entitySchedulers.size()));
        for (Map.Entry<ServerLevel, EntityThreadScheduler> entry : entitySchedulers.entrySet()) {
            sb.append(String.format("  %s: %s\n",
                    entry.getKey().dimension().identifier(),
                    entry.getValue().getStats()));
        }
        sb.append(String.format("\nChunk Schedulers: %d active\n", chunkSchedulers.size()));
        for (Map.Entry<ServerLevel, ChunkIndependentScheduler> entry : chunkSchedulers.entrySet()) {
            sb.append(String.format("  %s: %s\n",
                    entry.getKey().dimension().identifier(),
                    entry.getValue().isRunning() ? "running" : "stopped"));
        }
        sb.append("=== End Stats ===");
        return sb.toString();
    }

    /**
     * 获取调度器总数 / Get total scheduler count.
     */
    public int getTotalSchedulerCount() {
        return entitySchedulers.size() + chunkSchedulers.size();
    }
}
