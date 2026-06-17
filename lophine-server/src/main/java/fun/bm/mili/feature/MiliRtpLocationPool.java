package fun.bm.mili.feature;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.function.RtpConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * mili - RTP 预计算坐标池 / RTP pre-computed coordinate pool.
 *
 * <p>在后台异步线程预生成随机 X/Z 坐标 / Pre-generates random X/Z coordinates
 * on the async scheduler thread.
 *
 * <p><b>重要: 池填充不调用任何世界 API / Important: pool fill does NOT call any
 * world APIs</b> (isChunkGenerated/getTileEntities 在 Folia 中是主线程 API，
 * 从异步线程调用会阻塞区域线程触发 watchdog)。所有安全检查在 /rtp 执行时
 * 在区域线程上进行 / All safety checks run on the region thread during /rtp execution.
 *
 * <p>安全检查由 {@link MiliRtpCommand#validateLocation} 执行 / Safety checks
 * are performed by MiliRtpCommand.validateLocation.
 */
public final class MiliRtpLocationPool {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MiliRtpLocationPool() {}

    /** 每个世界的坐标队列 / Per-world coordinate queues. */
    private static final Map<String, ConcurrentLinkedDeque<Location>> POOLS = new ConcurrentHashMap<>();

    /** 后台填充任务是否运行 / Whether background fill task is running. */
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    /**
     * 启动后台填充任务 / Start background fill task.
     * 每 2 秒补充坐标 (纯数学计算，零世界 API 调用) / Replenishes every 2s (pure math, zero world API calls).
     */
    public static void start() {
        if (!RUNNING.compareAndSet(false, true)) return;

        Bukkit.getAsyncScheduler().runAtFixedRate(
                MinecraftInternalPlugin.INSTANCE,
                task -> fillAllPools(),
                2, 2, java.util.concurrent.TimeUnit.SECONDS
        );
        LOGGER.info("mili-rtp location pool started (target: {})", RtpConfig.poolSize);
    }

    public static void stop() {
        RUNNING.set(false);
        POOLS.clear();
    }

    /**
     * 从池中取出一个候选坐标 / Pick a candidate coordinate from the pool.
     * 返回的位置需要由 {@link MiliRtpCommand} 验证安全性 / Returned location
     * must be validated by MiliRtpCommand for safety.
     */
    public static Location pick(World world) {
        final ConcurrentLinkedDeque<Location> pool = POOLS.get(world.getName());
        if (pool == null) return null;
        return pool.pollFirst();
    }

    public static int size(World world) {
        final ConcurrentLinkedDeque<Location> pool = POOLS.get(world.getName());
        return pool == null ? 0 : pool.size();
    }

    // ==================== 纯数学填充 / Pure math fill ====================

    private static void fillAllPools() {
        if (!RUNNING.get()) return;
        for (World world : Bukkit.getWorlds()) {
            try {
                fillPool(world);
            } catch (Throwable t) {
                LOGGER.debug("[RtpPool] Fill error for {}: {}", world.getName(), t.getMessage());
            }
        }
    }

    /**
     * 纯数学坐标生成 — 不调用任何世界 API / Pure math coordinate generation — no world API calls.
     * 安全检查在 /rtp 执行时由区域线程完成 / Safety checks done at /rtp time by region thread.
     */
    private static void fillPool(World world) {
        final int targetSize = RtpConfig.poolSize;
        ConcurrentLinkedDeque<Location> pool = POOLS.computeIfAbsent(
                world.getName(), k -> new ConcurrentLinkedDeque<>());

        while (pool.size() < targetSize) {
            final ThreadLocalRandom rng = ThreadLocalRandom.current();
            final double angle = rng.nextDouble() * Math.PI * 2;
            final double dist = rng.nextDouble(RtpConfig.minDistance, RtpConfig.maxDistance);

            final double x = Math.cos(angle) * dist;
            final double z = Math.sin(angle) * dist;

            pool.addLast(new Location(world, x, 0, z));
        }
    }
}
