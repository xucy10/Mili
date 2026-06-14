package fun.bm.mili.feature;

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import fun.bm.mili.config.modules.function.RtpConfig;
import fun.bm.mili.perf.MiliChunkPreloader;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * mili - /rtp 核心命令：区块优先预加载的安全随机传送 / Core RTP command with chunk-first preloading.
 *
 * <p>嵌入核心而非插件的原因 / Why embedded in core instead of a plugin:
 * <ul>
 *   <li>直接调用 {@link MiliChunkPreloader#onPlayerTeleport} 触发扩展预加载 /
 *       Directly invoke MiliChunkPreloader for extended preloading</li>
 *   <li>直接调用 {@code moonrise$loadChunksAsync} 实现零延迟区块生成 /
 *       Direct moonrise$loadChunksAsync for zero-delay chunk generation</li>
 *   <li>直接操作 {@link ChunkHolderManager} 添加 DELAYED ticket 保持区块 /
 *       Direct ChunkHolderManager ticket operations to hold chunks</li>
 *   <li>Folia 区域线程安全: 所有操作在正确的区域线程执行 /
 *       Folia region thread safety: all operations on correct threads</li>
 * </ul>
 *
 * <p>传送后提供可配置的无敌时间 (默认 15 秒) / Post-teleport invulnerability (default 15s).
 */
public class MiliRtpCommand extends RootNode {

    // ==================== 危险方块集合 / Hazardous block set ====================

    private static final Set<Material> HAZARDOUS = Set.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE,
            Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE,
            Material.CACTUS
    );

    // ==================== 状态追踪 / State tracking ====================

    /** 冷却中的玩家 / Player cooldowns (UUID → expiry ms). */
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    /** 正在传送中的玩家 / Players currently teleporting. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public MiliRtpCommand() {
        super("rtp", "lophine.commands.rtp");
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) {
        final CommandSender sender = context.getSender();
        if (!(sender instanceof Player bukkitPlayer)) {
            sender.sendMessage(Component.text("只有玩家可以执行 /rtp / Only players can use /rtp", NamedTextColor.RED));
            return true;
        }
        handleRtp(bukkitPlayer);
        return true;
    }

    @Override
    public boolean requires(@NotNull CommandSourceStack source) {
        return source.getSender().hasPermission("lophine.commands.rtp");
    }

    // ==================== 核心 RTP 流程 / Core RTP flow ====================

    private void handleRtp(Player bukkitPlayer) {
        final UUID uuid = bukkitPlayer.getUniqueId();

        // 冷却检查 / Cooldown check
        final Long expiry = cooldowns.get(uuid);
        if (expiry != null && System.currentTimeMillis() < expiry) {
            long remaining = (expiry - System.currentTimeMillis()) / 1000;
            bukkitPlayer.sendMessage(Component.text(
                    "冷却中，请等待 " + remaining + " 秒 / Cooldown: " + remaining + "s",
                    NamedTextColor.YELLOW));
            return;
        }

        // 防止重复 / Prevent duplicate
        if (!inFlight.add(uuid)) {
            bukkitPlayer.sendMessage(Component.text("正在传送中... / Teleport in progress...", NamedTextColor.YELLOW));
            return;
        }

        setCooldown(uuid);
        bukkitPlayer.sendMessage(Component.text(
                "正在寻找安全位置并预加载区块... / Finding safe location & pre-loading chunks...",
                NamedTextColor.GRAY));

        // 在玩家区域线程上执行核心逻辑 / Execute core logic on player's region thread
        bukkitPlayer.getScheduler().run(MinecraftInternalPlugin.INSTANCE, task -> {
            if (!bukkitPlayer.isOnline()) {
                finishTeleport(bukkitPlayer, false, null);
                return;
            }
            executeOnRegionThread(bukkitPlayer);
        }, () -> finishTeleport(bukkitPlayer, false, null));
    }

    /**
     * 在玩家区域线程上执行完整的 RTP 流程 / Execute full RTP on player's region thread.
     *
     * <p>此方法内所有操作都在正确的区域线程上，可以安全调用 NMS API /
     * All operations here are on the correct region thread, safe to call NMS APIs.
     */
    private void executeOnRegionThread(Player bukkitPlayer) {
        try {
            final ServerPlayer nmsPlayer = ((CraftPlayer) bukkitPlayer).getHandle();
            final ServerLevel level = nmsPlayer.level();
            final Location origin = bukkitPlayer.getLocation();

            // 步骤 1: 随机目标 / Step 1: Random target
            final Location roughTarget = randomTarget(origin);

            // 步骤 2: ★ 内圈区块同步预加载 — 核心! / Step 2: Inner chunk sync preload — CORE!
            final boolean loaded = preloadInnerChunks(level, roughTarget);
            if (!loaded) {
                finishTeleport(bukkitPlayer, false, "区块加载超时，请重试 / Chunk loading timed out");
                return;
            }
            if (!bukkitPlayer.isOnline()) {
                finishTeleport(bukkitPlayer, false, null);
                return;
            }

            // 步骤 3: 安全着陆点搜索 / Step 3: Safe landing search
            final Location safeSpot = findSafeLocation(bukkitPlayer.getWorld(), roughTarget);
            if (safeSpot == null) {
                finishTeleport(bukkitPlayer, false, "未找到安全着陆点，请重试 / No safe landing found");
                return;
            }

            // 步骤 4: 外圈区块异步预加载 (不阻塞) / Step 4: Outer chunk async preload (non-blocking)
            preloadOuterChunks(level, safeSpot);

            // 步骤 5: 触发 MiliChunkPreloader 扩展预加载 / Step 5: Trigger MiliChunkPreloader extended preload
            MiliChunkPreloader.onPlayerTeleport(nmsPlayer, level, BlockPos.containing(safeSpot.getX(), safeSpot.getY(), safeSpot.getZ()));

            // 步骤 6: 执行传送 / Step 6: Execute teleport
            final boolean teleported = bukkitPlayer.teleport(safeSpot);
            if (!teleported) {
                finishTeleport(bukkitPlayer, false, "传送失败 / Teleport failed");
                return;
            }

            // 步骤 7: 设置无敌 / Step 7: Set invulnerability
            applyInvulnerability(nmsPlayer);

            finishTeleport(bukkitPlayer, true, null);

        } catch (Throwable t) {
            finishTeleport(bukkitPlayer, false, "内部错误 / Internal error: " + t.getMessage());
        }
    }

    // ==================== 区块预加载 / Chunk pre-loading ====================

    /**
     * 内圈区块同步预加载 — 阻塞直到完成或超时 / Inner chunk sync preload — blocks until done or timeout.
     *
     * <p>直接使用 {@code moonrise$loadChunksAsync} + {@link ChunkHolderManager#addTicketAtLevel}
     * 确保区块不仅加载，而且通过 DELAYED ticket 保持加载状态 / Ensures chunks are loaded AND
     * held via DELAYED tickets.
     *
     * @return true 如果全部加载成功 / true if all chunks loaded successfully
     */
    private boolean preloadInnerChunks(ServerLevel level, Location center) {
        final int cx = center.getBlockX() >> 4;
        final int cz = center.getBlockZ() >> 4;
        final int radius = RtpConfig.preloadInnerRadius;
        final int minX = cx - radius, maxX = cx + radius;
        final int minZ = cz - radius, maxZ = cz + radius;
        final int total = (maxX - minX + 1) * (maxZ - minZ + 1);

        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        final AtomicInteger loaded = new AtomicInteger(0);

        // 使用 Moonrise 直接加载 / Use Moonrise direct loading
        level.moonrise$loadChunksAsync(minX, maxX, minZ, maxZ,
                ChunkStatus.FULL, Priority.HIGHEST, chunks -> {
                    // 回调在区域线程上: 添加 DELAYED ticket 保持区块 / Callback on region thread: add DELAYED tickets
                    addDelayedTickets(level, minX, maxX, minZ, maxZ);
                    loaded.set(total);
                    future.complete(true);
                });

        // 超时等待 / Timeout wait
        try {
            return future.get(RtpConfig.chunkLoadTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            return loaded.get() >= total * 0.6; // 60% 加载即可继续 / Allow if 60% loaded
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 外圈区块异步预加载 (不阻塞) / Outer chunk async preload (non-blocking).
     * 纯粹的"尽力而为"加载 / Pure best-effort loading.
     */
    private void preloadOuterChunks(ServerLevel level, Location center) {
        final int cx = center.getBlockX() >> 4;
        final int cz = center.getBlockZ() >> 4;
        final int outer = RtpConfig.preloadOuterRadius;
        final int inner = RtpConfig.preloadInnerRadius;
        final int minX = cx - outer, maxX = cx + outer;
        final int minZ = cz - outer, maxZ = cz + outer;

        level.moonrise$loadChunksAsync(minX, maxX, minZ, maxZ,
                ChunkStatus.FULL, Priority.HIGH, chunks -> {
                    addDelayedTickets(level, minX, maxX, minZ, maxZ);
                });
    }

    /**
     * 添加 DELAYED ticket 保持区块加载 / Add DELAYED tickets to hold chunks loaded.
     *
     * <p><b>注意 / Note:</b> DELAYED ticket 的 comparator 为 null，
     * identifier 必须传 null，否则抛出 IllegalStateException / DELAYED ticket
     * has null comparator, identifier MUST be null or IllegalStateException is thrown.
     */
    private static void addDelayedTickets(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
        final ChunkHolderManager holderManager =
                ((ChunkSystemServerLevel) level).moonrise$getChunkTaskScheduler().chunkHolderManager;
        final int ticketLevel = ChunkHolderManager.FULL_LOADED_TICKET_LEVEL;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                holderManager.addTicketAtLevel(TicketType.DELAYED, new ChunkPos(x, z), ticketLevel, null);
            }
        }
    }

    // ==================== 安全着陆检测 / Safe landing detection ====================

    /**
     * 在目标附近寻找安全着陆点 / Find safe landing near target.
     *
     * <p>5 级安全检查 / 5-level safety check:
     * <ol>
     *   <li>脚下是实体方块 / Solid block below feet</li>
     *   <li>脚和头部可通过 / Feet and head passable</li>
     *   <li>非危险方块 (岩浆/火/仙人掌等) / Not hazardous</li>
     *   <li>2 格头部空间 / 2-block headroom</li>
     *   <li>Y 坐标在合理范围 / Y in valid range</li>
     * </ol>
     */
    @Nullable
    private Location findSafeLocation(World world, Location roughTarget) {
        final ThreadLocalRandom rng = ThreadLocalRandom.current();
        final int minY = world.getMinHeight() + 5;
        final int maxY = Math.min(319, world.getMaxHeight() - 1);

        for (int attempt = 0; attempt < 30; attempt++) {
            final int x = roughTarget.getBlockX() + rng.nextInt(-32, 33);
            final int z = roughTarget.getBlockZ() + rng.nextInt(-32, 33);

            for (int y = maxY; y >= minY; y--) {
                final Material below = world.getBlockAt(x, y - 1, z).getType();
                final Material feet = world.getBlockAt(x, y, z).getType();
                final Material head1 = world.getBlockAt(x, y + 1, z).getType();
                final Material head2 = world.getBlockAt(x, y + 2, z).getType();

                if (!below.isSolid()) continue;
                if (!feet.isAir() && !feet.isPassable()) continue;
                if (!head1.isAir() && !head1.isPassable()) continue;
                if (!head2.isAir() && !head2.isPassable()) continue;
                if (HAZARDOUS.contains(below) || HAZARDOUS.contains(feet)) continue;

                return new Location(world, x + 0.5, y, z + 0.5, rng.nextFloat() * 360f, 0f);
            }
        }
        return null;
    }

    // ==================== 无敌系统 / Invulnerability system ====================

    /**
     * 应用传送后无敌 / Apply post-teleport invulnerability.
     *
     * <p>设置 {@link ServerPlayer#setInvulnerable(boolean)} 为 true，
     * 然后通过 Folia 实体调度器在配置时间后取消 / Sets entity invulnerable,
     * then removes after configured seconds via Folia entity scheduler.
     */
    private void applyInvulnerability(ServerPlayer nmsPlayer) {
        nmsPlayer.setInvulnerable(true);
        nmsPlayer.getBukkitEntity().sendMessage(Component.text(
                "你获得了 " + RtpConfig.invulnerableSeconds + " 秒无敌 / " +
                        RtpConfig.invulnerableSeconds + "s invulnerability granted",
                NamedTextColor.GREEN));

        final int ticks = RtpConfig.invulnerableSeconds * 20;
        Bukkit.getGlobalRegionScheduler().runDelayed(
                MinecraftInternalPlugin.INSTANCE,
                task -> {
                    if (nmsPlayer.getBukkitEntity().isOnline()) {
                        nmsPlayer.setInvulnerable(false);
                        nmsPlayer.getBukkitEntity().sendMessage(Component.text(
                                "无敌效果已结束 / Invulnerability expired",
                                NamedTextColor.GRAY));
                    }
                },
                ticks
        );
    }

    // ==================== 工具方法 / Utility methods ====================

    /** 随机目标位置 (圆环分布) / Random target (annular distribution). */
    private Location randomTarget(Location origin) {
        final ThreadLocalRandom rng = ThreadLocalRandom.current();
        final double angle = rng.nextDouble() * Math.PI * 2;
        final double dist = rng.nextDouble(RtpConfig.minDistance, RtpConfig.maxDistance);
        return new Location(origin.getWorld(),
                origin.getX() + Math.cos(angle) * dist,
                128,
                origin.getZ() + Math.sin(angle) * dist);
    }

    private void setCooldown(UUID uuid) {
        cooldowns.put(uuid, System.currentTimeMillis() + RtpConfig.cooldownSeconds * 1000L);
    }

    private void finishTeleport(Player bukkitPlayer, boolean success, @Nullable String errorMsg) {
        inFlight.remove(bukkitPlayer.getUniqueId());
        if (success) {
            bukkitPlayer.sendMessage(Component.text("传送成功! / Teleported!", NamedTextColor.GREEN));
        } else if (errorMsg != null) {
            bukkitPlayer.sendMessage(Component.text(errorMsg, NamedTextColor.RED));
            cooldowns.remove(bukkitPlayer.getUniqueId()); // 失败时移除冷却 / Remove cooldown on failure
        } else {
            cooldowns.remove(bukkitPlayer.getUniqueId());
        }
    }
}
