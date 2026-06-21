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

public final class EntityMigrationBus {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ConcurrentMap<Long, Queue<EntityMigration>> migrationQueue = new ConcurrentHashMap<>();
    private final AtomicInteger pendingMigrations = new AtomicInteger(0);

    public record EntityMigration(Entity entity, int targetChunkX, int targetChunkZ) {}

    public void enqueueMigration(Entity entity, int targetChunkX, int targetChunkZ) {
        if (entity == null || entity.isRemoved()) return;
        long key = ((long) targetChunkX << 32) | (targetChunkZ & 0xFFFFFFFFL);
        migrationQueue.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>())
            .add(new EntityMigration(entity, targetChunkX, targetChunkZ));
        pendingMigrations.incrementAndGet();
    }

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
                    if (entity == null) continue;

                    entity.getBukkitEntity().getScheduler().runDelayed(
                        MinecraftInternalPlugin.INSTANCE,
                        (io.papermc.paper.threadedregions.scheduler.ScheduledTask st) -> {
                            if (entity.isRemoved() || entity.level() != level) return;
                        },
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
