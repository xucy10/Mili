package fun.bm.mili.scheduler.border;

import com.mojang.logging.LogUtils;
import fun.bm.mili.scheduler.ChunkWorker;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class EntityMigrationBus {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ConcurrentMap<Long, Deque<EntityMigration>> migrationQueue = new ConcurrentHashMap<>();
    private final AtomicInteger pendingMigrations = new AtomicInteger(0);

    public record EntityMigration(Entity entity, int targetChunkX, int targetChunkZ) {}

    public void enqueueMigration(Entity entity, int targetChunkX, int targetChunkZ) {
        long key = asLong(targetChunkX, targetChunkZ);
        migrationQueue.computeIfAbsent(key, k -> new ArrayDeque<>())
            .add(new EntityMigration(entity, targetChunkX, targetChunkZ));
        pendingMigrations.incrementAndGet();
    }

    public void processMigrations(ChunkWorker worker, ServerLevel level) {
        long key = asLong(worker.getChunkX(), worker.getChunkZ());
        Deque<EntityMigration> migrations = migrationQueue.get(key);
        if (migrations == null || migrations.isEmpty()) return;

        LevelChunk chunk = worker.getChunk();
        if (chunk == null || !chunk.isLoaded()) return;

        int processed = 0;
        EntityMigration migration;
        while ((migration = migrations.poll()) != null) {
            try {
                Entity entity = migration.entity();
                if (entity == null || entity.isRemoved() || entity.level() != level) continue;

                int oldChunkX = entity.chunkPosition().x;
                int oldChunkZ = entity.chunkPosition().z;
                if (oldChunkX == migration.targetChunkX() && oldChunkZ == migration.targetChunkZ()) {
                    continue;
                }

                ChunkAccess oldChunk = level.getChunk(oldChunkX, oldChunkZ);
                if (oldChunk instanceof LevelChunk oldLevelChunk) {
                    oldLevelChunk.getEntities().remove(entity);
                }
                chunk.addEntity(entity);
                processed++;
            } catch (Exception e) {
                LOGGER.error("EntityMigrationBus: failed to migrate entity", e);
            }
        }
        if (processed > 0) {
            pendingMigrations.addAndGet(-processed);
        }
    }

    public int getPendingCount() {
        return pendingMigrations.get();
    }

    public void clear() {
        migrationQueue.clear();
        pendingMigrations.set(0);
    }

    private static long asLong(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }
}
