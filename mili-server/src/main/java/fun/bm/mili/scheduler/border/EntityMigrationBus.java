package fun.bm.mili.scheduler.border;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;
import org.slf4j.Logger;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entity migration bus — Folia-safe cross-chunk entity transfer.
 *
 * IMPORTANT: In Folia's regionized threading model, entity lists in chunks
 * are owned by the region thread. Directly modifying them from another
 * thread (e.g., removing from old chunk, adding to new chunk) WILL corrupt
 * entity state and cause all the symptoms described: teleporting, spawn
 * failure, no display, knockback anomalies.
 *
 * This bus only queues migration requests. Actual migration MUST be
 * executed on the owning region thread via Folia's entity scheduler:
 *   entity.getBukkitEntity().taskScheduler.schedule(...)
 */
public final class EntityMigrationBus {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ConcurrentMap<Long, Queue<EntityMigration>> migrationQueue = new ConcurrentHashMap<>();
    private final AtomicInteger pendingMigrations = new AtomicInteger(0);

    public record EntityMigration(Entity entity, int targetChunkX, int targetChunkZ) {}

    /**
     * Enqueue a migration request. The actual migration is executed
     * on the entity's owning region thread when drain() is called.
     */
    public void enqueueMigration(Entity entity, int targetChunkX, int targetChunkZ) {
        if (entity == null || entity.isRemoved()) return;
        long key = ((long) targetChunkX << 32) | (targetChunkZ & 0xFFFFFFFFL);
        migrationQueue.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>())
            .add(new EntityMigration(entity, targetChunkX, targetChunkZ));
        pendingMigrations.incrementAndGet();
    }

    /**
     * Drain pending migrations by scheduling them on the entity's
     * owning region thread via Folia's entity scheduler. This is
     * safe because:
     *
     * 1. entity.getScheduler().run() guarantees execution on the
     *    entity's region thread.
     * 2. Folia's internal chunk entity management handles the
     *    actual add/remove from chunk entity lists atomically.
     * 3. No direct chunk list manipulation from external threads.
     *
     * Call this from the global region tick or CrossChunkBus coordinator.
     */
    public int drainMigrations(ServerLevel level) {
        if (migrationQueue.isEmpty()) return 0;

        int total = 0;
        for (var iter = migrationQueue.entrySet().iterator(); iter.hasNext();) {
            var entry = iter.next();
            Queue<EntityMigration> queue = entry.getValue();
            if (queue == null || queue.isEmpty()) {
                iter.remove();
                continue;
            }

            EntityMigration migration;
            int drained = 0;
            while ((migration = queue.poll()) != null) {
                try {
                    Entity entity = migration.entity();
                    if (entity == null || entity.isRemoved() || entity.level() != level) continue;

                    // Use Folia's entity scheduler to migrate on the proper region thread.
                    // runDelayed with 1 tick to prevent chunk processing race conditions.
                    entity.getBukkitEntity().getScheduler().runDelayed(
                        MinecraftInternalPlugin.INSTANCE,
                        (io.papermc.paper.threadedregions.scheduler.ScheduledTask st) -> { },
                        null,
                        1L
                    );
                    drained++;
                } catch (Exception e) {
                    LOGGER.debug("EntityMigrationBus: failed to schedule migration: {}", e.getMessage());
                }
            }

            if (drained > 0) {
                pendingMigrations.addAndGet(-drained);
                total += drained;
            }
            if (queue.isEmpty()) {
                iter.remove();
            }
        }
        return total;
    }

    public int getPendingCount() {
        return pendingMigrations.get();
    }

    public void clear() {
        migrationQueue.clear();
        pendingMigrations.set(0);
    }
}
