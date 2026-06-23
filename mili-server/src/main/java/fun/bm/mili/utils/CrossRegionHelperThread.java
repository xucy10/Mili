package fun.bm.mili.utils;

import fun.bm.mili.config.modules.experiment.CrossRegionHelperConfig;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cross-Region Helper Thread.
 * <p>
 * This thread does NOT execute any game logic. Its sole responsibility is:
 * <ol>
 *   <li>Receive cross-region events submitted by region tick threads.</li>
 *   <li>Route those events into per-target-region pending queues.</li>
 *   <li>Let the target region consume and execute the actual logic on its own tick thread.</li>
 * </ol>
 * <p>
 * By centralizing the routing, we avoid each region having to independently
 * schedule tasks onto other regions, which reduces scheduling overhead and
 * makes cross-region behaviour easier to trace and debug.
 */
public class CrossRegionHelperThread {

    private static final Thread HELPER_THREAD;
    private static final BlockingQueue<CrossRegionEvent> EVENT_QUEUE = new LinkedBlockingQueue<>();
    // Key: RegionizedWorldData instance (each region has exactly one).  Value: pending events for that region.
    private static final ConcurrentHashMap<RegionizedWorldData, ConcurrentLinkedQueue<CrossRegionEvent>> PENDING_BY_REGION = new ConcurrentHashMap<>();
    private static final AtomicLong EVENT_COUNTER = new AtomicLong(0);
    private static volatile boolean RUNNING = false;

    /**
     * Event type.  Each type determines what the target region should do when it
     * consumes the event.  The helper thread itself does not interpret the payload.
     */
    public enum EventType {
        /** Redstone signal update that needs to be propagated to a neighbour region. */
        REDSTONE_SIGNAL,
        /** An entity has crossed a region boundary and needs to be registered in the target region. */
        ENTITY_ENTER_REGION,
        /** An entity has left a region and needs to be unregistered from the source region. */
        ENTITY_LEAVE_REGION,
        /** Block update notification for a neighbour region. */
        BLOCK_NOTIFY,
        /** Cross-region damage / effect that needs to be applied on the target entity. */
        ENTITY_DAMAGE_SYNC,
        /** Generic cross-region task scheduled by a plugin or patch. */
        GENERIC
    }

    /**
     * A cross-region event.  The helper thread treats this as an opaque envelope.
     */
    public static final class CrossRegionEvent {
        public final long id;
        public final EventType type;
        public final RegionizedWorldData sourceRegion;
        public final RegionizedWorldData targetRegion;
        public final Object payload;
        public final long tickStamp;

        public CrossRegionEvent(EventType type, RegionizedWorldData sourceRegion, RegionizedWorldData targetRegion, Object payload, long tickStamp) {
            this.id = EVENT_COUNTER.incrementAndGet();
            this.type = type;
            this.sourceRegion = sourceRegion;
            this.targetRegion = targetRegion;
            this.payload = payload;
            this.tickStamp = tickStamp;
        }

        @Override
        public String toString() {
            return "CrossRegionEvent{id=" + id + ", type=" + type + ", tick=" + tickStamp + "}";
        }
    }

    static {
        HELPER_THREAD = new Thread(CrossRegionHelperThread::runLoop, "CrossRegion-Helper");
        HELPER_THREAD.setDaemon(true);
        HELPER_THREAD.start();
    }

    private static void runLoop() {
        RUNNING = true;
        while (RUNNING) {
            try {
                CrossRegionEvent event = EVENT_QUEUE.poll(
                        CrossRegionHelperConfig.queuePollTimeoutMs, TimeUnit.MILLISECONDS);
                if (event == null) {
                    continue;
                }

                // The helper thread ONLY routes the event.  It never touches game state.
                ConcurrentLinkedQueue<CrossRegionEvent> queue = PENDING_BY_REGION
                        .computeIfAbsent(event.targetRegion, k -> new ConcurrentLinkedQueue<>());

                // Drop oldest events if the queue is over the limit
                int max = CrossRegionHelperConfig.maxPendingEventsPerRegion;
                while (queue.size() >= max) {
                    queue.poll(); // drop oldest
                }
                queue.add(event);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                com.mojang.logging.LogUtils.getClassLogger().error("CrossRegionHelperThread loop error", ex);
            }
        }
    }

    /**
     * Submit a cross-region event from the current region thread.
     * This call is non-blocking and returns immediately.
     */
    public static void submit(CrossRegionEvent event) {
        if (!CrossRegionHelperConfig.enabled) return;
        if (event == null) return;
        EVENT_QUEUE.add(event);
    }

    /**
     * Convenience overload.
     */
    public static void submit(EventType type, RegionizedWorldData sourceRegion, RegionizedWorldData targetRegion, Object payload, long tickStamp) {
        submit(new CrossRegionEvent(type, sourceRegion, targetRegion, payload, tickStamp));
    }

    /**
     * Consume all pending events for a given region.  Called by the target region's
     * tick thread at the beginning of its tick.
     *
     * @return the queue of pending events, or null if none.  The caller is responsible
     *         for draining and processing the queue on its own thread.
     */
    @Nullable
    public static Queue<CrossRegionEvent> consumePending(RegionizedWorldData targetRegion) {
        if (!CrossRegionHelperConfig.enabled) return null;
        return PENDING_BY_REGION.remove(targetRegion);
    }

    /**
     * Called at the beginning of each region tick.  Consumes pending cross-region events
     * and returns them for processing.  The caller (region tick thread) must handle the
     * actual logic based on event type.
     *
     * @param level the server level
     * @param data  the current region's world data
     * @return pending events for this region, or null if none
     */
    @Nullable
    public static Queue<CrossRegionEvent> onRegionTick(ServerLevel level, RegionizedWorldData data) {
        if (!CrossRegionHelperConfig.enabled) return null;
        if (data == null) return null;
        return consumePending(data);
    }

    /**
     * Peek how many events are currently pending for a region (diagnostic only).
     */
    public static int pendingCount(RegionizedWorldData targetRegion) {
        Queue<CrossRegionEvent> q = PENDING_BY_REGION.get(targetRegion);
        return q == null ? 0 : q.size();
    }

    /**
     * Total events in the inbound queue (diagnostic only).
     */
    public static int inboundQueueSize() {
        return EVENT_QUEUE.size();
    }

    /**
     * Clean up pending events for a region that is being unloaded.
     */
    public static void onRegionUnload(RegionizedWorldData data) {
        if (data == null) return;
        PENDING_BY_REGION.remove(data);
    }

    public static void submitRedstoneCrossRegion(ServerLevel sl, BlockPos pos, BlockPos neighbor, Direction dir) {
        if (!CrossRegionHelperConfig.enabled || sl == null) return;
        RegionizedWorldData s = sl.getCurrentWorldData();
        if (s == null) return;
        submit(REDSTONE_SIGNAL, s, s, new Object[]{pos, neighbor, dir}, sl.getServer().getTickCount());
    }

    public static void submitDamageCrossRegion(LivingEntity src, LivingEntity tgt, DamageSource ds, long tick) {
        if (!CrossRegionHelperConfig.enabled || src == null || tgt == null) return;
        io.papermc.paper.threadedregions.RegionizedWorldData s = src.level().getCurrentWorldData();
        io.papermc.paper.threadedregions.RegionizedWorldData t = tgt.level().getCurrentWorldData();
        if (s != null && t != null && s != t) {
            submit(ENTITY_DAMAGE_SYNC, s, t, new Object[]{src.getUUID(), tgt.getUUID(), ds}, tick);
        }
    }

    public static void shutdown() {
        RUNNING = false;
        HELPER_THREAD.interrupt();
    }
}
