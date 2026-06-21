package fun.bm.mili.scheduler;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SchedulerRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SchedulerRegistry INSTANCE = new SchedulerRegistry();

    private final Map<ServerLevel, EntityThreadScheduler> entitySchedulers = new ConcurrentHashMap<>();
    private final Map<ServerLevel, ChunkIndependentScheduler> chunkSchedulers = new ConcurrentHashMap<>();

    private SchedulerRegistry() {}

    public static SchedulerRegistry getInstance() { return INSTANCE; }

    public void registerEntityScheduler(ServerLevel level, EntityThreadScheduler scheduler) {
        EntityThreadScheduler old = entitySchedulers.put(level, scheduler);
        if (old != null && old != scheduler) {
            try { old.stop(); } catch (Exception e) { LOGGER.error("stop old entity scheduler fail", e); }
        }
    }

    public void unregisterEntityScheduler(ServerLevel level) {
        EntityThreadScheduler removed = entitySchedulers.remove(level);
        if (removed != null) {
            try { removed.stop(); } catch (Exception e) { LOGGER.error("stop entity scheduler fail", e); }
        }
    }

    public EntityThreadScheduler getEntityScheduler(ServerLevel level) {
        return entitySchedulers.get(level);
    }

    public void registerChunkScheduler(ServerLevel level, ChunkIndependentScheduler scheduler) {
        ChunkIndependentScheduler old = chunkSchedulers.put(level, scheduler);
        if (old != null && old != scheduler) {
            try { old.stop(); } catch (Exception e) { LOGGER.error("stop old chunk scheduler fail", e); }
        }
    }

    public void unregisterChunkScheduler(ServerLevel level) {
        ChunkIndependentScheduler removed = chunkSchedulers.remove(level);
        if (removed != null) {
            try { removed.stop(); } catch (Exception e) { LOGGER.error("stop chunk scheduler fail", e); }
        }
    }

    public ChunkIndependentScheduler getChunkScheduler(ServerLevel level) {
        return chunkSchedulers.get(level);
    }

    public void startAll() {
        LOGGER.info("[SchedulerRegistry] Starting all schedulers...");
        for (EntityThreadScheduler s : java.util.List.copyOf(entitySchedulers.values())) {
            try { s.start(); } catch (Exception e) { LOGGER.error("entity start fail", e); }
        }
        for (ChunkIndependentScheduler s : java.util.List.copyOf(chunkSchedulers.values())) {
            try { s.start(); } catch (Exception e) { LOGGER.error("chunk start fail", e); }
        }
        LOGGER.info("[SchedulerRegistry] Started {} entity, {} chunk schedulers",
                entitySchedulers.size(), chunkSchedulers.size());
    }

    public void stopAll() {
        LOGGER.info("[SchedulerRegistry] Stopping all schedulers...");
        for (EntityThreadScheduler s : java.util.List.copyOf(entitySchedulers.values())) {
            try { s.stop(); } catch (Exception e) { LOGGER.error("entity stop fail", e); }
        }
        for (ChunkIndependentScheduler s : java.util.List.copyOf(chunkSchedulers.values())) {
            try { s.stop(); } catch (Exception e) { LOGGER.error("chunk stop fail", e); }
        }
        entitySchedulers.clear();
        chunkSchedulers.clear();
        LOGGER.info("[SchedulerRegistry] All schedulers stopped");
    }

    public String getGlobalStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("Entity schedulers: ").append(entitySchedulers.size()).append("\n");
        sb.append("Chunk schedulers: ").append(chunkSchedulers.size()).append("\n");
        for (var entry : entitySchedulers.entrySet()) {
            sb.append("  ").append(entry.getKey().dimension().toString()).append(": ")
                .append(entry.getValue().getStats()).append("\n");
        }
        for (var entry : chunkSchedulers.entrySet()) {
            sb.append("  ").append(entry.getKey().dimension().toString()).append(": ")
                .append("workers=").append(entry.getValue().getActiveWorkerCount()).append("\n");
        }
        return sb.toString();
    }
}
