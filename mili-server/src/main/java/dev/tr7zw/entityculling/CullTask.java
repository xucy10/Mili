package dev.tr7zw.entityculling;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.logisticscraft.occlusionculling.OcclusionCullingInstance;
import com.logisticscraft.occlusionculling.util.Vec3d;
import dev.tr7zw.entityculling.versionless.access.Cullable;
import fun.bm.mili.config.modules.experiment.RayTrackingEntityTrackerConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import fun.bm.mili.scheduler.MiliScheduler;
import fun.bm.mili.scheduler.Tier;

public class CullTask implements Runnable {

    private volatile boolean requestCull = false;
    private volatile boolean scheduleNext = true;
    private volatile boolean inited = false;

    private final OcclusionCullingInstance culling;
    private final Player checkTarget;

    private final int hitboxLimit;

    public long lastCheckedTime = 0;

    // reused preallocated vars
    private final Vec3d lastPos = new Vec3d(0, 0, 0);
    private final Vec3d aabbMin = new Vec3d(0, 0, 0);
    private final Vec3d aabbMax = new Vec3d(0, 0, 0);

    // Mili start - 实体剔除工作池。常量必须声明在池字段之前：静态初始化按声明顺序执行，
    // 若置后则取到的仍是 0，LinkedBlockingQueue(0) 会直接抛 IllegalArgumentException。
    private static final int CULL_QUEUE_CAPACITY = 256;

    /**
     * <p>原实现是 {@code Executors.newCachedThreadPool}：队列无上界，一旦提交快于消费
     * 就会无限创建线程直至内存耗尽，且该池<strong>从不关闭</strong>。此处改为有界池，
     * 满队列时丢弃最旧任务 —— 剔除结果丢一帧无关紧要，下一 tick 会重算。</p>
     *
     * <p><b>刻意保留 TickThread 身份</b>：Minecraft 代码中的
     * {@code TickThread.ensureTickThread(...)} 要求当前线程是 {@code TickThread} 实例
     * （见 {@code TickThread.isTickThread} 的 instanceof 判断），换成普通池线程会直接抛
     * IllegalStateException。这是典型的"看起来能优化、实际会炸"的改动，切勿顺手替换。</p>
     */
    private static final ThreadPoolExecutor backgroundWorker = createBackgroundWorker();

    private static ThreadPoolExecutor createBackgroundWorker() {
        final int maxThreads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                0, maxThreads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(CULL_QUEUE_CAPACITY),
                task -> {
                    final TickThread worker = new TickThread("EntityCulling") {
                        @Override
                        public void run() {
                            task.run();
                        }
                    };

                    worker.setDaemon(true);

                    return worker;
                },
                // 剔除任务的结果可以被安全地丢弃，因此不采用 CallerRunsPolicy ——
                // 后者会把计算压回调用方（往往是 tick 线程），反而伤 tick。
                new ThreadPoolExecutor.DiscardOldestPolicy());
        MiliScheduler.adopt("entity-culling", Tier.CPU, executor);
        return executor;
    }
    // Mili end

    private final Executor worker;

    public CullTask(
            OcclusionCullingInstance culling,
            Player checkTarget,
            int hitboxLimit,
            long checkIntervalMs
    ) {
        this.culling = culling;
        this.checkTarget = checkTarget;
        this.hitboxLimit = hitboxLimit;
        this.worker = CompletableFuture.delayedExecutor(checkIntervalMs, TimeUnit.MILLISECONDS, backgroundWorker);
    }

    public void requestCullSignal() {
        this.requestCull = true;
    }

    public void signalStop() {
        this.scheduleNext = false;
    }

    public void setup() {
        if (!this.inited)
            this.inited = true;
        else
            return;
        this.worker.execute(this);
    }

    @Override
    public void run() {
        try {
            if (this.checkTarget.tickCount > 10) {
                // getEyePosition can use a fixed delta as its debug only anyway
                Vec3 cameraMC = this.checkTarget.getEyePosition(0);
                if (requestCull || !(cameraMC.x == lastPos.x && cameraMC.y == lastPos.y && cameraMC.z == lastPos.z)) {
                    long start = System.currentTimeMillis();

                    requestCull = false;

                    lastPos.set(cameraMC.x, cameraMC.y, cameraMC.z);
                    culling.resetCache();

                    cullEntities(cameraMC, lastPos);

                    lastCheckedTime = (System.currentTimeMillis() - start);
                }
            }
        } finally {
            if (this.scheduleNext) {
                this.worker.execute(this);
            }
        }
    }

    private void cullEntities(Vec3 cameraMC, Vec3d camera) {
        for (Entity entity : this.checkTarget.level().getEntities().getAll()) {
            if (!(entity instanceof Cullable cullable)) {
                continue; // Not sure how this could happen outside from mixin screwing up the inject into
                // Entity
            }

            if (entity.getType().skipRaytracningCheck) {
                continue;
            }

            if (!cullable.isForcedVisible()) {
                if (entity.isCurrentlyGlowing() || isSkippableArmorstand(entity)) {
                    cullable.setCulled(false);
                    continue;
                }

                if (!entity.position().closerThan(cameraMC, RayTrackingEntityTrackerConfig.tracingDistance)) {
                    cullable.setCulled(false); // If your entity view distance is larger than tracingDistance just
                    // render it
                    continue;
                }

                AABB boundingBox = entity.getBoundingBox();
                if (boundingBox.getXsize() > hitboxLimit || boundingBox.getYsize() > hitboxLimit
                        || boundingBox.getZsize() > hitboxLimit) {
                    cullable.setCulled(false); // Too big to bother to cull
                    continue;
                }

                aabbMin.set(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
                aabbMax.set(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);

                boolean visible = culling.isAABBVisible(aabbMin, aabbMax, camera);

                cullable.setCulled(!visible);
            }
        }
    }

    private boolean isSkippableArmorstand(Entity entity) {
        if (!RayTrackingEntityTrackerConfig.skipMarkerArmorStands)
            return false;
        return entity instanceof ArmorStand && entity.isInvisible();
    }
}
