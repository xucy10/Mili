package fun.bm.mili.feature;

import fun.bm.mili.perf.MiliAffinityAutoTuner;
import fun.bm.mili.perf.MiliRegionLoadMonitor;
import fun.bm.mili.perf.MiliRegionLoadMonitor.RegionDisplayData;
import fun.bm.mili.perf.MiliTickProfiler;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.openhft.affinity.Affinity;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;

import java.text.DecimalFormat;
import java.util.BitSet;
import java.util.List;

/**
 * mili - /lophine-perf 性能命令 / Performance diagnostics command.
 *
 * <p>子命令 / Subcommands:
 * <ul>
 *   <li>{@code /lophine-perf status} - 显示所有性能模块状态 / Show all perf module settings</li>
 *   <li>{@code /lophine-perf affinity} - CPU 亲和性状态 / CPU affinity status</li>
 *   <li>{@code /lophine-perf regions} - 区域负载摘要 (前 20 区域) / Region load summary (top 20)</li>
 *   <li>{@code /lophine-perf profiler} - Tick 探查器统计 / Tick profiler statistics</li>
 *   <li>{@code /lophine-perf deadlock-stats} - 死锁/慢 tick 警告 / Deadlock/slow tick warnings</li>
 * </ul>
 *
 * <p>别名 / Aliases: {@code /lperf}, {@code /lophineperf}.
 */
public class MiliPerfCommand extends RootNode {
    // DecimalFormat 非线程安全，使用 ThreadLocal 避免并发区域线程冲突
    // DecimalFormat is NOT thread-safe; use ThreadLocal for concurrent region threads
    private static final ThreadLocal<DecimalFormat> F1 = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.0"));
    private static final ThreadLocal<DecimalFormat> F2 = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));

    public MiliPerfCommand() {
        super("lophine-perf", "lophine.commands.perf");
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) {
        io.papermc.paper.command.brigadier.CommandSourceStack stack = context.getSource();
        String[] args = context.getInput().split("\\s+");
        String sub = args.length > 1 ? args[1] : "status";
        switch (sub.toLowerCase(java.util.Locale.ROOT)) {
            case "affinity" -> sendAffinity(stack);
            case "regions" -> sendRegions(stack);
            case "profiler" -> sendProfiler(stack);
            case "deadlock-stats" -> sendDeadlockStats(stack);
            default -> sendStatus(stack);
        }
        return true;
    }

    @Override
    public boolean requires(@NotNull CommandSourceStack source) {
        return source.getSender().hasPermission("lophine.commands.perf");
    }

    private void sendStatus(CommandSourceStack stack) {
        stack.getSender().sendMessage(header("mili 性能模块状态"));
        stack.getSender().sendMessage(key("自动亲和性", MiliAffinityAutoTuner.autoTuneEnabled
                ? "启用" : "关闭", MiliAffinityAutoTuner.autoTuneEnabled
                ? NamedTextColor.GREEN : NamedTextColor.RED));
        stack.getSender().sendMessage(key("亲和性策略", MiliAffinityAutoTuner.strategy, NamedTextColor.GOLD));
        stack.getSender().sendMessage(key("区域负载监控", MiliRegionLoadMonitor.enabled
                ? "启用 (" + MiliRegionLoadMonitor.getTrackedRegionCount() + " regions)"
                : "关闭", MiliRegionLoadMonitor.enabled
                ? NamedTextColor.GREEN : NamedTextColor.RED));
        stack.getSender().sendMessage(key("Tick 探查器", MiliTickProfiler.enabled
                ? "启用 (" + MiliTickProfiler.getTrackedLabelCount() + " labels)"
                : "关闭", MiliTickProfiler.enabled
                ? NamedTextColor.GREEN : NamedTextColor.RED));
        stack.getSender().sendMessage(Component.text("  用法: /lophine-perf {status|affinity|regions|profiler|deadlock-stats}")
                .color(NamedTextColor.GRAY));
    }

    private void sendAffinity(CommandSourceStack stack) {
        stack.getSender().sendMessage(header("mili 亲和性状态"));
        BitSet set = MiliAffinityAutoTuner.getResolvedBitSet();
        if (set == null || set.isEmpty()) {
            stack.getSender().sendMessage(Component.text("  未配置亲和性 (关闭或未检测)")
                    .color(NamedTextColor.YELLOW));
            return;
        }
        stack.getSender().sendMessage(key("策略", MiliAffinityAutoTuner.strategy, NamedTextColor.GOLD));
        stack.getSender().sendMessage(key("核心数", String.valueOf(set.cardinality()), NamedTextColor.WHITE));
        stack.getSender().sendMessage(key("Bitmask", describeBitSet(set), NamedTextColor.AQUA));
        try {
            BitSet current = Affinity.getAffinity();
            stack.getSender().sendMessage(key("当前线程", describeBitSet(current), NamedTextColor.AQUA));
            if (current.equals(set)) {
                stack.getSender().sendMessage(Component.text("  当前线程已绑定到目标核心")
                        .color(NamedTextColor.GREEN));
            } else {
                stack.getSender().sendMessage(Component.text("  当前线程未绑定到目标核心 (command 是从主线程执行的 - 这是正常的)")
                        .color(NamedTextColor.GRAY));
            }
        } catch (Throwable t) {
            stack.getSender().sendMessage(Component.text("  当前线程亲和性: 不可用 (" + t.getMessage() + ")")
                    .color(NamedTextColor.GRAY));
        }
    }

    private void sendRegions(CommandSourceStack stack) {
        stack.getSender().sendMessage(header("mili 区域负载摘要"));
        List<RegionDisplayData> regions = MiliRegionLoadMonitor.getDisplayData();
        if (regions.isEmpty()) {
            stack.getSender().sendMessage(Component.text("  还没有收集到样本 (需要等待 " + MiliRegionLoadMonitor.sampleIntervalTicks + " ticks)")
                    .color(NamedTextColor.YELLOW));
            return;
        }

        // Table header
        stack.getSender().sendMessage(
                Component.text("  等级   ").color(NamedTextColor.GRAY)
                        .append(Component.text("#ID  ").color(NamedTextColor.DARK_GRAY))
                        .append(Component.text("维度                    ").color(NamedTextColor.GRAY))
                        .append(Component.text("区块   实体  玩家  EMA负载").color(NamedTextColor.GRAY))
        );

        int shown = 0;
        for (RegionDisplayData d : regions) {
            // Skip fully idle regions for cleaner display
            if ("IDLE".equals(d.loadTag()) && d.entityCount() == 0 && d.chunkCount() == 0) {
                continue;
            }
            if (shown >= 15) break;

            NamedTextColor loadColor = switch (d.loadTag()) {
                case "CRIT" -> NamedTextColor.RED;
                case "HIGH" -> NamedTextColor.GOLD;
                case "MED" -> NamedTextColor.YELLOW;
                case "LOW" -> NamedTextColor.GREEN;
                default -> NamedTextColor.DARK_GRAY;
            };

            // Hover tooltip with detailed stats
            Component hover = buildRegionHover(d);

            Component line = Component.text("  ")
                    .append(Component.text(d.loadTag()).color(loadColor))
                    .append(Component.text("  #" + d.regionId() + " ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(pad(d.levelName(), 22)).color(NamedTextColor.AQUA))
                    .append(Component.text(pad(String.valueOf(d.chunkCount()), 6)).color(NamedTextColor.WHITE))
                    .append(Component.text(pad(String.valueOf(d.entityCount()), 5)).color(NamedTextColor.WHITE))
                    .append(Component.text(pad(String.valueOf(d.playerCount()), 5)).color(NamedTextColor.WHITE))
                    .append(Component.text(F1.get().format(d.emaLoadScore())).color(loadColor))
                    .hoverEvent(HoverEvent.showText(hover));

            if (d.consecutiveSlow() >= MiliRegionLoadMonitor.slowRegionConsecutive) {
                line = line.append(Component.text(" ⚠").color(NamedTextColor.RED));
            }

            stack.getSender().sendMessage(line);
            shown++;
        }
        if (shown == 0) {
            stack.getSender().sendMessage(Component.text("  所有区域处于空闲状态").color(NamedTextColor.DARK_GRAY));
        }
    }

    /** 构建区域悬停提示组件 / Build region hover tooltip component. */
    private static Component buildRegionHover(RegionDisplayData d) {
        TextComponent.Builder hover = Component.text()
                .append(Component.text("区域 #" + d.regionId()).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("维度: ").color(NamedTextColor.GRAY))
                .append(Component.text(d.levelName()).color(NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("━━━━━━━━━━━━━━━━").color(NamedTextColor.DARK_GRAY))
                .append(Component.newline())
                .append(Component.text("区块: ").color(NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(d.chunkCount())).color(NamedTextColor.WHITE))
                .append(Component.text("  实体: ").color(NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(d.entityCount())).color(NamedTextColor.WHITE))
                .append(Component.text("  玩家: ").color(NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(d.playerCount())).color(NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("━━━━━━━━━━━━━━━━").color(NamedTextColor.DARK_GRAY))
                .append(Component.newline())
                .append(Component.text("EMA 负载: ").color(NamedTextColor.GRAY))
                .append(Component.text(F1.get().format(d.emaLoadScore())).color(NamedTextColor.WHITE))
                .append(Component.text("  峰值: ").color(NamedTextColor.GRAY))
                .append(Component.text(F1.get().format(d.peakLoadScore())).color(NamedTextColor.WHITE));

        if (d.lastMspt() >= 0) {
            hover.append(Component.newline())
                    .append(Component.text("MSPT: ").color(NamedTextColor.GRAY))
                    .append(Component.text(F2.get().format(d.lastMspt()) + " ms").color(msptColor(d.lastMspt())))
                    .append(Component.text("  均值: ").color(NamedTextColor.GRAY))
                    .append(Component.text(F2.get().format(d.emaMspt()) + " ms").color(NamedTextColor.WHITE))
                    .append(Component.text("  峰值: ").color(NamedTextColor.GRAY))
                    .append(Component.text(F2.get().format(d.peakMspt()) + " ms").color(NamedTextColor.RED));
        }
        if (d.lastTps() >= 0) {
            hover.append(Component.newline())
                    .append(Component.text("TPS: ").color(NamedTextColor.GRAY))
                    .append(Component.text(F2.get().format(d.lastTps())).color(tpsColor(d.lastTps())));
        }

        hover.append(Component.newline())
                .append(Component.text("采样数: ").color(NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(d.samplesTaken())).color(NamedTextColor.DARK_GRAY));

        if (d.consecutiveSlow() >= MiliRegionLoadMonitor.slowRegionConsecutive) {
            hover.append(Component.newline())
                    .append(Component.text("⚠ 持续慢区域 (" + d.consecutiveSlow() + " 连续样本)").color(NamedTextColor.RED));
        }

        return hover.build();
    }

    /** 字符串右填充空格 / Right-pad string with spaces. */
    private static String pad(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
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

    private void sendProfiler(CommandSourceStack stack) {
        stack.getSender().sendMessage(header("mili Tick 探查器"));
        if (!MiliTickProfiler.enabled) {
            stack.getSender().sendMessage(Component.text("  探查器未启用 (在 lophine-perf.tick-profiler = true 启用)")
                    .color(NamedTextColor.YELLOW));
            return;
        }
        String stats = MiliTickProfiler.dumpStats();
        for (String line : stats.split("\n")) {
            stack.getSender().sendMessage(Component.text(line).color(NamedTextColor.WHITE));
        }
    }

    private void sendDeadlockStats(CommandSourceStack stack) {
        stack.getSender().sendMessage(header("mili 安全监控"));
        stack.getSender().sendMessage(key("进行中的 region tick", String.valueOf(org.leavesmc.leaves.util.MiliRegionSafetyGuard.getInFlightTickCount()), NamedTextColor.WHITE));
        stack.getSender().sendMessage(key("累计死锁/慢 tick 警告", String.valueOf(org.leavesmc.leaves.util.MiliRegionSafetyGuard.getTotalDeadlockWarnings()), NamedTextColor.WHITE));
    }

    private static Component header(String text) {
        return Component.text("===== " + text + " =====")
                .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
    }

    private static Component key(String name, String value, NamedTextColor color) {
        return Component.text("  " + name + ": ").color(NamedTextColor.WHITE)
                .append(Component.text(value).color(color));
    }

    private static String describeBitSet(BitSet bs) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            if (!first) sb.append(",");
            sb.append(i);
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
