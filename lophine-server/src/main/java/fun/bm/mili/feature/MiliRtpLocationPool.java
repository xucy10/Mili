package fun.bm.mili.feature;

import com.mojang.logging.LogUtils;
import fun.bm.mili.config.modules.function.RtpConfig;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
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
 *   <li>优先选择已生成的区块 (避免实时地形生成) / Prioritizes generated chunks</li>
 *   <li>避开玩家及玩家建筑 / Avoids players and player structures</li>
 *   <li>冷却期间后台自动补充 / Auto-replenishes during cooldown</li>
 * </ul>
 *
 * <p>安全检查流程 / Safety check pipeline:
 * <ol>
 *   <li>区块已生成检查 (isChunkGenerated) / Chunk generated check</li>
 *   <li>周围区块也已生成 (避免区块边界) / Surrounding chunks also generated</li>
 *   <li>无附近玩家 (avoidPlayerRadius) / No nearby players</li>
 *   <li>无玩家建筑 (箱子/床/门/告示牌) / No player structures</li>
 * </ol>
 */
public final class MiliRtpLocationPool {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MiliRtpLocationPool() {}

    /** 每个世界的位置队列 / Per-world location queues. */
    private static final Map<String, ConcurrentLinkedDeque<Location>> POOLS = new ConcurrentHashMap<>();

    /** 后台填充任务是否运行 / Whether background fill task is running. */
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    /** 单次填充最大尝试次数 (避免死循环) / Max attempts per fill cycle (avoid infinite loop). */
    private static final int MAX_ATTEMPTS_PER_CYCLE = 100;

    /** 玩家建筑指示性方块 / Player structure indicator blocks. */
    private static final Set<Material> PLAYER_INDICATORS = Set.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.CRAFTING_TABLE, Material.ANVIL, Material.ENCHANTING_TABLE,
            Material.BREWING_STAND,
            Material.OAK_DOOR, Material.IRON_DOOR, Material.SPRUCE_DOOR,
            Material.BIRCH_DOOR, Material.JUNGLE_DOOR, Material.ACACIA_DOOR,
            Material.DARK_OAK_DOOR, Material.CRIMSON_DOOR, Material.WARPED_DOOR,
            Material.OAK_SIGN, Material.OAK_WALL_SIGN,
            Material.BEACON, Material.HOPPER, Material.DROPPER, Material.DISPENSER,
            Material.ENDER_CHEST, Material.SHULKER_BOX
    );

    /** 所有床类型 / All bed types. */
    private static boolean isBed(Material mat) {
        return mat.name().endsWith("_BED");
    }

    /**
     * 启动后台填充任务 / Start background fill task.
     */
    public static void start() {
        if (!RUNNING.compareAndSet(false, true)) return;

        Bukkit.getAsyncScheduler().runAtFixedRate(
                MinecraftInternalPlugin.INSTANCE,
                task -> fillAllPools(),
                2, 2, java.util.concurrent.TimeUnit.SECONDS
        );
        LOGGER.info("mili-rtp location pool started (target: {}, requireGenerated: {}, avoidRadius: {})",
                RtpConfig.poolSize, RtpConfig.requireGenerated, RtpConfig.avoidPlayerRadius);
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

    // ==================== 内部填充逻辑 / Internal fill logic ====================

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
     *
     * <p>每次填充周期最多尝试 {@link #MAX_ATTEMPTS_PER_CYCLE} 次，
     * 避免在未生成世界中死循环 / Max attempts per cycle to avoid infinite loops
     * in ungenerated worlds.
     */
    private static void fillPool(World world) {
        final int targetSize = RtpConfig.poolSize;
        ConcurrentLinkedDeque<Location> pool = POOLS.computeIfAbsent(
                world.getName(), k -> new ConcurrentLinkedDeque<>());

        int attempts = 0;
        while (pool.size() < targetSize && attempts < MAX_ATTEMPTS_PER_CYCLE) {
            attempts++;
            final ThreadLocalRandom rng = ThreadLocalRandom.current();
            final double angle = rng.nextDouble() * Math.PI * 2;
            final double dist = rng.nextDouble(RtpConfig.minDistance, RtpConfig.maxDistance);

            final int x = (int) (Math.cos(angle) * dist);
            final int z = (int) (Math.sin(angle) * dist);

            if (isSafeLocation(world, x, z)) {
                pool.addLast(new Location(world, x, 0, z));
            }
        }
    }

    /**
     * 检查位置是否安全 (已生成 + 无玩家活动) / Check if location is safe.
     *
     * <p>在异步调度器线程上运行 / Runs on async scheduler thread.
     *
     * @return true 如果区块已生成且无玩家活动 / true if generated and no player activity
     */
    private static boolean isSafeLocation(World world, int x, int z) {
        final int cx = x >> 4;
        final int cz = z >> 4;

        // 检查 1: 目标区块已生成 / Check 1: Target chunk is generated
        if (RtpConfig.requireGenerated) {
            if (!world.isChunkGenerated(cx, cz)) {
                return false;
            }

            // 检查周围区块也已生成 / Check surrounding chunks are also generated
            final int checkR = RtpConfig.generatedCheckRadius;
            for (int dx = -checkR; dx <= checkR; dx++) {
                for (int dz = -checkR; dz <= checkR; dz++) {
                    if (!world.isChunkGenerated(cx + dx, cz + dz)) {
                        return false;
                    }
                }
            }
        }

        // 检查 2: 附近无玩家 / Check 2: No nearby players
        final double avoidRadius = RtpConfig.avoidPlayerRadius;
        if (avoidRadius > 0) {
            for (Player player : world.getPlayers()) {
                final double pdx = player.getX() - x;
                final double pdz = player.getZ() - z;
                if (pdx * pdx + pdz * pdz < avoidRadius * avoidRadius) {
                    return false;
                }
            }
        }

        // 检查 3: 无玩家建筑 (扫描多区块范围) / Check 3: No player structures (multi-chunk scan)
        if (RtpConfig.requireGenerated) {
            try {
                if (hasPlayerStructuresNearby(world, cx, cz, 2)) {
                    return false;
                }
            } catch (Throwable ignored) {
                // Folia 线程安全问题时跳过 / Skip if thread-safety issue
            }
        }

        return true;
    }

    /**
     * 扫描多区块范围内的玩家建筑 / Scan multiple chunks for player structures.
     *
     * <p>检查目标区块周围 {@code radius} 区块内所有已加载区块的方块实体 /
     * Checks tile entities in all loaded chunks within radius of target.
     * 只扫描已加载区块 (不触发加载) / Only scans loaded chunks (no load trigger).
     *
     * @param world 世界 / World
     * @param cx 中心区块 X / Center chunk X
     * @param cz 中心区块 Z / Center chunk Z
     * @param radius 扫描半径 (区块) / Scan radius (chunks)
     */
    private static boolean hasPlayerStructuresNearby(World world, int cx, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                final int scanCx = cx + dx;
                final int scanCz = cz + dz;
                if (!world.isChunkLoaded(scanCx, scanCz)) continue;

                final Chunk chunk = world.getChunkAt(scanCx, scanCz);
                for (var te : chunk.getTileEntities()) {
                    final Material type = te.getType();
                    if (PLAYER_INDICATORS.contains(type) || isBed(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
