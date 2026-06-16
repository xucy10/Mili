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
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * mili - /rtp 核心命令: 位置池 + 区块预加载 + teleportAsync / Core RTP with pool + preload + teleportAsync.
 *
 * <p>架构 / Architecture:
 * <pre>
 *   玩家进服 → MiliRtpLocationPool 后台预计算位置 (每 2s 填充)
 *   /rtp 执行 → 从池中取位置 (O(1)) → 触发区块预加载 → teleportAsync → 无敌
 *   冷却期间 → 后台自动补充位置池
 * </pre>
 *
 * <p><b>全程非阻塞 / Fully non-blocking:</b>
 * <ul>
 *   <li>位置池: 异步调度器填充 / Pool filled by async scheduler</li>
 *   <li>区块预加载: moonrise$loadChunksAsync (fire-and-forget) / Chunk preload: fire-and-forget</li>
 *   <li>传送: teleportAsync (Folia 原生) / Teleport: Folia-native</li>
 *   <li>无敌: setInvulnerable + GlobalRegionScheduler 定时取消 / Timed removal</li>
 * </ul>
 */
public class MiliRtpCommand extends RootNode {

    // ==================== 状态追踪 / State tracking ====================

    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
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

    // ==================== 核心流程 / Core flow ====================

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

        if (!inFlight.add(uuid)) {
            bukkitPlayer.sendMessage(Component.text("正在传送中... / Teleport in progress...", NamedTextColor.YELLOW));
            return;
        }

        setCooldown(uuid);

        // 从位置池取，池空则随机生成 / Pick from pool, random fallback if empty
        Location target = MiliRtpLocationPool.pick(bukkitPlayer.getWorld());
        if (target == null) {
            target = randomTarget(bukkitPlayer.getWorld(), bukkitPlayer.getLocation());
        }

        bukkitPlayer.sendMessage(Component.text(
                "正在传送... / Teleporting...", NamedTextColor.GRAY));

        // 步骤 1: 触发区块预加载 (fire-and-forget) / Step 1: Trigger chunk preload (fire-and-forget)
        final ServerPlayer nmsPlayer = ((CraftPlayer) bukkitPlayer).getHandle();
        final ServerLevel level = nmsPlayer.level();
        triggerPreload(nmsPlayer, level, target);

        // 步骤 2: 异步加载目标区块并查找安全 Y / Step 2: Async load target chunk & find safe Y
        final int targetX = target.getBlockX();
        final int targetZ = target.getBlockZ();
        final World world = target.getWorld();

        world.getChunkAtAsync(targetX >> 4, targetZ >> 4).thenAccept(chunk -> {
            // 最终安全检查: 确认无附近玩家 / Final safety: confirm no nearby players
            if (RtpConfig.avoidPlayerRadius > 0) {
                for (Player nearby : world.getPlayers()) {
                    double dx = nearby.getX() - targetX;
                    double dz = nearby.getZ() - targetZ;
                    if (dx * dx + dz * dz < (double) RtpConfig.avoidPlayerRadius * RtpConfig.avoidPlayerRadius) {
                        inFlight.remove(uuid);
                        cooldowns.remove(uuid);
                        bukkitPlayer.sendMessage(Component.text(
                                "请重试 / Please retry",
                                NamedTextColor.YELLOW));
                        return;
                    }
                }
            }

            // 区块已加载，查询高度图 / Chunk loaded, query heightmap
            final int safeY = world.getHighestBlockYAt(targetX, targetZ) + 1;
            final Location safeTarget = new Location(world, targetX + 0.5, safeY, targetZ + 0.5,
                    ThreadLocalRandom.current().nextFloat() * 360f, 0f);

            // 步骤 3: teleportAsync (Folia 原生) / Step 3: teleportAsync (Folia-native)
            bukkitPlayer.teleportAsync(safeTarget).thenAccept(success -> {
                inFlight.remove(uuid);
                if (success) {
                    // 步骤 4: 设置无敌 / Step 4: Apply invulnerability
                    applyInvulnerability(nmsPlayer);
                    bukkitPlayer.sendMessage(Component.text("传送成功! / Teleported!", NamedTextColor.GREEN));
                } else {
                    cooldowns.remove(uuid);
                    bukkitPlayer.sendMessage(Component.text("传送失败，请重试 / Teleport failed, please retry", NamedTextColor.RED));
                }
            }).exceptionally(ex -> {
                inFlight.remove(uuid);
                cooldowns.remove(uuid);
                bukkitPlayer.sendMessage(Component.text("传送异常 / Teleport error: " + ex.getMessage(), NamedTextColor.RED));
                return null;
            });
        }).exceptionally(ex -> {
            inFlight.remove(uuid);
            cooldowns.remove(uuid);
            bukkitPlayer.sendMessage(Component.text("区块加载失败 / Chunk load failed: " + ex.getMessage(), NamedTextColor.RED));
            return null;
        });
    }

    // ==================== 区块预加载 / Chunk pre-loading ====================

    /**
     * 触发非阻塞区块预加载 / Trigger non-blocking chunk preload.
     *
     * <p>fire-and-forget: 发起加载请求后立即返回，不等待完成 / Fires and forgets:
     * requests load and returns immediately without waiting.
     * teleportAsync 会自行处理区块加载 / teleportAsync handles its own chunk loading.
     * 此预加载只是给 Moonrise 一个提前量 / This preload just gives Moonrise a head start.
     */
    private void triggerPreload(ServerPlayer nmsPlayer, ServerLevel level, Location target) {
        final int cx = target.getBlockX() >> 4;
        final int cz = target.getBlockZ() >> 4;
        final int inner = RtpConfig.preloadInnerRadius;
        final int outer = RtpConfig.preloadOuterRadius;

        // 内圈 HIGHEST / Inner circle HIGHEST priority
        level.moonrise$loadChunksAsync(
                cx - inner, cx + inner, cz - inner, cz + inner,
                ChunkStatus.FULL, Priority.HIGHEST, chunks -> {
                    addDelayedTickets(level, cx - inner, cx + inner, cz - inner, cz + inner);
                });

        // 外圈 HIGH / Outer circle HIGH priority
        level.moonrise$loadChunksAsync(
                cx - outer, cx + outer, cz - outer, cz + outer,
                ChunkStatus.FULL, Priority.HIGH, chunks -> {
                    addDelayedTickets(level, cx - outer, cx + outer, cz - outer, cz + outer);
                });

        // 触发 MiliChunkPreloader 扩展预加载 / Trigger MiliChunkPreloader extended preload
        MiliChunkPreloader.onPlayerTeleport(nmsPlayer, level,
                BlockPos.containing(target.getX(), target.getY(), target.getZ()));
    }

    /**
     * 添加 DELAYED ticket 保持区块 / Add DELAYED tickets to hold chunks.
     * <b>identifier 必须为 null (DELAYED comparator 为 null) / identifier MUST be null.</b>
     */
    private static void addDelayedTickets(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
        final ChunkHolderManager hm = ((ChunkSystemServerLevel) level).moonrise$getChunkTaskScheduler().chunkHolderManager;
        final int ticketLevel = ChunkHolderManager.FULL_LOADED_TICKET_LEVEL;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                hm.addTicketAtLevel(TicketType.DELAYED, new ChunkPos(x, z), ticketLevel, null);
            }
        }
    }

    // ==================== 无敌系统 / Invulnerability system ====================

    private void applyInvulnerability(ServerPlayer nmsPlayer) {
        nmsPlayer.setInvulnerable(true);
        nmsPlayer.getBukkitEntity().sendMessage(Component.text(
                "你获得了 " + RtpConfig.invulnerableSeconds + " 秒无敌 / " +
                        RtpConfig.invulnerableSeconds + "s invulnerability granted",
                NamedTextColor.GREEN));

        Bukkit.getGlobalRegionScheduler().runDelayed(
                MinecraftInternalPlugin.INSTANCE,
                task -> {
                    if (nmsPlayer.getBukkitEntity().isOnline()) {
                        nmsPlayer.setInvulnerable(false);
                        nmsPlayer.getBukkitEntity().sendMessage(Component.text(
                                "无敌效果已结束 / Invulnerability expired", NamedTextColor.GRAY));
                    }
                },
                RtpConfig.invulnerableSeconds * 20L
        );
    }

    // ==================== 工具方法 / Utility ====================

    private Location randomTarget(World world, Location origin) {
        final ThreadLocalRandom rng = ThreadLocalRandom.current();
        final double angle = rng.nextDouble() * Math.PI * 2;
        final double dist = rng.nextDouble(RtpConfig.minDistance, RtpConfig.maxDistance);
        return new Location(world,
                origin.getX() + Math.cos(angle) * dist,
                128,
                origin.getZ() + Math.sin(angle) * dist);
    }

    private void setCooldown(UUID uuid) {
        cooldowns.put(uuid, System.currentTimeMillis() + RtpConfig.cooldownSeconds * 1000L);
    }
}
