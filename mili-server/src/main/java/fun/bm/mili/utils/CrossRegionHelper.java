package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.CrossRegionHelperConfig;
import fun.bm.mili.scheduler.CrossRegionTransaction;
import fun.bm.mili.scheduler.EntityScheduler;
import fun.bm.mili.scheduler.RegionIdRegistry;
import fun.bm.mili.scheduler.RegionResolver;
import fun.bm.mili.scheduler.SchedulerLog;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Cross-region event routing.
 *
 * <h2>What was wrong</h2>
 *
 * <p><b>1. The target region was never resolved (fix.md §12).</b> The old body of
 * {@link #submitRedstoneCrossRegion} read:
 *
 * <pre>
 *     RegionizedWorldData srcRegion = level.getCurrentWorldData();
 *     RegionizedWorldData tgtRegion = level.getCurrentWorldData();   // same expression
 *     if (srcRegion == tgtRegion) return;                            // therefore always true
 * </pre>
 *
 * Both sides were the <em>current</em> region, so the guard was always true and the
 * method could never emit anything. The {@code neighbor} coordinate — the one piece
 * of information that identifies the target — was stored on the event and never
 * read. Source and target are now resolved independently: the source from the
 * executing context, the target from the neighbour's own coordinates via the
 * regioniser.
 *
 * <p><b>2. Events pinned dead regions.</b> {@code pendingByRegion} was a
 * {@code ConcurrentHashMap<RegionizedWorldData, ...>} — a <em>strong</em> reference
 * to the region as a map key. {@code onRegionUnload} existed to remove them but had
 * no callers anywhere in the repository, so any region that ever received a
 * cross-region event stayed reachable, dragging its whole
 * {@code RegionizedWorldData} graph (neighbour updater, block ticks, wire handler,
 * …) with it. Routing is now keyed by the stable {@link RegionIdRegistry} id, so
 * the queue holds no reference to any region.
 *
 * <p><b>3. Nothing tracked delivery.</b> An event dropped on a full queue was
 * indistinguishable from one that was applied. Each routed event carries a
 * {@link CrossRegionTransaction}, so a stranded event is visible and a region being
 * destroyed can sever everything aimed at it.
 *
 * <h2>Scope</h2>
 * This class is responsible for "produced → handed to the target region's tick".
 * Applying the payload is the target region's business. A transaction therefore
 * commits when the event is drained by {@link #consumePending}, not when the world
 * mutation lands — that keeps the tracked span honest instead of claiming credit
 * for work this class does not do.
 */
public class CrossRegionHelper {

    private static final AtomicLong eventIdGen = new AtomicLong(0);
    private static final LongAdder eventsProcessed = new LongAdder();
    private static final LongAdder eventsDropped = new LongAdder();
    private static final LongAdder batchesDispatched = new LongAdder();
    private static final LongAdder regionQueueOverflows = new LongAdder();
    private static final LongAdder localSkipped = new LongAdder();
    /** Target positions that could not be resolved. Never silently misrouted. */
    private static final LongAdder unresolvedTargets = new LongAdder();

    private static final int BATCH_SIZE = 64;

    public abstract static class Event {
        public final long id;
        /** Stable ids, not region references: nothing here may pin a region. */
        public final long sourceRegionId;
        public final long targetRegionId;
        public final long tickStamp;
        public final UUID taskUuid;
        /** Routing transaction, committed when the event reaches the target region. */
        public final CrossRegionTransaction transaction;

        protected Event(long srcRegionId, long tgtRegionId, long tick) {
            this.id = eventIdGen.incrementAndGet();
            this.sourceRegionId = srcRegionId;
            this.targetRegionId = tgtRegionId;
            this.tickStamp = tick;
            this.taskUuid = RegionTaskIdRegistry.allocateAndRegister("cross-region-event", null);
            this.transaction = CrossRegionTransaction.begin(srcRegionId, tgtRegionId, "cross-region-event");
            if (this.transaction != null) {
                this.transaction.markEnqueued();
            }
        }
    }

    public static class RedstoneSignal extends Event {
        // Coordinates rather than BlockPos: the event survives on the dispatcher
        // thread, and an immutable snapshot of a position is what a cross-thread
        // handoff should carry.
        public final int posX;
        public final int posY;
        public final int posZ;
        public final int neighborX;
        public final int neighborY;
        public final int neighborZ;
        public final Direction dir;

        public RedstoneSignal(BlockPos pos, BlockPos neighbor, Direction dir,
                              long srcRegionId, long tgtRegionId, long tick) {
            super(srcRegionId, tgtRegionId, tick);
            this.posX = pos.getX();
            this.posY = pos.getY();
            this.posZ = pos.getZ();
            this.neighborX = neighbor.getX();
            this.neighborY = neighbor.getY();
            this.neighborZ = neighbor.getZ();
            this.dir = dir;
        }

        public BlockPos pos() {
            return new BlockPos(posX, posY, posZ);
        }

        public BlockPos neighbor() {
            return new BlockPos(neighborX, neighborY, neighborZ);
        }
    }

    public static class EntityDamageSync extends Event {
        public final UUID sourceUUID;
        public final UUID targetUUID;
        public final DamageSource damageSource;

        public EntityDamageSync(UUID sourceUUID, UUID targetUUID, DamageSource ds,
                                long srcRegionId, long tgtRegionId, long tick) {
            super(srcRegionId, tgtRegionId, tick);
            this.sourceUUID = sourceUUID;
            this.targetUUID = targetUUID;
            this.damageSource = ds;
        }
    }

    public static class EntityEnterRegion extends Event {
        public final UUID entityUUID;

        public EntityEnterRegion(UUID entityUUID, long srcRegionId, long tgtRegionId, long tick) {
            super(srcRegionId, tgtRegionId, tick);
            this.entityUUID = entityUUID;
        }
    }

    public static class EntityLeaveRegion extends Event {
        public final UUID entityUUID;

        public EntityLeaveRegion(UUID entityUUID, long srcRegionId, long tgtRegionId, long tick) {
            super(srcRegionId, tgtRegionId, tick);
            this.entityUUID = entityUUID;
        }
    }

    private static final int MAX_QUEUE_SIZE = 10000;
    private static final BlockingQueue<Event> inboundQueue =
            new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

    /** Keyed by stable region id. Holds no reference to any region. */
    private static final ConcurrentHashMap<Long, ConcurrentLinkedQueue<Event>>
            pendingByRegion = new ConcurrentHashMap<>();

    private static volatile boolean running = false;
    private static volatile long lastDropWarning = 0;

    private static Thread dispatcherThread;

    // ---------- Lifecycle ----------

    public static void init() {
        if (running) return;
        running = true;

        dispatcherThread = new Thread(() -> {
            SchedulerLog.info("CrossRegionHelper started");

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
                    // Mili start - fix: catch Throwable (not just Exception) to prevent
                    // dispatcher thread death on Error (e.g. StackOverflowError)
                    SchedulerLog.warn("CrossRegionHelper dispatch error: %s", e);
                    // Mili end
                }
            }

            SchedulerLog.info("CrossRegionHelper stopped");
        }, "Mili-CrossRegion");

        dispatcherThread.setDaemon(true);
        dispatcherThread.setPriority(Thread.NORM_PRIORITY - 1);
        dispatcherThread.start();
    }

    public static boolean isRunning() {
        return running;
    }

    private static void dispatchToTarget(Event event) {
        if (event.targetRegionId == RegionIdRegistry.UNKNOWN) {
            RegionTaskIdRegistry.unregister(event.taskUuid);
            if (event.transaction != null) {
                event.transaction.cancel("unknown target region");
            }
            return;
        }

        ConcurrentLinkedQueue<Event> queue = pendingByRegion.computeIfAbsent(
                event.targetRegionId, k -> new ConcurrentLinkedQueue<>());

        int maxPending = CrossRegionHelperConfig.maxPendingEventsPerRegion;

        if (queue.size() >= maxPending) {
            Event evicted = queue.poll();
            if (evicted != null) {
                RegionTaskIdRegistry.unregister(evicted.taskUuid);
                if (evicted.transaction != null) {
                    evicted.transaction.cancel("region queue overflow");
                }
            }
            regionQueueOverflows.increment();
        }

        queue.add(event);
    }

    public static void submit(Event event) {
        if (!CrossRegionHelperConfig.enabled || event == null) return;

        if (!inboundQueue.offer(event)) {
            RegionTaskIdRegistry.unregister(event.taskUuid);
            if (event.transaction != null) {
                event.transaction.cancel("inbound queue full");
            }
            eventsDropped.increment();
            long now = System.currentTimeMillis();
            if (now - lastDropWarning > 5000) {
                lastDropWarning = now;
                SchedulerLog.warn("CrossRegionHelper queue full, dropping events (suppressing for 5s)");
            }
        }
    }

    // ---------- Region resolution ----------

    /**
     * Resolve the region that owns a block position.
     *
     * <p>This is the piece that was missing. Returning {@code null} is a
     * deliberate, load-bearing outcome: callers must <b>skip</b> the cross-region
     * operation rather than falling back to the current region, because that
     * fallback is precisely the bug — a "cross-region" write that silently lands on
     * the source, or worse, gets executed by the source's thread.
     *
     * @return the owning region, or {@code null} if it cannot be resolved
     *         (unloaded chunk, no region yet, server mid-shutdown)
     */
    public static Object regionAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return null;
        try {
            // The regioniser is the only component that can answer "which region
            // owns this coordinate?" without running on that region's thread, which
            // is exactly what routing needs.
            //
            // Note what is returned: the *region*, not its RegionizedWorldData. The
            // two are interchangeable as identities here, and the region is reachable
            // from both directions (a position resolves to it, and
            // TickRegionScheduler.getCurrentRegion() hands back the same object type
            // for the region the caller is already running in), whereas walking from
            // a region to its world data needs an accessor on TickRegionData that is
            // not part of the public surface this project compiles against.
            return level.regioniser.getRegionAtUnsynchronised(pos.getX() >> 4, pos.getZ() >> 4);
        } catch (Throwable t) {
            // Never let a lookup failure escape into a tick. Callers treat null as
            // "do not route", which is the safe direction.
            SchedulerLog.debug("CrossRegionHelper region lookup failed for %s in %s: %s", pos, level, t);
            return null;
        }
    }

    /**
     * The region the calling thread is currently ticking.
     * <p>
     * This is the source side of every cross-region decision, and it is deliberately
     * the <em>same object type</em> that {@link #regionAt} returns for the target
     * side: two region references can then be compared with {@code ==}, which is the
     * only comparison that actually answers "are these the same region?".
     *
     * @return the current region, or {@code null} outside a region tick (for example
     *         on the global region's thread or a non-server thread)
     */
    public static Object currentRegion() {
        try {
            return TickRegionScheduler.getCurrentRegion();
        } catch (Throwable t) {
            // Called from a thread Folia does not consider a tick thread.
            return null;
        }
    }

    // ---------- Scheduler integration ----------

    /**
     * Publish this class's position lookup to the scheduler.
     * <p>
     * The scheduler core cannot resolve coordinates itself — that needs the Folia
     * regioniser, which is a Minecraft type. Installing the locator here means
     * every region-resolution decision in the process goes through one
     * implementation, so the "which region owns this?" answer cannot drift between
     * the scheduler and the cross-region router.
     */
    public static void installSchedulerLocator() {
        RegionResolver.install((level, blockX, blockZ) -> {
            if (!(level instanceof ServerLevel serverLevel)) return null;
            return regionAt(serverLevel, new BlockPos(blockX, 0, blockZ));
        });
    }

    // ---------- Producers ----------
    /**
     * Route a redstone update to the region owning {@code neighbor}.
     *
     * @see #regionAt(ServerLevel, BlockPos) for why an unresolved target is skipped
     */
    public static void submitRedstoneCrossRegion(ServerLevel level, BlockPos pos,
                                                 BlockPos neighbor, Direction dir) {
        if (!CrossRegionHelperConfig.enabled || level == null
                || pos == null || neighbor == null) return;

        // Source: the region this thread is already ticking.
        Object srcRegion = currentRegion();
        if (srcRegion == null) return;

        // Target: resolved from the neighbour's own coordinates. The old code read
        // level.getCurrentWorldData() a second time here, which made
        // `srcRegion == tgtRegion` unconditionally true and the method a no-op.
        Object tgtRegion = regionAt(level, neighbor);
        if (tgtRegion == null) {
            unresolvedTargets.increment();
            return;
        }

        if (srcRegion == tgtRegion) {
            // Genuinely not cross-region: the neighbour is in this region, so the
            // caller's own update path already handles it.
            localSkipped.increment();
            return;
        }

        submit(new RedstoneSignal(pos, neighbor, dir,
                RegionIdRegistry.idOf(srcRegion), RegionIdRegistry.idOf(tgtRegion),
                level.getGameTime()));
    }

    /**
     * Route damage from {@code source} to {@code target}.
     * <p>
     * The source region comes from the executing context; the target region is
     * resolved from the target entity's own position. Using
     * {@code target.level().getCurrentWorldData()} — as the previous version did —
     * asks for the <em>current thread's</em> region data, so a target entity in
     * another region produced the same value as the source and the event was
     * silently dropped by the equality guard.
     */
    public static void submitDamageCrossRegion(LivingEntity source, LivingEntity target,
                                               DamageSource damageSource, long tick) {
        if (!CrossRegionHelperConfig.enabled || source == null
                || target == null || damageSource == null) return;

        Object srcRegion = currentRegion();
        if (srcRegion == null) return;

        if (!(target.level() instanceof ServerLevel targetLevel)) return;
        Object tgtRegion = regionAt(targetLevel, target.blockPosition());
        if (tgtRegion == null) {
            unresolvedTargets.increment();
            return;
        }

        if (srcRegion == tgtRegion) {
            localSkipped.increment();
            return;
        }

        submit(new EntityDamageSync(source.getUUID(), target.getUUID(),
                damageSource, RegionIdRegistry.idOf(srcRegion), RegionIdRegistry.idOf(tgtRegion), tick));
    }

    // ---------- Consumers ----------

    /**
     * Drain everything queued for a region.
     * <p>
     * Takes the region reference itself and never the world data: identity has to be
     * the same kind of object on both sides of the comparison, and the id is derived
     * with {@link RegionIdRegistry#peek} so a region nobody registered reports "no
     * queue" rather than being handed a fresh identity.
     */
    public static ConcurrentLinkedQueue<Event> consumePending(Object region) {
        if (!CrossRegionHelperConfig.enabled || region == null) return null;
        long regionId = RegionIdRegistry.peek(region);
        if (regionId == RegionIdRegistry.UNKNOWN) return null;

        ConcurrentLinkedQueue<Event> queue = pendingByRegion.remove(regionId);
        if (queue != null) {
            for (Event event : queue) {
                RegionTaskIdRegistry.unregister(event.taskUuid);
                // The event has reached the target region's tick. As far as this
                // class is responsible for it, the routing transaction is done.
                if (event.transaction != null) {
                    event.transaction.markExecuting();
                    event.transaction.commit();
                }
            }
        }
        return queue;
    }

    /**
     * Called from the target region's tick.
     * <p>
     * The {@code RegionizedWorldData} argument is retained because the injection
     * point passes it, but identity comes from the current region instead — see
     * {@link #currentRegion()}.
     */
    public static ConcurrentLinkedQueue<Event> onRegionTick(ServerLevel level,
                                                            RegionizedWorldData data) {
        if (!CrossRegionHelperConfig.enabled) return null;
        return consumePending(currentRegion());
    }

    /** Pending event count for a region. */
    public static int pendingCount(Object region) {
        if (region == null) return 0;
        long regionId = RegionIdRegistry.peek(region);
        if (regionId == RegionIdRegistry.UNKNOWN) return 0;
        ConcurrentLinkedQueue<Event> q = pendingByRegion.get(regionId);
        return q != null ? q.size() : 0;
    }

    /** Pending event count for the calling thread's own region. */
    public static int pendingCountHere() {
        return pendingCount(currentRegion());
    }

    public static int inboundQueueSize() {
        return inboundQueue.size();
    }

    /**
     * Drop everything queued for a region.
     * <p>
     * Wired into the region destroy sequence via
     * {@code FoliaSchedulerAdapter.installLifecycleHooks}, so this runs when a
     * region goes away instead of never.
     */
    public static void onRegionUnload(Object region) {
        if (region == null) return;
        onRegionUnload(RegionIdRegistry.peek(region));
    }

    /** Drop everything queued for a region id. Used by the lifecycle hook. */
    public static void onRegionUnload(long regionId) {
        if (regionId == RegionIdRegistry.UNKNOWN) return;

        ConcurrentLinkedQueue<Event> removed = pendingByRegion.remove(regionId);
        if (removed != null && !removed.isEmpty()) {
            for (Event event : removed) {
                RegionTaskIdRegistry.unregister(event.taskUuid);
                if (event.transaction != null) {
                    event.transaction.cancel("target region unloaded");
                }
            }
            SchedulerLog.debug("Dropped %d event(s) for unloaded %s",
                    removed.size(), RegionIdRegistry.labelOf(regionId));
        }

        // The region's entity budget goes with it (fix.md §14): one scheduler per
        // region is only bounded if the region destroys it.
        EntityScheduler.release(regionId);
    }

    /** Number of regions with queued events. Diagnostics and leak checks. */
    public static int trackedRegions() {
        return pendingByRegion.size();
    }

    public static int totalPendingAcrossRegions() {
        int total = 0;
        for (ConcurrentLinkedQueue<Event> q : pendingByRegion.values()) {
            total += q.size();
        }
        return total;
    }

    /**
     * Find the source region id for a task UUID.
     *
     * @return the source region id, or {@link RegionIdRegistry#UNKNOWN}
     */
    public static long findSourceRegionIdForTaskUuid(UUID taskUuid) {
        if (taskUuid == null) return RegionIdRegistry.UNKNOWN;
        for (Event event : inboundQueue) {
            if (taskUuid.equals(event.taskUuid)) return event.sourceRegionId;
        }
        for (ConcurrentLinkedQueue<Event> queue : pendingByRegion.values()) {
            for (Event event : queue) {
                if (taskUuid.equals(event.taskUuid)) return event.sourceRegionId;
            }
        }
        return RegionIdRegistry.UNKNOWN;
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
        stats.put("unresolved_targets", unresolvedTargets.sum());
        stats.putAll(CrossRegionTransaction.stats());
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
            dispatcherThread = null;
        }

        for (Event event : inboundQueue) {
            RegionTaskIdRegistry.unregister(event.taskUuid);
            if (event.transaction != null) {
                event.transaction.cancel("shutdown");
            }
        }
        for (ConcurrentLinkedQueue<Event> queue : pendingByRegion.values()) {
            for (Event event : queue) {
                RegionTaskIdRegistry.unregister(event.taskUuid);
                if (event.transaction != null) {
                    event.transaction.cancel("shutdown");
                }
            }
        }

        inboundQueue.clear();
        pendingByRegion.clear();

        SchedulerLog.info("CrossRegionHelper shutdown");
    }
}
