package fun.bm.mili.perf;

import fun.bm.mili.config.modules.misc.ChunkPreloadConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家行为预测区块预加载引擎 / Predictive chunk pre-loading engine.
 *
 * <p>通过分析玩家速度、方向和移动模式，在玩家到达之前预加载区块 /
 * Pre-loads chunks ahead of the player by analyzing velocity, direction,
 * and movement mode.
 *
 * <p><b>Folia 线程安全 / Thread safety:</b>
 * <ul>
 *   <li>{@link #onPlayerTick} 必须在玩家所属的区域 tick 线程调用 / Must be called from
 *       the player's owning region tick thread</li>
 *   <li>{@link #onPlayerTeleport} 可从任意 tick 线程调用 / Can be called from any
 *       tick thread (uses thread-safe async APIs)</li>
 * </ul>
 *
 * <p>不与 Moonrise 的 {@code RegionizedPlayerChunkLoader} 冲突——本系统
 * 预加载玩家尚未进入 loader 范围的区块 / Does not conflict with Moonrise's
 * RegionizedPlayerChunkLoader - this system pre-loads chunks outside the
 * player's normal loader range.
 */
public final class MiliChunkPreloader {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MiliChunkPreloader() {}

    // ==================== 数据结构 / Data structures ====================

    /** 每玩家状态追踪 / Per-player state tracker. */
    private static final ConcurrentHashMap<UUID, PlayerTrackState> TRACK_STATES = new ConcurrentHashMap<>();

    /** 玩家移动模式 / Player movement mode. */
    enum MoveMode {
        WALKING, VEHICLE, ELYTRA, TRIDENT
    }

    /**
     * 单个玩家的状态快照 / Single player state snapshot.
     * 仅在玩家的区域 tick 线程上读写 / Only read/written on the player's region tick thread.
     */
    static final class PlayerTrackState {
        double lastX, lastZ;
        double vx, vz;
        double speed;
        MoveMode mode = MoveMode.WALKING;
        int tickCounter = 0;
        int lastPredictedChunkX = Integer.MIN_VALUE;
        int lastPredictedChunkZ = Integer.MIN_VALUE;
        int preloadedThisCycle = 0;
    }

    // ==================== 公共 API / Public API ====================

    /**
     * 在玩家 tick 中调用 — 追踪速度并触发预加载 / Called during player tick.
     *
     * <p>必须在玩家的区域 tick 线程上调用 / Must be called on the player's region tick thread.
     * ServerPlayer.tick() 保证在区域线程上执行 / ServerPlayer.tick() runs on the region thread.
     */
    public static void onPlayerTick(ServerPlayer player) {
        if (!ChunkPreloadConfig.enabled) return;
        if (player.isRemoved() || player.isSpectator()) return;

        final UUID uuid = player.getUUID();
        final PlayerTrackState state = TRACK_STATES.computeIfAbsent(uuid, k -> {
            PlayerTrackState s = new PlayerTrackState();
            s.lastX = player.getX();
            s.lastZ = player.getZ();
            return s;
        });

        state.tickCounter++;

        // 每 N tick 采样速度 / Sample velocity every N ticks
        final int interval = Math.max(1, ChunkPreloadConfig.sampleIntervalTicks);
        if (state.tickCounter % interval != 0) return;

        // 计算速度 (blocks/tick) / Calculate velocity (blocks/tick)
        final double dx = player.getX() - state.lastX;
        final double dz = player.getZ() - state.lastZ;
        state.vx = dx / interval;
        state.vz = dz / interval;
        state.speed = Math.sqrt(state.vx * state.vx + state.vz * state.vz);
        state.lastX = player.getX();
        state.lastZ = player.getZ();

        // 检测移动模式 / Detect movement mode
        state.mode = detectMode(player);

        // 低速时跳过预加载 (减少无效工作) / Skip preloading at low speed (reduce waste)
        if (state.speed < 0.1) {
            state.preloadedThisCycle = 0;
            return;
        }

        // 计算预测位置 / Calculate predicted position
        final int lookAhead = ChunkPreloadConfig.lookAheadTicks;
        final double predX = player.getX() + state.vx * lookAhead;
        final double predZ = player.getZ() + state.vz * lookAhead;
        final int predChunkX = (int) Math.floor(predX) >> 4;
        final int predChunkZ = (int) Math.floor(predZ) >> 4;

        // 预测位置未变化时跳过 / Skip if predicted position hasn't changed
        if (predChunkX == state.lastPredictedChunkX && predChunkZ == state.lastPredictedChunkZ) {
            return;
        }
        state.lastPredictedChunkX = predChunkX;
        state.lastPredictedChunkZ = predChunkZ;

        // 计算预加载半径 / Calculate preload radius
        final int radius = calculateRadius(state);

        // 触发预加载 / Trigger preload
        final ServerLevel level = (ServerLevel) player.level();
        if (level == null) return;

        final ca.spottedleaf.concurrentutil.util.Priority priority =
                state.speed > ChunkPreloadConfig.highSpeedThreshold
                        ? ca.spottedleaf.concurrentutil.util.Priority.HIGH
                        : ca.spottedleaf.concurrentutil.util.Priority.NORMAL;

        scheduleRectPreload(level, predChunkX, predChunkZ, radius, priority);

        if (ChunkPreloadConfig.debug) {
            LOGGER.debug("[ChunkPreload] {} mode={} speed={} pred=({},{}), r={}, chunks~{}",
                    player.getGameProfile().name(), state.mode, String.format("%.2f", state.speed),
                    predChunkX, predChunkZ, radius, (2 * radius + 1) * (2 * radius + 1));
        }
    }

    /**
     * 传送事件时调用 — 立即预加载目标区域 / Called on teleport — preload destination.
     *
     * <p>可从任意 tick 线程安全调用 / Safe to call from any tick thread.
     * 使用 {@code moonrise$loadChunksAsync} 自动路由到目标区域线程 /
     * Uses moonrise$loadChunksAsync which auto-routes to the correct region thread.
     *
     * @param player 传送的玩家 / The teleporting player
     * @param dest 目标世界 / Destination world
     * @param target 目标坐标 / Target position
     */
    public static void onPlayerTeleport(ServerPlayer player, ServerLevel dest, BlockPos target) {
        if (!ChunkPreloadConfig.enabled) return;
        if (dest == null || target == null) return;

        final int centerChunkX = target.getX() >> 4;
        final int centerChunkZ = target.getZ() >> 4;
        final int teleportRadius = ChunkPreloadConfig.teleportPreloadRadius;

        // 内圈: BLOCKING 优先级，确保快速可用 / Inner ring: BLOCKING priority for fast availability
        final int innerRadius = Math.min(3, teleportRadius);
        scheduleRectPreload(dest, centerChunkX, centerChunkZ, innerRadius,
                ca.spottedleaf.concurrentutil.util.Priority.HIGHEST);

        // 外圈: HIGH 优先级 / Outer ring: HIGH priority
        if (teleportRadius > innerRadius) {
            scheduleRingPreload(dest, centerChunkX, centerChunkZ,
                    innerRadius + 1, teleportRadius,
                    ca.spottedleaf.concurrentutil.util.Priority.HIGH);
        }

        if (ChunkPreloadConfig.debug) {
            LOGGER.debug("[ChunkPreload] Teleport preload: dest={}, center=({},{}), r={}",
                    dest.dimension().identifier(), centerChunkX, centerChunkZ, teleportRadius);
        }
    }

    /**
     * 玩家离开时清理状态 / Clean up state when player leaves.
     */
    public static void onPlayerRemove(ServerPlayer player) {
        TRACK_STATES.remove(player.getUUID());
    }

    /**
     * 清理所有断开连接玩家的状态 (由 MiliMemoryOptimizer 调用) /
     * Clean all disconnected players' states (called by MiliMemoryOptimizer).
     * 可安全从任意线程调用 / Safe to call from any thread.
     */
    public static void cleanupOfflinePlayers() {
        final var server = net.minecraft.server.MinecraftServer.getServer();
        if (server == null) return;
        TRACK_STATES.keySet().removeIf(uuid -> {
            final var player = server.getPlayerList().getPlayer(uuid);
            return player == null || player.hasDisconnected();
        });
    }

    // ==================== 内部方法 / Internal methods ====================

    /**
     * 检测玩家当前移动模式 / Detect player's current movement mode.
     */
    private static MoveMode detectMode(ServerPlayer player) {
        if (player.isFallFlying()) return MoveMode.ELYTRA;
        if (player.isAutoSpinAttack()) return MoveMode.TRIDENT;
        final Entity vehicle = player.getVehicle();
        if (vehicle != null) return MoveMode.VEHICLE;
        return MoveMode.WALKING;
    }

    /**
     * 根据移动模式和速度计算预加载半径 / Calculate preload radius based on mode and speed.
     */
    private static int calculateRadius(PlayerTrackState state) {
        final int baseRadius = ChunkPreloadConfig.basePreloadRadius;
        final int maxRadius = ChunkPreloadConfig.maxSpeedRadius;
        final double speedBlocksPerTick = state.speed; // blocks/tick

        double multiplier;
        switch (state.mode) {
            case ELYTRA:  multiplier = ChunkPreloadConfig.elytraMultiplier; break;
            case TRIDENT: multiplier = ChunkPreloadConfig.tridentMultiplier; break;
            default:      multiplier = 1.0; break;
        }

        // 动态半径 = 基础 + 速度 * 倍数 / Dynamic radius = base + speed * multiplier
        final int dynamic = (int) (baseRadius + speedBlocksPerTick * multiplier * 4);
        return Math.min(dynamic, maxRadius);
    }

    /**
     * 矩形区域预加载 / Preload a rectangular area of chunks.
     *
     * <p>使用 {@code moonrise$loadChunksAsync} 以指定优先级加载区块 /
     * Uses moonrise$loadChunksAsync with the given priority.
     * 回调在目标区域线程触发，安全添加 ticket / Callback fires on the target region thread,
     * safe to add tickets there.
     */
    private static void scheduleRectPreload(ServerLevel level, int centerChunkX, int centerChunkZ,
                                            int radius, ca.spottedleaf.concurrentutil.util.Priority priority) {
        final int minX = centerChunkX - radius;
        final int maxX = centerChunkX + radius;
        final int minZ = centerChunkZ - radius;
        final int maxZ = centerChunkZ + radius;

        level.moonrise$loadChunksAsync(minX, maxX, minZ, maxZ,
                ChunkStatus.FULL, priority, chunks -> {
                    // 回调在目标区域线程执行 / Callback runs on destination region thread
                    addDelayedTickets(level, minX, maxX, minZ, maxZ);
                });
    }

    /**
     * 环形区域预加载 (不含内圈) / Preload a ring-shaped area (excludes inner circle).
     */
    private static void scheduleRingPreload(ServerLevel level, int centerChunkX, int centerChunkZ,
                                            int innerRadius, int outerRadius,
                                            ca.spottedleaf.concurrentutil.util.Priority priority) {
        final int minX = centerChunkX - outerRadius;
        final int maxX = centerChunkX + outerRadius;
        final int minZ = centerChunkZ - outerRadius;
        final int maxZ = centerChunkZ + outerRadius;

        level.moonrise$loadChunksAsync(minX, maxX, minZ, maxZ,
                ChunkStatus.FULL, priority, chunks -> {
                    addDelayedTickets(level, minX, maxX, minZ, maxZ);
                });
    }

    /**
     * 为预加载的区块添加 DELAYED ticket 以保持加载状态 / Add DELAYED tickets to keep preloaded chunks loaded.
     *
     * <p>DELAYED ticket 有 5 tick 超时，到期后区块可正常卸载 / DELAYED ticket has 5 tick timeout,
     * chunks unload normally after expiry.
     *
     * <p>必须在目标区域线程上调用 / Must be called on the target region's thread.
     */
    private static void addDelayedTickets(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
        final var scheduler = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel) level)
                .moonrise$getChunkTaskScheduler();
        final var holderManager = scheduler.chunkHolderManager;
        final int ticketLevel = ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager.FULL_LOADED_TICKET_LEVEL;

        // DELAYED ticket 类型的 comparator 为 null，因此 identifier 必须为 null
        // DELAYED ticket type has null comparator, so identifier must be null
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                holderManager.addTicketAtLevel(
                        net.minecraft.server.level.TicketType.DELAYED,
                        new ChunkPos(cx, cz),
                        ticketLevel,
                        null
                );
            }
        }
    }
}
