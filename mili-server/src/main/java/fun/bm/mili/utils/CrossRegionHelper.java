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

    private static final AtomicLong eventIdGen = new AtomicLong(0);

    public abstract static class Event {
        public final long id;
        public final RegionizedWorldData sourceRegion;
        public final RegionizedWorldData targetRegion;
        public final long tickStamp;
        public final long createdNanos;

        protected Event(RegionizedWorldData src, RegionizedWorldData tgt, long tick) {
            this.id = eventIdGen.incrementAndGet();
            this.sourceRegion = src;
            this.targetRegion = tgt;
            this.tickStamp = tick;
            this.createdNanos = System.nanoTime();
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

        public EntityDamageSync(UUID sourceUUID, UUID targetUUID, DamageSource ds,
                                RegionizedWorldData src, RegionizedWorldData tgt, long tick) {
            super(src, tgt, tick);
            this.sourceUUID = sourceUUID;
            this.targetUUID = targetUUID;
            this.damageSource = ds;
        }
    }

    public static class EntityEnterRegion extends Event {
        public final UUID entityUUID;

        public EntityEnterRegion(UUID entityUUID, RegionizedWorldData src,
                                 RegionizedWorldData tgt, long tick) {
            super(src, tgt, tick);
            this.entityUUID = entityUUID;
        }
    }

    public static class EntityLeaveRegion extends Event {
        public final UUID entityUUID;

        public EntityLeaveRegion(UUID entityUUID, RegionizedWorldData src,
                                 RegionizedWorldData tgt, long tick) {
            super(src, tgt, tick);
            this.entityUUID = entityUUID;
        }
    }

    private static final int MAX_QUEUE_SIZE = 10000;
    private static final BlockingQueue<Event> inboundQueue =
            new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

    private static final ConcurrentHashMap<RegionizedWorldData, ConcurrentLinkedQueue<Event>>
            pendingByRegion = new ConcurrentHashMap<>();

    private static volatile boolean running = false;

    private static final Thread dispatcherThread = new Thread(() -> {
        running = true;
        com.mojang.logging.LogUtils.getClassLogger().info("[Mili] CrossRegionHelper started");

        while (running) {
            try {
                Event event = inboundQueue.poll(
                        CrossRegionHelperConfig.queuePollTimeoutMs, TimeUnit.MILLISECONDS);

                if (event == null) continue;

                dispatchToTarget(event);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getClassLogger()
                        .warn("[Mili] CrossRegionHelper dispatch error", e);
            }
        }

        com.mojang.logging.LogUtils.getClassLogger().info("[Mili] CrossRegionHelper stopped");
    }, "Mili-CrossRegion");

    static {
        dispatcherThread.setDaemon(true);
        dispatcherThread.setPriority(Thread.NORM_PRIORITY - 1);
        dispatcherThread.start();
    }

    private static void dispatchToTarget(Event event) {
        if (event.targetRegion == null) return;

        ConcurrentLinkedQueue<Event> queue = pendingByRegion.computeIfAbsent(
                event.targetRegion, k -> new ConcurrentLinkedQueue<>());

        int maxPending = CrossRegionHelperConfig.maxPendingEventsPerRegion;

        if (queue.size() >= maxPending) {
            queue.poll();
        }

        queue.add(event);
    }

    public static void submit(Event event) {
        if (!CrossRegionHelperConfig.enabled || event == null) return;

        if (!inboundQueue.offer(event)) {
            com.mojang.logging.LogUtils.getClassLogger()
                    .warn("[Mili] CrossRegionHelper queue full, dropping event");
        }
    }

    public static void submitRedstoneCrossRegion(ServerLevel level, BlockPos pos,
                                                  BlockPos neighbor, Direction dir) {
        if (!CrossRegionHelperConfig.enabled || level == null) return;

        RegionizedWorldData region = level.getCurrentWorldData();
        if (region == null) return;

        submit(new RedstoneSignal(pos, neighbor, dir, region, region, level.getGameTime()));
    }

    public static void submitDamageCrossRegion(LivingEntity source, LivingEntity target,
                                                DamageSource damageSource, long tick) {
        if (!CrossRegionHelperConfig.enabled || source == null ||
                target == null || damageSource == null) return;

        RegionizedWorldData srcRegion = source.level().getCurrentWorldData();
        RegionizedWorldData tgtRegion = target.level().getCurrentWorldData();

        if (srcRegion == null || tgtRegion == null) return;
        if (srcRegion == tgtRegion) return;

        submit(new EntityDamageSync(source.getUUID(), target.getUUID(),
                damageSource, srcRegion, tgtRegion, tick));
    }

    public static ConcurrentLinkedQueue<Event> consumePending(RegionizedWorldData target) {
        if (!CrossRegionHelperConfig.enabled || target == null) return null;
        return pendingByRegion.remove(target);
    }

    public static ConcurrentLinkedQueue<Event> onRegionTick(ServerLevel level,
                                                            RegionizedWorldData data) {
        if (!CrossRegionHelperConfig.enabled || data == null) return null;
        return consumePending(data);
    }

    public static int pendingCount(RegionizedWorldData region) {
        ConcurrentLinkedQueue<Event> q = pendingByRegion.get(region);
        return q != null ? q.size() : 0;
    }

    public static int inboundQueueSize() { return inboundQueue.size(); }

    public static int totalPendingAcrossRegions() {
        int total = 0;
        for (ConcurrentLinkedQueue<Event> q : pendingByRegion.values()) {
            total += q.size();
        }
        return total;
    }

    public static void onRegionUnload(RegionizedWorldData data) {
        if (data != null) {
            ConcurrentLinkedList<Event> removed = pendingByRegion.remove(data);
            if (removed != null && !removed.isEmpty()) {
                com.mojang.logging.LogUtils.getClassLogger()
                        .debug("[Mili] Dropped {} events for unloaded region", removed.size());
            }
        }
    }

    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("inbound_queue_size", inboundQueue.size());
        stats.put("tracked_regions", pendingByRegion.size());
        stats.put("total_pending_events", totalPendingAcrossRegions());
        stats.put("running", running);

        long totalEventsProcessed = 0;
        for (ConcurrentLinkedQueue<Event> q : pendingByRegion.values()) {
            totalEventsProcessed += q.size();
        }
        stats.put("total_events_in_queues", totalEventsProcessed);

        return stats;
    }

    public static void shutdown() {
        running = false;
        dispatcherThread.interrupt();

        try {
            dispatcherThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        inboundQueue.clear();
        pendingByRegion.clear();

        com.mojang.logging.LogUtils.getClassLogger().info("[Mili] CrossRegionHelper shutdown");
    }
}