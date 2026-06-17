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
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * mili - /rtp 核心命令: 位置池 + 区域线程验证 + teleportAsync / Core RTP with pool + region-thread validation.
 *
 * <p>架构 / Architecture:
 * <pre>
 *   异步线程 → MiliRtpLocationPool 纯数学填充坐标 (零世界 API)
 *   /rtp 执行 → player.getScheduler().run() → 区域线程验证 → 异步传送
 * </pre>
 *
 * <p><b>关键: 所有世界 API 调用在区域线程 / All world API calls on region thread:</b>
 * <ul>
 *   <li>isChunkGenerated — 仅区域线程安全 / Region thread only</li>
 *   <li>getPlayers + 距离计算 — 区域线程 / Region thread</li>
 *   <li>getTileEntities — 区域线程扫描 / Region thread scan</li>
 *   <li>teleportAsync — Folia 原生 / Folia-native</li>
 * </ul>
 */
public class MiliRtpCommand extends RootNode {

    /** 最大验证尝试次数 / Max validation attempts before giving up. */
    private static final int MAX_ATTEMPTS = 10;

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
        bukkitPlayer.sendMessage(Component.text("正在寻找安全位置... / Finding safe location...", NamedTextColor.GRAY));

        // ★ 在玩家区域线程上执行所有验证和传送 / Execute all validation & teleport on player's region thread
        bukkitPlayer.getScheduler().run(MinecraftInternalPlugin.INSTANCE, task -> {
            if (!bukkitPlayer.isOnline()) {
                inFlight.remove(uuid);
                cooldowns.remove(uuid);
                return;
            }
            executeOnRegionThread(bukkitPlayer);
        }, () -> {
            inFlight.remove(uuid);
            cooldowns.remove(uuid);
        });
    }

    /**
     * 在区域线程上执行: 验证 + 预加载 + 传送 / Execute on region thread: validate + preload + teleport.
     * 从池中取候选位置，验证失败则尝试下一个 / Picks candidates from pool, retries on validation failure.
     */
    private void executeOnRegionThread(Player bukkitPlayer) {
        final UUID uuid = bukkitPlayer.getUniqueId();
        final World world = bukkitPlayer.getWorld();

        // 尝试多个候选位置 / Try multiple candidate locations
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Location candidate = MiliRtpLocationPool.pick(world);
            if (candidate == null) {
                candidate = randomTarget(world, bukkitPlayer.getLocation());
            }

            if (validateLocation(world, candidate.getBlockX(), candidate.getBlockZ())) {
                // 验证通过: 执行传送 / Validation passed: execute teleport
                executeTeleport(bukkitPlayer, candidate);
                return;
            }
            // 验证失败: 尝试下一个 / Validation failed: try next
        }

        // 所有候选都失败 / All candidates failed
        inFlight.remove(uuid);
        cooldowns.remove(uuid);
        bukkitPlayer.sendMessage(Component.text(
                "未找到安全位置，请稍后重试 / No safe location found, please retry later",
                NamedTextColor.YELLOW));
    }

    // ==================== 区域线程安全验证 / Region-thread safety validation ====================

    /**
     * 在区域线程上验证位置安全性 / Validate location safety on region thread.
     *
     * <p>多层检查 / Multi-layer checks:
     * <ol>
     *   <li>区块已生成 (isChunkGenerated) / Chunk is generated</li>
     *   <li>周围区块也已生成 / Surrounding chunks also generated</li>
     *   <li>无附近玩家 / No nearby players</li>
     *   <li>无玩家建筑 (TileEntity 扫描) / No player structures (TileEntity scan)</li>
     * </ol>
     */
    private boolean validateLocation(World world, int x, int z) {
        final int cx = x >> 4;
        final int cz = z >> 4;

        // 检查 1: 区块已生成 / Check 1: Chunk is generated
        if (RtpConfig.requireGenerated) {
            if (!world.isChunkGenerated(cx, cz)) return false;

            // 周围区块检查 / Surrounding chunk check
            final int r = RtpConfig.generatedCheckRadius;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!world.isChunkGenerated(cx + dx, cz + dz)) return false;
                }
            }
        }

        // 检查 2: 无附近玩家 / Check 2: No nearby players
        final double avoidR = RtpConfig.avoidPlayerRadius;
        if (avoidR > 0) {
            final double avoidRSq = avoidR * avoidR;
            for (Player p : world.getPlayers()) {
                double dx = p.getX() - x;
                double dz = p.getZ() - z;
                if (dx * dx + dz * dz < avoidRSq) return false;
            }
        }

        // 检查 3: 无玩家建筑 (扫描已加载区块) / Check 3: No player structures (scan loaded chunks)
        if (hasPlayerStructuresNearby(world, cx, cz, 2)) return false;

        return true;
    }

    /**
     * 扫描多区块范围内的玩家建筑指示方块 / Scan loaded chunks for player structure indicators.
     * 只检查已加载区块 (不触发加载) / Only checks loaded chunks (no load trigger).
     */
    private static boolean hasPlayerStructuresNearby(World world, int cx, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!world.isChunkLoaded(cx + dx, cz + dz)) continue;
                try {
                    final Chunk chunk = world.getChunkAt(cx + dx, cz + dz);
                    for (var te : chunk.getTileEntities()) {
                        final Material type = te.getType();
                        if (isPlayerIndicator(type)) return true;
                    }
                } catch (Throwable ignored) {
                    // 线程安全问题时跳过此区块 / Skip chunk on thread-safety issue
                }
            }
        }
        return false;
    }

    /** 判断方块是否为玩家活动指示器 / Check if block indicates player activity. */
    private static boolean isPlayerIndicator(Material mat) {
        return switch (mat) {
            case CHEST, TRAPPED_CHEST, BARREL,
                 FURNACE, BLAST_FURNACE, SMOKER,
                 CRAFTING_TABLE, ANVIL, ENCHANTING_TABLE, BREWING_STAND,
                 OAK_DOOR, IRON_DOOR, SPRUCE_DOOR, BIRCH_DOOR,
                 JUNGLE_DOOR, ACACIA_DOOR, DARK_OAK_DOOR,
                 CRIMSON_DOOR, WARPED_DOOR,
                 OAK_SIGN, OAK_WALL_SIGN,
                 BEACON, HOPPER, DROPPER, DISPENSER,
                 ENDER_CHEST, SHULKER_BOX -> true;
            default -> mat.name().endsWith("_BED");
        };
    }

    // ==================== 传送执行 / Teleport execution ====================

    private void executeTeleport(Player bukkitPlayer, Location target) {
        final UUID uuid = bukkitPlayer.getUniqueId();
        final ServerPlayer nmsPlayer = ((CraftPlayer) bukkitPlayer).getHandle();
        final ServerLevel level = nmsPlayer.level();
        final World world = target.getWorld();
        final int targetX = target.getBlockX();
        final int targetZ = target.getBlockZ();

        // 步骤 1: 触发区块预加载 (fire-and-forget) / Step 1: Trigger preload (fire-and-forget)
        triggerPreload(nmsPlayer, level, target);

        // 步骤 2: 异步加载目标区块查高度图 → 传送 / Step 2: Async load → find Y → teleport
        world.getChunkAtAsync(targetX >> 4, targetZ >> 4).thenAccept(chunk -> {
            final int safeY = world.getHighestBlockYAt(targetX, targetZ) + 1;
            final Location safeTarget = new Location(world, targetX + 0.5, safeY, targetZ + 0.5,
                    ThreadLocalRandom.current().nextFloat() * 360f, 0f);

            // 步骤 3: teleportAsync (Folia 原生) / Step 3: teleportAsync (Folia-native)
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

    // ==================== 工具 / Utility ====================

    private Location randomTarget(World world, Location origin) {
        final ThreadLocalRandom rng = ThreadLocalRandom.current();
        final double angle = rng.nextDouble() * Math.PI * 2;
        final double dist = rng.nextDouble(RtpConfig.minDistance, RtpConfig.maxDistance);
        return new Location(world,
                origin.getX() + Math.cos(angle) * dist, 128,
                origin.getZ() + Math.sin(angle) * dist);
    }

    private void setCooldown(UUID uuid) {
        cooldowns.put(uuid, System.currentTimeMillis() + RtpConfig.cooldownSeconds * 1000L);
    }
}
