package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.CrossRegionHelperConfig;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;

public class CrossRegionHelper {

    private static final AtomicLong eventCounter = new AtomicLong(0);

    public static abstract class Event {
        public final long id = eventCounter.incrementAndGet();
        public final RegionizedWorldData sourceRegion;
        public final RegionizedWorldData targetRegion;
        public final long tickStamp;

        public Event(RegionizedWorldData sourceRegion, RegionizedWorldData targetRegion, long tickStamp) {
            this.sourceRegion = sourceRegion;
            this.targetRegion = targetRegion;
            this.tickStamp = tickStamp;
        }
    }

    public static class RedstoneSignal extends Event {
        public final BlockPos pos;
        public final BlockPos neighbor;
        public final Direction dir;

        public RedstoneSignal(BlockPos pos, BlockPos neighbor, Direction dir,
                              RegionizedWorldData src, RegionizedWorldData tgt, long tick) {
            super(src, tgt, tick);
            this.pos = pos;
            this.neighbor = neighbor;
            this.dir = dir;
        }
    }

    public static class EntityDamageSync extends Event {
        public final UUID sourceUUID;
        public final UUID targetUUID;
        public final DamageSource damageSource;

        public EntityDamageSync(UUID sourceUUID, UUID targetUUID, DamageSource damageSource,
                                RegionizedWorldData src, RegionizedWorldData tgt, long tick) {
            super(src, tgt, tick);
            this.sourceUUID = sourceUUID;
            this.targetUUID = targetUUID;
            this.damageSource = damageSource;
        }
    }

    public static class EntityEnterRegion extends Event {
        public final UUID entityUUID;
        public EntityEnterRegion(UUID entityUUID, RegionizedWorldData src, RegionizedWorldData tgt, long tick) {
            super(src, tgt, tick);
            this.entityUUID = entityUUID;
        }
    }

    public static class EntityLeaveRegion extends Event {
        public final UUID entityUUID;
        public EntityLeaveRegion(UUID entityUUID, RegionizedWorldData src, RegionizedWorldData tgt, long tick) {
            super(src, tgt, tick);
            this.entityUUID = entityUUID;
        }
    }

    private static final LinkedBlockingQueue<Event> eventQueue = new LinkedBlockingQueue<>();
    private static final ConcurrentHashMap<RegionizedWorldData, ConcurrentLinkedQueue<Event>> pendingByRegion = new ConcurrentHashMap<>();
    private static volatile boolean running = false;

    private static final Thread helperThread = new Thread(() -> {
        running = true;
        while (running) {
            try {
                Event event = eventQueue.poll(CrossRegionHelperConfig.queuePollTimeoutMs, TimeUnit.MILLISECONDS);
                if (event == null) continue;
                ConcurrentLinkedQueue<Event> queue = pendingByRegion.computeIfAbsent(
                    event.targetRegion, k -> new ConcurrentLinkedQueue<>());
                int max = CrossRegionHelperConfig.maxPendingEventsPerRegion;
                while (queue.size() >= max) { queue.poll(); }
                queue.add(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {}
        }
    }, "CrossRegion-Helper");

    static {
        helperThread.setDaemon(true);
        helperThread.start();
    }

    public static void submit(Event event) {
        if (!CrossRegionHelperConfig.enabled) return;
        eventQueue.add(event);
    }

    public static void submitRedstoneCrossRegion(ServerLevel sl, BlockPos pos, BlockPos neighbor, Direction dir) {
        if (!CrossRegionHelperConfig.enabled || sl == null) return;
        RegionizedWorldData s = sl.getCurrentWorldData();
        if (s == null) return;
        submit(new RedstoneSignal(pos, neighbor, dir, s, s, sl.getGameTime()));
    }

    public static void submitDamageCrossRegion(LivingEntity src, LivingEntity tgt, DamageSource ds, long tick) {
        if (!CrossRegionHelperConfig.enabled || src == null || tgt == null || ds == null) return;
        RegionizedWorldData s = src.level().getCurrentWorldData();
        RegionizedWorldData t = tgt.level().getCurrentWorldData();
        if (s == null || t == null) return;
        if (s != t) {
            submit(new EntityDamageSync(src.getUUID(), tgt.getUUID(), ds, s, t, tick));
        }
    }

    public static ConcurrentLinkedQueue<Event> consumePending(RegionizedWorldData targetRegion) {
        if (!CrossRegionHelperConfig.enabled) return null;
        return pendingByRegion.remove(targetRegion);
    }

    public static ConcurrentLinkedQueue<Event> onRegionTick(ServerLevel level, RegionizedWorldData data) {
        if (!CrossRegionHelperConfig.enabled || data == null) return null;
        return consumePending(data);
    }

    public static int pendingCount(RegionizedWorldData targetRegion) {
        ConcurrentLinkedQueue<Event> q = pendingByRegion.get(targetRegion);
        return q == null ? 0 : q.size();
    }

    public static int inboundQueueSize() { return eventQueue.size(); }

    public static void onRegionUnload(RegionizedWorldData data) {
        if (data != null) pendingByRegion.remove(data);
    }

    public static void shutdown() {
        running = false;
        helperThread.interrupt();
    }
}
