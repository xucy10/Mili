package fun.bm.mili.perf;

import fun.bm.mili.config.modules.misc.UnifiedSchedulerConfig;
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
 * 玩家行为预测区块预加载引�?/ Predictive chunk pre-loading engine.
 *
 * <p>通过分析玩家速度、方向和移动模式，在玩家到达之前预加载区�?/
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
 * <p>不与 Moonrise �?{@code RegionizedPlayerChunkLoader} 冲突——本系统
 * 预加载玩家尚未进�?loader 范围的区�?/ Does not conflict with Moonrise's
 * RegionizedPlayerChunkLoader - this system pre-loads chunks outside the
 * player's normal loader range.
 */
public final class MiliChunkPreloader {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MiliChunkPreloader() {}

    // ==================== 数据结构 / Data structures ====================

    /** 每玩家状态追�?/ Per-player state tracker. */
    private static final ConcurrentHashMap<UUID, PlayerTrackState> TRACK_STATES = new ConcurrentHashMap<>();

    /** 玩家移动模式 / Player movement mode. */
    enum MoveMode {
        WALKING, VEHICLE, ELYTRA, TRIDENT
    }

    /**
     * 单个玩家的状态快�?/ Single player state snapshot.
     * 仅在玩家的区�?tick 线程上读�?/ Only read/written on the player's region tick thread.
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
     * 在玩�?tick 中调�?�?追踪速度并触发预加载 / Called during player tick.
     *
     * <p>必须在玩家的区域 tick 线程上调�?/ Must be called on the player's region tick thread.
     * ServerPlayer.tick() 保证在区域线程上执行 / ServerPlayer.tick() runs on the region thread.
     */
    public static void onPlayerTick(ServerPlayer player) {
        if (!UnifiedSchedulerConfig.enabled) return;
        if (player.isRemoved() || player.isSpectator()) return;

        final UUID uuid = player.getUUID();
        final PlayerTrackState state = TRACK_STATES.computeIfAbsent(uuid, k -> {
            PlayerTrackState s = new PlayerTrackState();
            s.lastX = player.getX();
            s.lastZ = player.getZ();
            return s;
        });

        state.tickCounter++;

        // �?N tick 采样速度 / Sample velocity every N ticks
        final int interval = Math.max(1, UnifiedSchedulerConfig.sampleIntervalTicks);
        if (state.tickCounter % interval != 0) return;

        // 计算速度 (blocks/tick) �?EMA 平滑避免瞬间抖动 / Calculate velocity with EMA smoothing
        final double dx = player.getX() - state.lastX;
        final double dz = player.getZ() - state.lastZ;
        final double rawVx = dx / interval;
        final double rawVz = dz / interval;
        // EMA alpha=0.3: 70% 历史�?+ 30% 新采�?/ 70% history + 30% new sample
        state.vx = state.vx * 0.7 + rawVx * 0.3;
        state.vz = state.vz * 0.7 + rawVz * 0.3;
        state.speed = Math.sqrt(state.vx * state.vx + state.vz * state.vz);
        state.lastX = player.getX();
        state.lastZ = player.getZ();

        // 检测移动模�?/ Detect movement mode
        state.mode = detectMode(player);

        // 低速时跳过预加�?(减少无效工作) / Skip preloading at low speed (reduce waste)
        if (state.speed < 0.1) {
            state.preloadedThisCycle = 0;
            return;
        }

        // 计算预测位置 / Calculate predicted position
        final int lookAhead = UnifiedSchedulerConfig.lookAheadTicks;
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

        // 计算预加载半�? 基础 + 速度 + 自适应 MSPT / Calculate radius: base + speed + adaptive MSPT
        int radius = calculateRadius(state);

        // 自适应: �?MSPT 时缩减预加载以保�?TPS / Adaptive: reduce preload when MSPT is high
        try {
            final var region = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegion();
            if (region != null && region.getData() != null) {
                final var report = region.getData().getRegionSchedulingHandle().getTickReport5s(System.nanoTime());
                if (report != null) {
                    final double mspt = report.timePerTickData().segmentAll().average() / 1.0E6;
                    if (mspt > 40) radius = Math.max(1, radius / 2);
                    else if (mspt > 30) radius = Math.max(1, (int)(radius * 0.7));
                }
            }
        } catch (Throwable ignored) {}

        // 触发预加�?/ Trigger preload
        final ServerLevel level = (ServerLevel) player.level();
        if (level == null) return;

        final ca.spottedleaf.concurrentutil.util.Priority priority =
                state.speed > UnifiedSchedulerConfig.highSpeedThreshold
                        ? ca.spottedleaf.concurrentutil.util.Priority.HIGH
                        : ca.spottedleaf.concurrentutil.util.Priority.NORMAL;

        scheduleRectPreload(level, predChunkX, predChunkZ, radius, priority);

        if (UnifiedSchedulerConfig.debug) {
            LOGGER.debug("[ChunkPreload] {} mode={} speed={} pred=({},{}), r={}, chunks~{}",
                    player.getGameProfile().name(), state.mode, String.format("%.2f", state.speed),
                    predChunkX, predChunkZ, radius, (2 * radius + 1) * (2 * radius + 1));
        }
    }

    /**
     * 传送事件时调用 �?立即预加载目标区�?/ Called on teleport �?preload destination.
     *
     * <p>可从任意 tick 线程安全调用 / Safe to call from any tick thread.
     * 使用 {@code moonrise$loadChunksAsync} 自动路由到目标区域线�?/
     * Uses moonrise$loadChunksAsync which auto-routes to the correct region thread.
     *
     * @param player 传送的玩家 / The teleporting player
     * @param dest 目标世界 / Destination world
     * @param target 目标坐标 / Target position
     */
    public static void onPlayerTeleport(ServerPlayer player, ServerLevel dest, BlockPos target) {
        if (!UnifiedSchedulerConfig.enabled) return;
        if (dest == null || target == null) return;

        final int centerChunkX = target.getX() >> 4;
        final int centerChunkZ = target.getZ() >> 4;
        final int teleportRadius = UnifiedSchedulerConfig.teleportPreloadRadius;

        // 内圈: BLOCKING 优先级，确保快速可�?/ Inner ring: BLOCKING priority for fast availability
        final int innerRadius = Math.min(3, teleportRadius);
        scheduleRectPreload(dest, centerChunkX, centerChunkZ, innerRadius,
                ca.spottedleaf.concurrentutil.util.Priority.HIGHEST);

        // 外圈: HIGH 优先�?/ Outer ring: HIGH priority
        if (teleportRadius > innerRadius) {
            scheduleRingPreload(dest, centerChunkX, centerChunkZ,
                    innerRadius + 1, teleportRadius,
                    ca.spottedleaf.concurrentutil.util.Priority.HIGH);
        }

        if (UnifiedSchedulerConfig.debug) {
            LOGGER.debug("[ChunkPreload] Teleport preload: dest={}, center=({},{}), r={}",
                    dest.dimension().identifier(), centerChunkX, centerChunkZ, teleportRadius);
        }
    }

    /**
     * 玩家离开时清理状�?/ Clean up state when player leaves.
     */
    public static void onPlayerRemove(ServerPlayer player) {
        TRACK_STATES.remove(player.getUUID());
    }

    /**
     * 清理所有断开连接玩家的状�?(�?MiliMemoryOptimizer 调用) /
     * Clean all disconnected players' states (called by MiliMemoryOptimizer).
     * 可安全从任意线程调用 / Safe to call from any thread.
     *
     * @return 清理的条目数 / Number of entries removed
     */
    public static int cleanupOfflinePlayers() {
        final var server = net.minecraft.server.MinecraftServer.getServer();
        if (server == null) return 0;
        final int before = TRACK_STATES.size();
        TRACK_STATES.keySet().removeIf(uuid -> {
            final var player = server.getPlayerList().getPlayer(uuid);
            return player == null || player.hasDisconnected();
        });
        return before - TRACK_STATES.size();
    }

    // ==================== 内部方法 / Internal methods ====================

    /**
     * 检测玩家当前移动模�?/ Detect player's current movement mode.
     */
    private static MoveMode detectMode(ServerPlayer player) {
        if (player.isFallFlying()) return MoveMode.ELYTRA;
        if (player.isAutoSpinAttack()) return MoveMode.TRIDENT;
        final Entity vehicle = player.getVehicle();
        if (vehicle != null) return MoveMode.VEHICLE;
        return MoveMode.WALKING;
    }

    /**
     * 根据移动模式和速度计算预加载半�?/ Calculate preload radius based on mode and speed.
     */
    private static int calculateRadius(PlayerTrackState state) {
        final int baseRadius = UnifiedSchedulerConfig.basePreloadRadius;
        final int maxRadius = UnifiedSchedulerConfig.maxSpeedRadius;
        final double speedBlocksPerTick = state.speed; // blocks/tick

        double multiplier;
        switch (state.mode) {
            case ELYTRA:  multiplier = UnifiedSchedulerConfig.elytraMultiplier; break;
            case TRIDENT: multiplier = UnifiedSchedulerConfig.tridentMultiplier; break;
            default:      multiplier = 1.0; break;
        }

        // 动态半�?= 基础 + 速度 * 倍数 / Dynamic radius = base + speed * multiplier
        final int dynamic = (int) (baseRadius + speedBlocksPerTick * multiplier * 4);
        return Math.min(dynamic, maxRadius);
    }

    /**
     * 矩形区域预加�?/ Preload a rectangular area of chunks.
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
                    // 回调在目标区域线程执�?/ Callback runs on destination region thread
                    addDelayedTickets(level, minX, maxX, minZ, maxZ);
                });
    }

    /**
     * 环形区域预加�?(不含内圈) / Preload a ring-shaped area (excludes inner circle).
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
     * 为预加载的区块添�?DELAYED ticket 以保持加载状�?/ Add DELAYED tickets to keep preloaded chunks loaded.
     *
     * <p>DELAYED ticket �?5 tick 超时，到期后区块可正常卸�?/ DELAYED ticket has 5 tick timeout,
     * chunks unload normally after expiry.
     *
     * <p>必须在目标区域线程上调用 / Must be called on the target region's thread.
     */
    private static void addDelayedTickets(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
        final var scheduler = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel) level)
                .moonrise$getChunkTaskScheduler();
        final var holderManager = scheduler.chunkHolderManager;
        final int ticketLevel = ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager.FULL_LOADED_TICKET_LEVEL;

        // DELAYED ticket 类型�?comparator �?null，因�?identifier 必须�?null
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
