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
 * mili - RTP 预计算位置池 / RTP pre-computed location pool.
 *
 * <p>在后台持续为每个世界预计算安全的随机位置 / Continuously pre-computes safe
 * random locations for each world in the background:
 * <ul>
 *   <li>服务器启动后立即开始填充 / Starts filling immediately after server starts</li>
 *   <li>玩家使用 /rtp 后立即从池中取用 / Player uses /rtp → instant pick from pool</li>
 *   <li>冷却期间后台自动补充 / Auto-replenishes in background during cooldown</li>
 * </ul>
 *
 * <p>只存储 X/Z 坐标 (不加载区块验证 Y) / Only stores X/Z (does NOT load chunks to
 * validate Y)。Y 坐标在传送时快速搜索 / Y is searched quickly at teleport time.
 */
public final class MiliRtpLocationPool {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MiliRtpLocationPool() {}

    /** 每个世界的位置队列 / Per-world location queues. */
    private static final Map<String, ConcurrentLinkedDeque<Location>> POOLS = new ConcurrentHashMap<>();

    /** 后台填充任务是否运行 / Whether background fill task is running. */
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    /** 填充任务 ID / Fill task reference. */
    private static volatile Object fillTask = null;

    /**
     * 启动后台填充任务 / Start background fill task.
     * 每 2 秒尝试为每个世界补充位置 / Tries to fill each world every 2 seconds.
     */
    public static void start() {
        if (!RUNNING.compareAndSet(false, true)) return;

        fillTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                MinecraftInternalPlugin.INSTANCE,
                task -> fillAllPools(),
                2, 2, java.util.concurrent.TimeUnit.SECONDS
        );
        LOGGER.info("mili-rtp location pool started (target size: {})", RtpConfig.poolSize);
    }

    /**
     * 停止后台填充任务 / Stop background fill task.
     */
    public static void stop() {
        RUNNING.set(false);
        POOLS.clear();
    }

    /**
     * 从池中取出一个位置 / Pick a location from the pool.
     *
     * @param world 世界 / The world
     * @return 预计算的位置, 或 null 如果池为空 / Pre-computed location, or null if empty
     */
    public static Location pick(World world) {
        final ConcurrentLinkedDeque<Location> pool = POOLS.get(world.getName());
        if (pool == null) return null;
        return pool.pollFirst();
    }

    /**
     * 获取池当前大小 / Get current pool size.
     */
    public static int size(World world) {
        final ConcurrentLinkedDeque<Location> pool = POOLS.get(world.getName());
        return pool == null ? 0 : pool.size();
    }

    /**
     * 为所有世界填充位置 / Fill locations for all worlds.
     * 在异步调度器线程上运行 (不阻塞区域线程) / Runs on async scheduler thread.
     */
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
     * 为单个世界补充位置到目标大小 / Fill a single world's pool to target size.
     */
    private static void fillPool(World world) {
        final int targetSize = RtpConfig.poolSize;
        ConcurrentLinkedDeque<Location> pool = POOLS.computeIfAbsent(
                world.getName(), k -> new ConcurrentLinkedDeque<>());

        while (pool.size() < targetSize) {
            final ThreadLocalRandom rng = ThreadLocalRandom.current();
            final double angle = rng.nextDouble() * Math.PI * 2;
            final double dist = rng.nextDouble(RtpConfig.minDistance, RtpConfig.maxDistance);

            // 以世界原点 (0,0) 为中心 / Centered on world origin
            final double x = Math.cos(angle) * dist;
            final double z = Math.sin(angle) * dist;

            pool.addLast(new Location(world, x, 0, z));
        }
    }
}
