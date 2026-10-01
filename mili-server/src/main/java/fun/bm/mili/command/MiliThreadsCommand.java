package fun.bm.mili.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fun.bm.mili.scheduler.MiliScheduler;
import fun.bm.mili.scheduler.RecurringTask;
import fun.bm.mili.scheduler.RegionTickDag;
import fun.bm.mili.scheduler.TickIntervalBus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;

import java.util.List;

/**
 * 线程治理观测门面。
 *
 * <p>把散在各处的运行时状态汇到一处：共享池与具名池、周期任务健康度、
 * tick 间隔归属、跨 region 依赖图，以及最关键的<b>未纳管线程审计</b> ——
 * 最后一项既是迁移进度条，也是防止回潮的护栏。</p>
 */
public class MiliThreadsCommand extends RootNode {

    private static final String PERM = "mili.admin.threads";

    /** 未纳管线程的展示上限，避免长列表刷屏。 */
    private static final int MAX_UNMANAGED_LINES = 12;

    public MiliThreadsCommand() {
        super("mili-threads", PERM);
    }

    @Override
    public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
        return source.getSender().hasPermission(PERM);
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
        final CommandSender sender = context.getSender();
        sender.sendMessage(Component.text("=== Mili Thread Governance ===", NamedTextColor.GOLD));
        sendPools(sender);
        sendRecurring(sender);
        sender.sendMessage(Component.empty());
        sendTickInterval(sender);
        sendRegionDag(sender);
        sender.sendMessage(Component.empty());
        sendUnmanaged(sender);
        return true;
    }

    private static void sendPools(final CommandSender sender) {
        sender.sendMessage(Component.text("-- Pools --", NamedTextColor.YELLOW));
        for (MiliScheduler.Snapshot snapshot : MiliScheduler.snapshot()) {
            sender.sendMessage(Component.text("  " + snapshot.name(), NamedTextColor.GRAY)
                    .append(Component.text(" [" + snapshot.tier() + "]", NamedTextColor.DARK_GRAY))
                    .append(Component.text("  threads=" + num(snapshot.poolSize())
                                    + " active=" + num(snapshot.active())
                                    + " queued=" + num(snapshot.queued())
                                    + " done=" + num(snapshot.completed()),
                            NamedTextColor.WHITE)));
        }
    }

    private static void sendRecurring(final CommandSender sender) {
        final List<RecurringTask.Handle> tasks = RecurringTask.all();
        sender.sendMessage(Component.text("-- Recurring Tasks (" + tasks.size() + ") --", NamedTextColor.YELLOW));
        if (tasks.isEmpty()) {
            sender.sendMessage(Component.text("  (none)", NamedTextColor.DARK_GRAY));
            return;
        }
        for (RecurringTask.Handle handle : tasks) {
            final RecurringTask.Stats stats = handle.stats();
            final NamedTextColor color = stats.cancelled() ? NamedTextColor.RED
                    : stats.failures() > 0 ? NamedTextColor.YELLOW
                    : NamedTextColor.WHITE;
            sender.sendMessage(Component.text("  " + handle.name(), NamedTextColor.GRAY)
                    .append(Component.text("  " + handle.periodMs() + "ms", NamedTextColor.DARK_GRAY))
                    .append(Component.text("  runs=" + stats.runs()
                                    + " fails=" + stats.failures()
                                    + " last=" + stats.lastDurationMs() + "ms"
                                    + " max=" + stats.maxDurationMs() + "ms"
                                    + (stats.cancelled() ? "  [disabled]" : ""),
                            color)));
        }
    }

    private static void sendTickInterval(final CommandSender sender) {
        final TickIntervalBus.Decision decision = TickIntervalBus.decide();
        sender.sendMessage(Component.text("-- Tick Interval --", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  active=" + (decision.activeName() == null
                        ? "(none - Folia default)" : decision.activeName())
                        + "  interval=" + (decision.activeIntervalNs() / 1_000_000L) + "ms"
                        + "  proposers=" + decision.proposalCount(),
                NamedTextColor.WHITE));
        for (String line : TickIntervalBus.proposals()) {
            sender.sendMessage(Component.text("    " + line, NamedTextColor.DARK_GRAY));
        }
    }

    private static void sendRegionDag(final CommandSender sender) {
        if (!RegionTickDag.isEnabled()) {
            sender.sendMessage(Component.text("-- Region DAG --", NamedTextColor.YELLOW)
                    .append(Component.text("  disabled", NamedTextColor.DARK_GRAY)));
            return;
        }
        final RegionTickDag.DagStats stats = RegionTickDag.stats();
        sender.sendMessage(Component.text("-- Region DAG --", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  wave=" + stats.waveIndex()
                        + "  nodes=" + stats.nodes()
                        + "  ready=" + stats.ready()
                        + "  waiting=" + stats.waiting()
                        + "  lagging=" + stats.lagging()
                        + "  age=" + stats.waveAgeMillis() + "ms",
                NamedTextColor.WHITE));
    }

    private static void sendUnmanaged(final CommandSender sender) {
        final List<String> unmanaged = MiliScheduler.auditUnmanagedThreads();
        sender.sendMessage(Component.text("-- Unmanaged Threads (" + unmanaged.size() + ") --",
                NamedTextColor.YELLOW));
        if (unmanaged.isEmpty()) {
            sender.sendMessage(Component.text("  all Mili threads are governed", NamedTextColor.GREEN));
            return;
        }
        for (int i = 0; i < Math.min(unmanaged.size(), MAX_UNMANAGED_LINES); i++) {
            sender.sendMessage(Component.text("  " + unmanaged.get(i), NamedTextColor.RED));
        }
        if (unmanaged.size() > MAX_UNMANAGED_LINES) {
            sender.sendMessage(Component.text("  ... and " + (unmanaged.size() - MAX_UNMANAGED_LINES) + " more",
                    NamedTextColor.DARK_GRAY));
        }
    }

    /** 负值是"该池实现不暴露此计数"的哨兵，显示为占位符。 */
    private static String num(final long value) {
        return value < 0 ? "-" : Long.toString(value);
    }
}
