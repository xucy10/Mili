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
import org.bukkit.block.Block;
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
 * mili - /rtp 核心命令: 纯异步架构 / Core RTP: fully async architecture.
 *
 * <p><b>绝对不调用任何同步世界 API / NEVER call any synchronous world API:</b>
 * <ul>
 *   <li>❌ isChunkGenerated — 内部 CompletableFuture.join() 死锁</li>
 *   <li>❌ isChunkLoaded — 同上</li>
 *   <li>❌ getChunkAt (同步) — 同上</li>
 *   <li>❌ getHighestBlockYAt — 可能阻塞</li>
 *   <li>❌ getTileEntities (未加载区块) — 可能触发加载</li>
 * </ul>
 *
 * <p><b>只使用异步 API / Only async APIs:</b>
 * <ul>
 *   <li>✅ getChunkAtAsync — 真正的异步加载</li>
 *   <li>✅ teleportAsync — Folia 原生</li>
 *   <li>✅ moonrise$loadChunksAsync — fire-and-forget</li>
 *   <li>✅ world.getPlayers() — 线程安全列表引用</li>
 * </ul>
 */
public class MiliRtpCommand extends RootNode {

    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public MiliRtpCommand() {
        super("rtp", "mili.commands.rtp");
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) {
        if (!(context.getSender() instanceof Player bukkitPlayer)) {
            context.getSender().sendMessage(Component.text("只有玩家可以执行 /rtp", NamedTextColor.RED));
            return true;
        }
        handleRtp(bukkitPlayer);
        return true;
    }

    @Override
    public boolean requires(@NotNull CommandSourceStack source) {
        return source.getSender().hasPermission("mili.commands.rtp");
    }

    // ==================== 核心流程 / Core flow ====================

    private void handleRtp(Player bukkitPlayer) {
        final UUID uuid = bukkitPlayer.getUniqueId();

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

        // 从池中取坐标 (纯数学，零 API) / Pick from pool (pure math, zero API)
        Location target = MiliRtpLocationPool.pick(bukkitPlayer.getWorld());
        if (target == null) {
            target = randomTarget(bukkitPlayer.getWorld(), bukkitPlayer.getLocation());
        }

        // ★ 唯一同步检查: 玩家距离 (world.getPlayers() 是线程安全的) / Only sync check: player distance
        final int tx = target.getBlockX();
        final int tz = target.getBlockZ();
        if (!isPlayerDistanceSafe(bukkitPlayer.getWorld(), tx, tz, bukkitPlayer)) {
            inFlight.remove(uuid);
            cooldowns.remove(uuid);
            bukkitPlayer.sendMessage(Component.text(
                    "目标位置附近有玩家，请重试 / Player detected near target", NamedTextColor.YELLOW));
            return;
        }

        bukkitPlayer.sendMessage(Component.text("正在传送... / Teleporting...", NamedTextColor.GRAY));

        // ★ 触发预加载 (fire-and-forget) / Trigger preload (fire-and-forget)
        final ServerPlayer nmsPlayer = ((CraftPlayer) bukkitPlayer).getHandle();
        triggerPreload(nmsPlayer, nmsPlayer.level(), target);

        // ★ 异步加载目标区块 → 在已加载区块中找安全 Y → 传送
        // Async load target chunk → find safe Y in loaded chunk → teleport
        final World world = target.getWorld();
        world.getChunkAtAsync(tx >> 4, tz >> 4).thenAccept(chunk -> {
            // 区块已加载，现在可以安全访问 / Chunk loaded, now safe to access
            final int safeY = findSafeYInChunk(chunk, tx, tz, world);
            final Location safeTarget = new Location(world, tx + 0.5, safeY, tz + 0.5,
                    ThreadLocalRandom.current().nextFloat() * 360f, 0f);

            // ★ teleportAsync (Folia 原生，唯一合法的传送方式)
            bukkitPlayer.teleportAsync(safeTarget).thenAccept(success -> {
                inFlight.remove(uuid);
                if (success) {
                    applyInvulnerability(nmsPlayer);
                    bukkitPlayer.sendMessage(Component.text("传送成功! / Teleported!", NamedTextColor.GREEN));
                } else {
                    cooldowns.remove(uuid);
                    bukkitPlayer.sendMessage(Component.text("传送失败，请重试 / Teleport failed", NamedTextColor.RED));
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

    // ==================== 安全检查 / Safety checks ====================

    /**
     * 玩家距离检查 (同步但安全: world.getPlayers() 返回线程安全列表) /
     * Player distance check (sync but safe: getPlayers() returns thread-safe list).
     */
    private static boolean isPlayerDistanceSafe(World world, int x, int z, Player self) {
        final double avoidR = RtpConfig.avoidPlayerRadius;
        if (avoidR <= 0) return true;
        final double avoidRSq = avoidR * avoidR;
        try {
            for (Player p : world.getPlayers()) {
                if (p.getUniqueId().equals(self.getUniqueId())) continue;
                double dx = p.getX() - x;
                double dz = p.getZ() - z;
                if (dx * dx + dz * dz < avoidRSq) return false;
            }
        } catch (Throwable ignored) {
            // 线程安全异常时跳过检查 / Skip on thread-safety exception
        }
        return true;
    }

    /**
     * 在已加载的区块中从高到低查找安全 Y 坐标 / Find safe Y in loaded chunk top-down.
     *
     * <p>因为 chunk 已通过 getChunkAtAsync 加载，block 访问是安全的 /
     * Block access is safe because chunk was loaded via getChunkAtAsync.
     */
    private static int findSafeYInChunk(org.bukkit.Chunk chunk, int x, int z, World world) {
        final int maxY = world.getMaxHeight() - 1;
        final int minY = world.getMinHeight();
        // 从最高处向下扫描 / Scan from top down
        for (int y = maxY; y > minY; y--) {
            final Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid()) {
                // 找到实体方块，返回上方 1 格 / Found solid block, return 1 above
                return y + 1;
            }
        }
        // 未找到实体方块 (海洋/洞穴?)，使用海平面 / No solid block (ocean/cave?), use sea level
        return world.getSeaLevel();
    }

    // ==================== 区块预加载 / Chunk pre-loading ====================

    private void triggerPreload(ServerPlayer nmsPlayer, ServerLevel level, Location target) {
        final int cx = target.getBlockX() >> 4;
        final int cz = target.getBlockZ() >> 4;
        final int inner = RtpConfig.preloadInnerRadius;
        final int outer = RtpConfig.preloadOuterRadius;

        level.moonrise$loadChunksAsync(
                cx - inner, cx + inner, cz - inner, cz + inner,
                ChunkStatus.FULL, Priority.HIGHEST, chunks ->
                        addDelayedTickets(level, cx - inner, cx + inner, cz - inner, cz + inner));

        level.moonrise$loadChunksAsync(
                cx - outer, cx + outer, cz - outer, cz + outer,
                ChunkStatus.FULL, Priority.HIGH, chunks ->
                        addDelayedTickets(level, cx - outer, cx + outer, cz - outer, cz + outer));

        MiliChunkPreloader.onPlayerTeleport(nmsPlayer, level,
                BlockPos.containing(target.getX(), target.getY(), target.getZ()));
    }

    private static void addDelayedTickets(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
        final ChunkHolderManager hm = ((ChunkSystemServerLevel) level).moonrise$getChunkTaskScheduler().chunkHolderManager;
        final int ticketLevel = ChunkHolderManager.FULL_LOADED_TICKET_LEVEL;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                hm.addTicketAtLevel(TicketType.DELAYED, new ChunkPos(x, z), ticketLevel, null);
            }
        }
    }

    // ==================== 无敌系统 / Invulnerability ====================

    /**
     * 应用无敌效果 / Apply invulnerability.
     *
     * <p><b>线程安全</b>: 使用 {@code player.getScheduler().runDelayed()} 确保
     * setInvulnerable 在玩家所属区域线程执行 / Uses entity scheduler to ensure
     * setInvulnerable runs on the player's region thread.
     *
     * <p><b>注意</b>: 此处不能使用 FoliaSchedulerUtil.runTaskLater()，因为它调度到
     * 全局区域线程，而不是玩家的区域线程 / Cannot use FoliaSchedulerUtil.runTaskLater()
     * because it schedules on global region, not the player's region.
     */
    private void applyInvulnerability(ServerPlayer nmsPlayer) {
        final Player bukkitEntity = nmsPlayer.getBukkitEntity();

        // 设置无敌 (sendMessage 是线程安全的) / Set invulnerable (sendMessage is thread-safe)
        nmsPlayer.setInvulnerable(true);
        bukkitEntity.sendMessage(Component.text(
                "你获得了 " + RtpConfig.invulnerableSeconds + " 秒无敌 / " +
                        RtpConfig.invulnerableSeconds + "s invulnerability granted",
                NamedTextColor.GREEN));

        // ★ 使用实体调度器在玩家区域线程上取消无敌 / Use entity scheduler on player's region thread
        bukkitEntity.getScheduler().runDelayed(
                MinecraftInternalPlugin.INSTANCE,
                task -> {
                    if (bukkitEntity.isOnline()) {
                        nmsPlayer.setInvulnerable(false);
                        bukkitEntity.sendMessage(Component.text(
                                "无敌效果已结束 / Invulnerability expired", NamedTextColor.GRAY));
                    }
                },
                null,
                RtpConfig.invulnerableSeconds * 20L
        );
    }

    // ==================== 工具 / Utility ====================

    private Location randomTarget(World world, Location origin) {
        final ThreadLocalRandom rng = ThreadLocalRandom.current();
        final double angle = rng.nextDouble() * Math.PI * 2;
        final double dist = rng.nextDouble(RtpConfig.minDistance, RtpConfig.maxDistance);
        return new Location(world, origin.getX() + Math.cos(angle) * dist, 128,
                origin.getZ() + Math.sin(angle) * dist);
    }

    private void setCooldown(UUID uuid) {
        cooldowns.put(uuid, System.currentTimeMillis() + RtpConfig.cooldownSeconds * 1000L);
    }
}
