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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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

        protected Event(RegionizedWorldData src, RegionizedWorldData tgt, long tick) {
            this.id = eventIdGen.incrementAndGet();
            this.sourceRegion = src;
            this.targetRegion = tgt;
            this.tickStamp = tick;
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
            } catch (Exception e) {
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
        if (event.targetRegion == null) return;

        ConcurrentLinkedQueue<Event> queue = pendingByRegion.computeIfAbsent(
                event.targetRegion, k -> new ConcurrentLinkedQueue<>());

        int maxPending = CrossRegionHelperConfig.maxPendingEventsPerRegion;

        if (queue.size() >= maxPending) {
            queue.poll();
            regionQueueOverflows.increment();
        }

        queue.add(event);
    }

    public static void submit(Event event) {
        if (!CrossRegionHelperConfig.enabled || event == null) return;

        if (!inboundQueue.offer(event)) {
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

        // D2 fix: 用 regioniser 判断 pos 与 neighbor 是否处于不同区域。
        // getRegionAtUnsynchronised 在当前线程拥有该 chunk 时返回非 null（调用发生在源区域 tick 线程）。
        // 若 neighbor 与 pos 在同一 ThreadedRegion（引用相等），属于区域内红石，无需跨区处理。
        var srcThreadedRegion = level.regioniser.getRegionAtUnsynchronised(
                pos.getX() >> 4, pos.getZ() >> 4);
        var neighborThreadedRegion = level.regioniser.getRegionAtSynchronised(
                neighbor.getX() >> 4, neighbor.getZ() >> 4);

        if (srcThreadedRegion == neighborThreadedRegion) return; // 同区，跳过
        if (neighborThreadedRegion == null) return; // neighbor chunk 未加载

        // 目标区域的 RegionizedWorldData 是线程局部的，无法在此获取。
        // targetRegion 设为 null；消费侧通过 neighbor 坐标匹配当前区域。
        submit(new RedstoneSignal(pos, neighbor, dir, srcRegion, null, level.getGameTime()));
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

    /**
     * 在目标区域 tick 开头调用：从 inboundQueue 中取出属于当前区域的 RedstoneSignal 事件并重放。
     *
     * <p>D2 fix: 原实现仅返回队列给调用方丢弃；现内部实装重放逻辑。
     * 红线：红石语义判定留 Java——调用 {@link ServerLevel#updateNeighborsAt} 触发原版邻居更新。
     *
     * @return 仍待其他区域处理的事件数（观测用；调用方可忽略）
     */
    public static ConcurrentLinkedQueue<Event> onRegionTick(ServerLevel level,
                                                            RegionizedWorldData data) {
        if (!CrossRegionHelperConfig.enabled || data == null) return null;

        // 从 inboundQueue 中 drain 事件，按 neighbor 坐标匹配当前区域
        java.util.List<Event> deferred = new java.util.ArrayList<>();
        Event event;
        int processed = 0;
        while ((event = inboundQueue.poll()) != null) {
            if (event instanceof RedstoneSignal rs) {
                // getRegionAtUnsynchronised 在当前线程拥有该 chunk 时返回非 null
                var neighborRegion = level.regioniser.getRegionAtUnsynchronised(
                        rs.neighbor.getX() >> 4, rs.neighbor.getZ() >> 4);
                if (neighborRegion != null) {
                    // neighbor 在当前区域，重放红石邻居更新
                    replayRedstoneSignal(level, rs);
                    processed++;
                } else {
                    // 不属于当前区域，放回队列等待其他区域处理
                    deferred.add(event);
                }
            } else {
                deferred.add(event);
            }
        }
        if (processed > 0) {
            eventsProcessed.add(processed);
        }
        // 放回不属于当前区域的事件
        for (Event e : deferred) {
            if (!inboundQueue.offer(e)) {
                eventsDropped.increment();
            }
        }
        return null; // 返回 null：消费逻辑已内部完成，调用方无需处理
    }

    /**
     * 在目标区域线程内重放跨区红石信号。
     *
     * <p><b>红线</b>：只触发原版邻居更新机制，不自行判定红石语义。
     *
     * <p><b>线程安全</b>：仅读取目标区域内的 {@code neighbor} 位置，绝不读取 {@code rs.pos}
     * 的方块状态——后者属于源区域，此刻可能正被另一个区域线程修改。
     */
    private static void replayRedstoneSignal(ServerLevel level, RedstoneSignal rs) {
        try {
            if (level.getBlockState(rs.neighbor).isAir()) {
                return; // 目标位置为空，没有需要通知的方块
            }
            // ServerLevel#neighborChanged(BlockPos, Block, @Nullable Orientation) 会委托给
            // 当前线程所属区域自身的 neighborUpdater（Folia 区域化下是安全的）。
            // 语义：告知位于目标区域内、紧邻源红石线的方块——"你旁边的红石线变了"。
            level.neighborChanged(rs.neighbor,
                    net.minecraft.world.level.block.Blocks.REDSTONE_WIRE, null);
        } catch (Exception e) {
            eventsDropped.increment();
        }
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
            ConcurrentLinkedQueue<Event> removed = pendingByRegion.remove(data);
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

        inboundQueue.clear();
        pendingByRegion.clear();

        com.mojang.logging.LogUtils.getClassLogger().info("[Mili] CrossRegionHelper shutdown");
    }
}