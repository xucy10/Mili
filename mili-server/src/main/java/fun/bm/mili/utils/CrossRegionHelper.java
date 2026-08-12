package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.CrossRegionHelperConfig;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class CrossRegionHelper {

    private static final AtomicLong eventIdGen = new AtomicLong(0);
    private static final LongAdder eventsProcessed = new LongAdder();
    private static final LongAdder eventsDropped = new LongAdder();
    private static final LongAdder batchesDispatched = new LongAdder();
    private static final LongAdder regionQueueOverflows = new LongAdder();

    private static final int BATCH_SIZE = 64;

    public abstract static class Event {
        public final long id;
        public final RegionizedWorldData sourceRegion;
        public final RegionizedWorldData targetRegion;
        public final long tickStamp;
        // Mili start - task UUID for cross-region parameter passing traceability
        public final UUID taskUuid;
        // Mili end

        protected Event(RegionizedWorldData src, RegionizedWorldData tgt, long tick) {
            this.id = eventIdGen.incrementAndGet();
            this.sourceRegion = src;
            this.targetRegion = tgt;
            this.tickStamp = tick;
            // Mili start - allocate UUID for this cross-region event
            this.taskUuid = RegionTaskIdRegistry.allocateAndRegister("cross-region-event", src);
            // Mili end
        }

        // Mili start - constructor for events with a pre-existing task UUID (e.g. passthrough from RegionBalancer)
        protected Event(RegionizedWorldData src, RegionizedWorldData tgt, long tick, UUID existingTaskUuid) {
            this.id = eventIdGen.incrementAndGet();
            this.sourceRegion = src;
            this.targetRegion = tgt;
            this.tickStamp = tick;
            this.taskUuid = existingTaskUuid;
        }
        // Mili end
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
    private static volatile long lastDropWarning = 0;

    private static Thread dispatcherThread;

    public static void init() {
        if (running) return;
        running = true;

        dispatcherThread = new Thread(() -> {
        com.mojang.logging.LogUtils.getClassLogger().info("[Mili] CrossRegionHelper started");

        while (running) {
            try {
                Event event = inboundQueue.poll(
                        CrossRegionHelperConfig.queuePollTimeoutMs, TimeUnit.MILLISECONDS);

                if (event == null) continue;

                dispatchToTarget(event);

                int batchCount = 1;
                while (batchCount < BATCH_SIZE) {
                    Event next = inboundQueue.poll();
                    if (next == null) break;
                    dispatchToTarget(next);
                    batchCount++;
                }
                if (batchCount > 1) {
                    batchesDispatched.increment();
                }
                eventsProcessed.add(batchCount);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable e) {
                // Mili start - fix: catch Throwable (not just Exception) to prevent dispatcher thread death on Error (e.g. StackOverflowError)
                com.mojang.logging.LogUtils.getClassLogger()
                        .warn("[Mili] CrossRegionHelper dispatch error", e);
            }
        }

        com.mojang.logging.LogUtils.getClassLogger().info("[Mili] CrossRegionHelper stopped");
    }, "Mili-CrossRegion");

        dispatcherThread.setDaemon(true);
        dispatcherThread.setPriority(Thread.NORM_PRIORITY - 1);
        dispatcherThread.start();
    }

    private static void dispatchToTarget(Event event) {
        if (event.targetRegion == null) {
            // Mili start - fix: unregister UUID when event has no target region
            RegionTaskIdRegistry.unregister(event.taskUuid);
            // Mili end
            return;
        }

        ConcurrentLinkedQueue<Event> queue = pendingByRegion.computeIfAbsent(
                event.targetRegion, k -> new ConcurrentLinkedQueue<>());

        int maxPending = CrossRegionHelperConfig.maxPendingEventsPerRegion;

        if (queue.size() >= maxPending) {
            // Mili start - fix: unregister UUID for the evicted event before dropping it
            Event evicted = queue.poll();
            if (evicted != null) {
                RegionTaskIdRegistry.unregister(evicted.taskUuid);
            }
            // Mili end
            regionQueueOverflows.increment();
        }

        queue.add(event);
    }

    public static void submit(Event event) {
        if (!CrossRegionHelperConfig.enabled || event == null) return;

        if (!inboundQueue.offer(event)) {
            // Mili start - fix: unregister UUID for the dropped event to prevent registry leak
            RegionTaskIdRegistry.unregister(event.taskUuid);
            // Mili end
            eventsDropped.increment();
            long now = System.currentTimeMillis();
            if (now - lastDropWarning > 5000) {
                lastDropWarning = now;
                com.mojang.logging.LogUtils.getClassLogger()
                        .warn("[Mili] CrossRegionHelper queue full, dropping events (suppressing for 5s)");
            }
        }
    }

    public static void submitRedstoneCrossRegion(ServerLevel level, BlockPos pos,
                                                  BlockPos neighbor, Direction dir) {
        if (!CrossRegionHelperConfig.enabled || level == null) return;

        RegionizedWorldData srcRegion = level.getCurrentWorldData();
        if (srcRegion == null) return;

        RegionizedWorldData tgtRegion = level.getCurrentWorldData();
        if (tgtRegion == null) return;
        if (srcRegion == tgtRegion) return; // not cross-region, skip

        submit(new RedstoneSignal(pos, neighbor, dir, srcRegion, tgtRegion, level.getGameTime()));
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
        ConcurrentLinkedQueue<Event> queue = pendingByRegion.remove(target);
        // Mili start - unregister task UUIDs for consumed events
        if (queue != null) {
            for (Event event : queue) {
                RegionTaskIdRegistry.unregister(event.taskUuid);
            }
        }
        // Mili end
        return queue;
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

    // Mili start - find events associated with a task UUID
    /**
     * Find the source region for a given task UUID.
     * Useful for region schedulers to trace where a cross-region event originated.
     */
    public static RegionizedWorldData findSourceRegionForTaskUuid(UUID taskUuid) {
        if (taskUuid == null) return null;
        // Check inbound queue
        for (Event event : inboundQueue) {
            if (taskUuid.equals(event.taskUuid)) {
                return event.sourceRegion;
            }
        }
        // Check pending by region
        for (ConcurrentLinkedQueue<Event> queue : pendingByRegion.values()) {
            for (Event event : queue) {
                if (taskUuid.equals(event.taskUuid)) {
                    return event.sourceRegion;
                }
            }
        }
        return null;
    }
    // Mili end

    public static int totalPendingAcrossRegions() {
        int total = 0;
        for (ConcurrentLinkedQueue<Event> q : pendingByRegion.values()) {
            total += q.size();
        }
        return total;
    }

    public static void onRegionUnload(RegionizedWorldData data) {
        if (data != null) {
            ConcurrentLinkedQueue<Event> removed = pendingByRegion.remove(data);
            if (removed != null && !removed.isEmpty()) {
                // Mili start - unregister task UUIDs for dropped events
                for (Event event : removed) {
                    RegionTaskIdRegistry.unregister(event.taskUuid);
                }
                // Mili end
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
        stats.put("events_processed", eventsProcessed.sum());
        stats.put("events_dropped", eventsDropped.sum());
        stats.put("batches_dispatched", batchesDispatched.sum());
        stats.put("region_queue_overflows", regionQueueOverflows.sum());

        long totalEventsProcessed = 0;
        for (ConcurrentLinkedQueue<Event> q : pendingByRegion.values()) {
            totalEventsProcessed += q.size();
        }
        stats.put("total_events_in_queues", totalEventsProcessed);

        return stats;
    }

    public static void shutdown() {
        running = false;
        if (dispatcherThread != null) {
            dispatcherThread.interrupt();
            try {
                dispatcherThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Mili start - unregister task UUIDs for all remaining events
        for (Event event : inboundQueue) {
            RegionTaskIdRegistry.unregister(event.taskUuid);
        }
        for (ConcurrentLinkedQueue<Event> queue : pendingByRegion.values()) {
            for (Event event : queue) {
                RegionTaskIdRegistry.unregister(event.taskUuid);
            }
        }
        // Mili end

        inboundQueue.clear();
        pendingByRegion.clear();

        com.mojang.logging.LogUtils.getClassLogger().info("[Mili] CrossRegionHelper shutdown");
    }
}