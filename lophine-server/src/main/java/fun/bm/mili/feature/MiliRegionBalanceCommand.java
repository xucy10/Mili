package fun.bm.mili.feature;

import fun.bm.mili.config.modules.misc.RegionBalancerConfig;
import fun.bm.mili.perf.MiliRegionBalancer;
import fun.bm.mili.perf.MiliRegionBalancer.HotspotInfo;
import fun.bm.mili.perf.MiliRegionBalancer.RegionInfo;
import fun.bm.mili.perf.MiliRegionBalancer.ScanResult;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;

import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;

/**
 * mili - /regionbalance 区域均衡命令 / Region balance command.
 *
 * <p>子命令 / Subcommands:
 * <ul>
 *   <li>{@code /regionbalance} — 显示所有区域负载排名 + 热点标记</li>
 *   <li>{@code /regionbalance advise} — 显示具体拆分建议</li>
 *   <li>{@code /regionbalance auto on|off} — 开关自动疏导 (需 OP)</li>
 * </ul>
 */
public class MiliRegionBalanceCommand extends RootNode {

    private static final ThreadLocal<DecimalFormat> F1 = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.0"));

    public MiliRegionBalanceCommand() {
        super("regionbalance", "lophine.commands.regionbalance");
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) {
        final CommandSender sender = context.getSender();
        final String[] args = context.getInput().split("\\s+");
        final String sub = args.length > 1 ? args[1] : "status";

        switch (sub.toLowerCase(java.util.Locale.ROOT)) {
            case "advise" -> sendAdvise(sender);
            case "auto" -> toggleAuto(sender, args);
            default -> sendStatus(sender);
        }
        return true;
    }

    @Override
    public boolean requires(@NotNull CommandSourceStack source) {
        return source.getSender().hasPermission("lophine.commands.regionbalance");
    }

    // ==================== 状态显示 / Status display ====================

    private void sendStatus(CommandSender sender) {
        final ScanResult result = MiliRegionBalancer.getLastResult();
        if (result.allRegions().isEmpty()) {
            sender.sendMessage(Component.text("区域数据尚未收集 (服务器刚启动?) / Region data not yet collected",
                    NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text("===== Region 均衡状态 / Region Balance Status =====")
                .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text(
                "  平均 MSPT / Average MSPT: " + F1.get().format(result.averageMspt()) +
                        "  |  区域数 / Regions: " + result.allRegions().size() +
                        "  |  自动疏导 / Auto-balance: " + (RegionBalancerConfig.autoBalanceEnabled ? "开启 / ON" : "关闭 / OFF"))
                .color(NamedTextColor.GRAY));

        // 热点区域 / Hotspot regions
        if (!result.hotspots().isEmpty()) {
            sender.sendMessage(Component.text("  热点区域 / Hotspot Regions:").color(NamedTextColor.RED));
            for (HotspotInfo h : result.hotspots()) {
                final String tag = h.info().mspt() > result.averageMspt() * 3 ? "[CRIT]" : "[HIGH]";
                sender.sendMessage(Component.text(
                                "    " + tag + " #" + h.info().regionId() + " " + h.info().dimension() +
                                        ": MSPT=" + F1.get().format(h.info().mspt()) +
                                        " (avg=" + F1.get().format(result.averageMspt()) + ")" +
                                        ", " + h.info().entityCount() + " 实体/entities" +
                                        ", " + h.consecutiveCount() + "x 连续/consecutive")
                        .color(h.info().mspt() > result.averageMspt() * 3 ? NamedTextColor.RED : NamedTextColor.GOLD));
            }
        } else {
            sender.sendMessage(Component.text("  无热点区域 / No hotspot regions")
                    .color(NamedTextColor.GREEN));
        }

        // 负载排名 (前 10) / Load ranking (top 10)
        sender.sendMessage(Component.text("  负载排名 (前 10) / Load Ranking (top 10):").color(NamedTextColor.YELLOW));
        List<RegionInfo> sorted = result.allRegions().stream()
                .sorted(Comparator.comparingDouble((RegionInfo r) -> r.mspt()).reversed())
                .limit(10)
                .toList();
        int rank = 1;
        for (RegionInfo r : sorted) {
            String msptStr = r.mspt() >= 0 ? F1.get().format(r.mspt()) : "N/A";
            sender.sendMessage(Component.text(
                            "    " + rank + ". #" + r.regionId() + " " + r.dimension() +
                                    ": MSPT=" + msptStr +
                                    " chunks=" + r.chunkCount() +
                                    " ent=" + r.entityCount() +
                                    " ply=" + r.playerCount())
                    .color(rank <= 3 ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
            rank++;
        }
    }

    // ==================== 建议显示 / Advice display ====================

    private void sendAdvise(CommandSender sender) {
        final ScanResult result = MiliRegionBalancer.getLastResult();
        if (result.hotspots().isEmpty()) {
            sender.sendMessage(Component.text("当前无热点区域，无需建议 / No hotspots, no advice needed",
                    NamedTextColor.GREEN));
            return;
        }

        sender.sendMessage(Component.text("===== 拆分建议 / Split Advice =====")
                .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        for (HotspotInfo h : result.hotspots()) {
            sender.sendMessage(Component.text("  #" + h.info().regionId() + " " + h.info().dimension() +
                            " (MSPT=" + F1.get().format(h.info().mspt()) + "): ")
                    .color(NamedTextColor.YELLOW)
                    .append(Component.text(h.advice()).color(NamedTextColor.WHITE)));
        }

        sender.sendMessage(Component.text("  提示: 使用 /regionbalance auto on 开启自动疏导 (实验性) / " +
                        "Tip: Use /regionbalance auto on to enable auto-balance (experimental)")
                .color(NamedTextColor.GRAY));
    }

    // ==================== 自动疏导开关 / Auto-balance toggle ====================

    private void toggleAuto(CommandSender sender, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("需要 OP 权限 / Requires OP permission", NamedTextColor.RED));
            return;
        }

        final String value = args.length > 2 ? args[2] : "";
        if ("on".equalsIgnoreCase(value)) {
            RegionBalancerConfig.autoBalanceEnabled = true;
            sender.sendMessage(Component.text("自动负载疏导已开启 (实验性) / Auto-balance enabled (experimental)",
                    NamedTextColor.GREEN));
        } else if ("off".equalsIgnoreCase(value)) {
            RegionBalancerConfig.autoBalanceEnabled = false;
            sender.sendMessage(Component.text("自动负载疏导已关闭 / Auto-balance disabled",
                    NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("用法 / Usage: /regionbalance auto on|off",
                    NamedTextColor.GRAY));
        }
    }
}
