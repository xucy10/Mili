package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.CrossRegionHelperConfig;
import fun.bm.mili.scheduler.CrossRegionTransaction;
import fun.bm.mili.scheduler.RegionIdRegistry;
import fun.bm.mili.scheduler.RegionResolver;
import fun.bm.mili.scheduler.RegionRuntime;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Cross-region event transport.
 * <p>
 * fix.md §10: the previous {@code submitRedstoneCrossRegion} resolved both the source and
 * the target with {@code level.getCurrentWorldData()}, so {@code srcRegion == tgtRegion}
 * was always true and the method silently did nothing.  Source and target are now
 * resolved independently:
 * <pre>
 *     Source Position -> Source Region
 *     Target Position -> Regionizer  -> Target Region
 * </pre>
 * <p>
 * fix.md §11: every cross-region operation is an explicit {@link CrossRegionTransaction}
 * with PREPARE / ENQUEUED / EXECUTING / COMMITTED (or FAILED / CANCELLED).
 * <p>
 * fix.md §8: routing is keyed by the stable region id, never by
 * {@code System.identityHashCode}.
 */
public class CrossRegionHelper {

    private static final AtomicLong eventIdGen = new AtomicLong(0);
    private static final LongAdder eventsProcessed = new LongAdder();
    private static final LongAdder eventsDropped = new LongAdder();
    private static final LongAdder batchesDispatched = new LongAdder();
    private static final LongAdder regionQueueOverflows = new LongAdder();
    private static final LongAdder localSkipped = new LongAdder();

    private static final int BATCH_SIZE = 64;

    public abstract static class Event {
        public final long id;
        public final long sourceRegionId;
        public final long targetRegionId;
        public final RegionizedWorldData sourceRegion;
        public final RegionizedWorldData targetRegion;
        public final long tickStamp;
        /** Task UUID for cross-region parameter passing traceability. */
        public final UUID taskUuid;

        protected Event(RegionizedWorldData src, RegionizedWorldData tgt, long tick) {
            this.id = eventIdGen.incrementAndGet();
            this.sourceRegion = src;
            this.targetRegion = tgt;
            this.sourceRegionId = RegionIdRegistry.idOf(src);
            this.targetRegionId = RegionIdRegistry.idOf(tgt);
            this.tickStamp = tick;
            this.taskUuid = RegionTaskIdRegistry.allocateAndRegister("cross-region-event", src);
        }

        /** Constructor for events carrying a pre-existing task UUID (passthrough). */
        protected Event(RegionizedWorldData src, RegionizedWorldData tgt, long tick, UUID existingTaskUuid) {
            this.id = eventIdGen.incrementAndGet();
            this.sourceRegion = src;
            this.targetRegion = tgt;
            this.sourceRegionId = RegionIdRegistry.idOf(src);
            this.targetRegionId = RegionIdRegistry.idOf(tgt);
            this.tickStamp = tick;
            this.taskUuid = existingTaskUuid;
        }

        public boolean isCrossRegion() {
            return sourceRegionId != 0L && targetRegionId != 0L && sourceRegionId != targetRegionId;
        }

        /** Wrap this event into a transaction executed on the target's owning thread. */
        public CrossRegionTransaction toTransaction(Runnable operation) {
            return CrossRegionTransaction.create(sourceRegion, targetRegion, operation);
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

    /** Pending events keyed by the stable target region id (fix.md §8). */
    private static final ConcurrentHashMap<Long, ConcurrentLinkedQueue<Event>>
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
                            CrossRegionHelperConfig.queuePollTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);

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
                    // fix: catch Throwable so an Error cannot permanently kill the dispatcher.
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
        if (event == null) return;

        if (event.targetRegion == null || !event.isCrossRegion()) {
            // Same region (or unresolvable): nothing to transport, run locally.
            localSkipped.increment();
            RegionTaskIdRegistry.unregister(event.taskUuid);
            return;
        }

        ConcurrentLinkedQueue<Event> queue = pendingByRegion.computeIfAbsent(
                event.targetRegionId, k -> new ConcurrentLinkedQueue<>());

        int maxPending = CrossRegionHelperConfig.maxPendingEventsPerRegion;

        if (queue.size() >= maxPending) {
            Event evicted = queue.poll();
            if (evicted != null) {
                RegionTaskIdRegistry.unregister(evicted.taskUuid);
            }
            regionQueueOverflows.increment();
        }

        queue.add(event);
    }

    public static void submit(Event event) {
        if (!CrossRegionHelperConfig.enabled || event == null) return;

        if (!inboundQueue.offer(event)) {
            RegionTaskIdRegistry.unregister(event.taskUuid);
            eventsDropped.increment();
            long now = System.currentTimeMillis();
            if (now - lastDropWarning > 5000) {
                lastDropWarning = now;
                com.mojang.logging.LogUtils.getClassLogger()
                        .warn("[Mili] CrossRegionHelper queue full, dropping events (suppressing for 5s)");
            }
        }
    }

    /**
     * Redstone crossing a region boundary.
     * <p>
     * The target is resolved from the <b>neighbour</b> position through the regionizer,
     * which is what makes this actually cross-region (fix.md §10).
     */
    public static void submitRedstoneCrossRegion(ServerLevel level, BlockPos pos,
                                                 BlockPos neighbor, Direction dir) {
        if (!CrossRegionHelperConfig.enabled || level == null || neighbor == null) return;

        RegionizedWorldData srcRegion = level.getCurrentWorldData();
        if (srcRegion == null) return;

        Object resolved = RegionResolver.regionAtBlock(level, neighbor);
        if (!(resolved instanceof RegionizedWorldData tgtRegion)) return;
        if (srcRegion == tgtRegion) return; // genuinely not cross-region

        submit(new RedstoneSignal(pos, neighbor, dir, srcRegion, tgtRegion, level.getGameTime()));
    }

    /**
     * Damage applied across a region boundary.
     * The target region is resolved from the target entity's own position.
     */
    public static void submitDamageCrossRegion(LivingEntity source, LivingEntity target,
                                               DamageSource damageSource, long tick) {
        if (!CrossRegionHelperConfig.enabled || source == null ||
                target == null || damageSource == null) return;

        RegionizedWorldData srcRegion = source.level().getCurrentWorldData();
        if (srcRegion == null) return;

        Object resolved = resolveRegionOf(target.level(), target.blockPosition());
        if (!(resolved instanceof RegionizedWorldData tgtRegion)) return;
        if (srcRegion == tgtRegion) return;

        submit(new EntityDamageSync(source.getUUID(), target.getUUID(),
                damageSource, srcRegion, tgtRegion, tick));
    }

    /** Resolve the region owning {@code pos}, when the level exposes a regionizer. */
    private static Object resolveRegionOf(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            return RegionResolver.regionAtBlock(serverLevel, pos);
        }
        return null;
    }

    /**
     * Consume pending events addressed to a region, keyed by its stable id.
     */
    public static ConcurrentLinkedQueue<Event> consumePending(RegionizedWorldData target) {
        if (!CrossRegionHelperConfig.enabled || target == null) return null;
        return consumePending(RegionIdRegistry.idOf(target));
    }

    public static ConcurrentLinkedQueue<Event> consumePending(long targetRegionId) {
        if (!CrossRegionHelperConfig.enabled || targetRegionId == 0L) return null;
        ConcurrentLinkedQueue<Event> queue = pendingByRegion.remove(targetRegionId);
        if (queue != null) {
            for (Event event : queue) {
                RegionTaskIdRegistry.unregister(event.taskUuid);
            }
        }
        return queue;
    }

    /** Called from the region tick hook (patch 0111). */
    public static ConcurrentLinkedQueue<Event> onRegionTick(ServerLevel level,
                                                            RegionizedWorldData data) {
        if (!CrossRegionHelperConfig.enabled || data == null) return null;
        return consumePending(data);
    }

    public static int pendingCount(RegionizedWorldData region) {
        if (region == null) return 0;
        return pendingCount(RegionIdRegistry.peek(region));
    }

    public static int pendingCount(long regionId) {
        ConcurrentLinkedQueue<Event> q = pendingByRegion.get(regionId);
        return q != null ? q.size() : 0;
    }

    public static int inboundQueueSize() {
        return inboundQueue.size();
    }

    /** Find the source region for a task UUID (traceability helper). */
    public static RegionizedWorldData findSourceRegionForTaskUuid(UUID taskUuid) {
        if (taskUuid == null) return null;
        for (Event event : inboundQueue) {
            if (taskUuid.equals(event.taskUuid)) {
                return event.sourceRegion;
            }
        }
        for (ConcurrentLinkedQueue<Event> queue : pendingByRegion.values()) {
            for (Event event : queue) {
                if (taskUuid.equals(event.taskUuid)) {
                    return event.sourceRegion;
                }
            }
        }
        return null;
    }

    public static int totalPendingAcrossRegions() {
        int total = 0;
        for (ConcurrentLinkedQueue<Event> q : pendingByRegion.values()) {
            total += q.size();
        }
        return total;
    }

    /**
     * Region teardown: drop pending work and cancel transactions targeting this region
     * so nothing is left pointing at a dead region (fix.md §9 / §11).
     */
    public static void onRegionUnload(RegionizedWorldData data) {
        if (data == null) return;
        long regionId = RegionIdRegistry.peek(data);
        if (regionId != 0L) {
            CrossRegionTransaction.cancelTargeting(regionId);
            ConcurrentLinkedQueue<Event> removed = pendingByRegion.remove(regionId);
            if (removed != null && !removed.isEmpty()) {
                for (Event event : removed) {
                    RegionTaskIdRegistry.unregister(event.taskUuid);
                }
                com.mojang.logging.LogUtils.getClassLogger()
                        .debug("[Mili] Dropped {} events for unloaded region", removed.size());
            }
        }
        RegionIdRegistry.remove(data);
    }

    /** Drain pending events for a region runtime, converting them into transactions. */
    public static List<CrossRegionTransaction> drainAsTransactions(RegionRuntime runtime,
                                                                   java.util.function.Function<Event, Runnable> binder) {
        List<CrossRegionTransaction> result = new ArrayList<>();
        if (runtime == null || binder == null) return result;
        ConcurrentLinkedQueue<Event> queue = consumePending(runtime.regionId());
        if (queue == null) return result;
        for (Event event : queue) {
            if (event.targetRegionId != runtime.regionId()) continue;
            CrossRegionTransaction tx = CrossRegionTransaction.create(
                    event.sourceRegion, event.targetRegion, binder.apply(event));
            tx.enqueue();
            result.add(tx);
        }
        return result;
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
        stats.put("local_skipped", localSkipped.sum());
        stats.putAll(CrossRegionTransaction.getStats());
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

        for (Event event : inboundQueue) {
            RegionTaskIdRegistry.unregister(event.taskUuid);
        }
        for (ConcurrentLinkedQueue<Event> queue : pendingByRegion.values()) {
            for (Event event : queue) {
                RegionTaskIdRegistry.unregister(event.taskUuid);
            }
        }

        inboundQueue.clear();
        pendingByRegion.clear();

        com.mojang.logging.LogUtils.getClassLogger().info("[Mili] CrossRegionHelper shutdown");
    }
}
