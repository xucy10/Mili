package fun.bm.mili.feature;

import ca.spottedleaf.moonrise.common.time.TickData;
import fun.bm.mili.util.FoliaSchedulerUtil;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * mili - /tpsall 命令 | /tpsall command.
 *
 * <p>打印当前区域的全面概览 / Prints a comprehensive overview of the current region:
 * <ul>
 *   <li>当前区域 TPS (5秒窗口) / Current region TPS (5-second window)</li>
 *   <li>当前区域 MSPT 及峰值 / Current region MSPT and peak MSPT</li>
 *   <li>区域统计: 区块、玩家、实体 / Region statistics: chunks, players, entities</li>
 *   <li>区域使用率百分比 / Region utilisation percentage</li>
 * </ul>
 *
 * <p>控制台执行时聚合所有区域数据 / When executed from console, aggregates all region data.
 * 别名 / Aliases: /mspt, /msptall
 *
 * <p>实现说明 / Implementation note: {@link TickRegionScheduler#getCurrentRegion()} 仅在区域
 * tick 线程上返回非 null / only returns non-null on a region tick thread. 控制台命令不在区域线程
 * 运行，因此需要聚合所有区域 / Console commands don't run on region threads, so we aggregate.
 */
public class MiliTpsAllCommand extends RootNode {
    // DecimalFormat 非线程安全，使用 ThreadLocal 避免并发区域线程冲突
    // DecimalFormat is NOT thread-safe; use ThreadLocal for concurrent region threads
    private static final ThreadLocal<DecimalFormat> ONE_DECIMAL = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.0"));
    private static final ThreadLocal<DecimalFormat> TWO_DECIMALS = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));

    public MiliTpsAllCommand() {
        super("tpsall", "lophine.commands.tpsall");
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        sendTpsAll(context.getSender());
        return true;
    }

    @Override
    public boolean requires(@NotNull CommandSourceStack source) {
        return source.getSender().hasPermission("lophine.commands.tpsall");
    }

    /**
     * 根据发送者类型选择执行策略 / Select execution strategy based on sender type.
     *
     * <p>玩家 → 在玩家所在区域线程执行 / Player → run on player's region thread
     * <p>控制台 → 在主线程聚合所有区域数据 / Console → aggregate all regions on main thread
     */
    public void sendTpsAll(@NotNull CommandSender sender) {
        final Consumer<List<Component>> reply = lines -> {
            final Runnable send = () -> {
                for (Component line : lines) {
                    sender.sendMessage(line);
                }
            };
            if (Bukkit.isPrimaryThread()) {
                send.run();
            } else {
                FoliaSchedulerUtil.runTask(send);
            }
        };

        if (sender instanceof Player player) {
            // 玩家: 在其所在区域线程获取该区域数据 / Player: get data for their region
            try {
                player.getScheduler().run(MinecraftInternalPlugin.INSTANCE, (task) -> {
                    List<Component> result = buildRegionReport();
                    reply.accept(result);
                }, null);
            } catch (Throwable t) {
                sender.sendMessage(Component.text("获取 TPS 数据失败 / Failed to get TPS data: " + t.getMessage()).color(NamedTextColor.RED));
            }
        } else {
            // 控制台/RCON: 聚合所有区域数据 / Console/RCON: aggregate all region data
            try {
                List<Component> result = buildAggregatedReport();
                reply.accept(result);
            } catch (Throwable t) {
                sender.sendMessage(Component.text("获取 TPS 数据失败 / Failed to get TPS data: " + t.getMessage()).color(NamedTextColor.RED));
            }
        }
    }

    /**
     * 构建单个区域的 TPS 报告 / Build TPS report for a single region.
     * 必须在区域 tick 线程上调用 / Must be called from a region tick thread.
     */
    private static List<Component> buildRegionReport() {
        final List<Component> lines = new ArrayList<>();
        try {
            final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region =
                    TickRegionScheduler.getCurrentRegion();
            if (region == null || region.getData() == null) {
                lines.add(Component.text("无法获取当前区域 / Cannot get current region").color(NamedTextColor.RED));
                return lines;
            }
            final TickData.TickReportData reportData =
                    region.getData().getRegionSchedulingHandle().getTickReport5s(System.nanoTime());
            final TickRegions.RegionStats regionStats = region.getData().getRegionStats();
            final long regionId = region.getData().id;

            // 标题 / Header
            lines.add(Component.text("===== mili 区域 TPS/MSPT 状态 / Region TPS/MSPT Status =====")
                    .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            lines.add(Component.text("  区域 / Region #" + regionId)
                    .color(NamedTextColor.DARK_GRAY)
                    .append(Component.text("  5s 窗口平均值 / 5s window average").color(NamedTextColor.DARK_GRAY)));

            if (reportData != null) {
                appendTpsMsptLines(lines, reportData);
            } else {
                lines.add(Component.text("  区域数据尚未收集 (服务器启动时间过短?) / Region data not yet collected (server just started?)").color(NamedTextColor.GRAY));
            }

            // 区域统计 / Region stats
            lines.add(Component.text("  区域统计 / Region Stats").color(NamedTextColor.YELLOW));
            appendStatLine(lines, "区块 / Chunks", regionStats.getChunkCount());
            appendStatLine(lines, "实体 / Entities", regionStats.getEntityCount());
            appendStatLine(lines, "玩家 / Players", regionStats.getPlayerCount());
        } catch (Throwable t) {
            lines.add(Component.text("获取 TPS 数据失败 / Failed to get TPS data: " + t.getMessage()).color(NamedTextColor.RED));
        }
        return lines;
    }

    /**
     * 构建聚合所有区域的 TPS 报告 / Build aggregated TPS report across all regions.
     * 可在任意线程调用 / Can be called from any thread.
     */
    private static List<Component> buildAggregatedReport() {
        final List<Component> lines = new ArrayList<>();
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            lines.add(Component.text("服务器尚未初始化 / Server not yet initialized").color(NamedTextColor.RED));
            return lines;
        }

        lines.add(Component.text("===== mili 全局 TPS/MSPT 状态 / Global TPS/MSPT Status =====")
                .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        lines.add(Component.text("  控制台聚合视图 / Console aggregated view")
                .color(NamedTextColor.DARK_GRAY));

        // 聚合所有区域的 MSPT/TPS / Aggregate MSPT/TPS across all regions
        double totalTps = 0;
        double maxMspt = 0;
        double avgMsptSum = 0;
        int regionCount = 0;
        int totalChunks = 0;
        int totalEntities = 0;
        int totalPlayers = 0;

        for (ServerLevel level : server.getAllLevels()) {
            final String dimName = level.dimension().identifier().toString();
            try {
                // 使用 computeForAllRegions 遍历该世界所有区域 / Iterate all regions in this level
                final double[] levelStats = new double[4]; // [tpsSum, msptSum, maxMspt, count]
                final int[] levelCounts = new int[3]; // [chunks, entities, players]

                level.regioniser.computeForAllRegions(region -> {
                    TickRegions.TickRegionData data = region.getData();
                    if (data == null) return;
                    try {
                        TickData.TickReportData report = data.getRegionSchedulingHandle().getTickReport5s(System.nanoTime());
                        if (report != null) {
                            levelStats[0] += report.tpsData().segmentAll().average();
                            double mspt = report.timePerTickData().segmentAll().average() / 1.0E6;
                            levelStats[1] += mspt;
                            double peak = report.timePerTickData().segmentAll().greatest() / 1.0E6;
                            if (peak > levelStats[2]) levelStats[2] = peak;
                            levelStats[3] += 1;
                        }
                    } catch (Throwable ignored) {}
                    TickRegions.RegionStats stats = data.getRegionStats();
                    levelCounts[0] += stats.getChunkCount();
                    levelCounts[1] += stats.getEntityCount();
                    levelCounts[2] += stats.getPlayerCount();
                });

                totalTps += levelStats[0];
                avgMsptSum += levelStats[1];
                if (levelStats[2] > maxMspt) maxMspt = levelStats[2];
                regionCount += (int) levelStats[3];
                totalChunks += levelCounts[0];
                totalEntities += levelCounts[1];
                totalPlayers += levelCounts[2];
            } catch (Throwable t) {
                lines.add(Component.text("  " + dimName + ": 数据获取失败 / data unavailable").color(NamedTextColor.RED));
            }
        }

        if (regionCount > 0) {
            double avgTps = totalTps / regionCount;
            double avgMspt = avgMsptSum / regionCount;

            // TPS / MSPT 行 / TPS / MSPT lines
            lines.add(Component.text("  TPS: ").color(NamedTextColor.GRAY)
                    .append(Component.text(TWO_DECIMALS.get().format(avgTps) + " / 20.00").color(tpsColor(avgTps)))
                    .append(Component.text("  " + tpsBar(avgTps)).color(NamedTextColor.DARK_GRAY)));

            lines.add(Component.text("  MSPT: ").color(NamedTextColor.GRAY)
                    .append(Component.text(TWO_DECIMALS.get().format(avgMspt) + " ms").color(msptColor(avgMspt)))
                    .append(Component.text("  (峰值 / peak " + TWO_DECIMALS.get().format(maxMspt) + " ms)")
                            .color(NamedTextColor.DARK_GRAY)));

            lines.add(Component.text("  \u2501".repeat(20)).color(NamedTextColor.DARK_GRAY));
        } else {
            lines.add(Component.text("  区域数据尚未收集 / Region data not yet collected").color(NamedTextColor.GRAY));
        }

        // 全局统计 / Global stats
        lines.add(Component.text("  全局统计 / Global Stats").color(NamedTextColor.YELLOW));
        appendStatLine(lines, "区域数 / Regions", regionCount);
        appendStatLine(lines, "区块 / Chunks", totalChunks);
        appendStatLine(lines, "实体 / Entities", totalEntities);
        appendStatLine(lines, "玩家 / Players", totalPlayers);

        return lines;
    }

    /**
     * 追加 TPS/MSPT/使用率行到输出 / Append TPS/MSPT/utilisation lines to output.
     */
    private static void appendTpsMsptLines(List<Component> lines, TickData.TickReportData reportData) {
        final TickData.SegmentData tpsData = reportData.tpsData().segmentAll();
        final double tps = tpsData.average();
        final double mspt = reportData.timePerTickData().segmentAll().average() / 1.0E6;
        final double maxMspt = reportData.timePerTickData().segmentAll().greatest() / 1.0E6;
        final double utilisation = reportData.utilisation() * 100.0;

        // TPS 进度条 / TPS bar
        lines.add(Component.text("  TPS: ").color(NamedTextColor.GRAY)
                .append(Component.text(TWO_DECIMALS.get().format(tps) + " / 20.00").color(tpsColor(tps)))
                .append(Component.text("  " + tpsBar(tps)).color(NamedTextColor.DARK_GRAY)));

        // MSPT 含峰值 / MSPT with peak
        lines.add(Component.text("  MSPT: ").color(NamedTextColor.GRAY)
                .append(Component.text(TWO_DECIMALS.get().format(mspt) + " ms").color(msptColor(mspt)))
                .append(Component.text("  (峰值 / peak " + TWO_DECIMALS.get().format(maxMspt) + " ms)")
                        .color(NamedTextColor.DARK_GRAY)));

        // 使用率进度条 / Utilisation bar
        NamedTextColor utilColor = utilisation >= 80 ? NamedTextColor.RED
                : (utilisation >= 50 ? NamedTextColor.YELLOW : NamedTextColor.GREEN);
        lines.add(Component.text("  使用率 / Utilisation: ").color(NamedTextColor.GRAY)
                .append(Component.text(ONE_DECIMAL.get().format(utilisation) + "%").color(utilColor))
                .append(Component.text("  " + utilBar(utilisation)).color(utilColor)));

        lines.add(Component.text("  \u2501".repeat(20)).color(NamedTextColor.DARK_GRAY));
    }

    /** 追加单条统计行 / Append a single stat line. */
    private static void appendStatLine(List<Component> lines, String label, int value) {
        lines.add(Component.text("    " + label + ": ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(value)).color(NamedTextColor.WHITE)));
    }

    /**
     * 构建 TPS 进度条 / Build TPS progress bar: ████░░░░░░
     */
    private static String tpsBar(double tps) {
        int filled = (int) Math.round(tps / 20.0 * 10);
        filled = Math.max(0, Math.min(10, filled));
        return "\u2588".repeat(filled) + "\u2591".repeat(10 - filled);
    }

    /**
     * 构建使用率进度条 / Build utilisation progress bar.
     */
    private static String utilBar(double pct) {
        int filled = (int) Math.round(pct / 100.0 * 10);
        filled = Math.max(0, Math.min(10, filled));
        return "\u2588".repeat(filled) + "\u2591".repeat(10 - filled);
    }

    /** TPS 颜色映射 / TPS color mapping. */
    private static NamedTextColor tpsColor(double tps) {
        if (tps >= 19.5) return NamedTextColor.GREEN;
        if (tps >= 18.0) return NamedTextColor.YELLOW;
        if (tps >= 15.0) return NamedTextColor.GOLD;
        return NamedTextColor.RED;
    }

    /** MSPT 颜色映射 / MSPT color mapping. */
    private static NamedTextColor msptColor(double mspt) {
        if (mspt <= 25.0) return NamedTextColor.GREEN;
        if (mspt <= 40.0) return NamedTextColor.YELLOW;
        if (mspt <= 50.0) return NamedTextColor.GOLD;
        return NamedTextColor.RED;
    }
}
