package fun.bm.mili.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fun.bm.mili.rust.runtime.RustRuntime;
import fun.bm.mili.utils.CrossRegionHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;

/**
 * /mili rust status — Rust 运行时状态观测命令。
 * 显示 JNI 可用性、rustd daemon 状态、跨区辅助统计。
 */
public class MiliRustStatusCommand extends RootNode {
    private static final String PERM = "mili.admin.rust-status";

    public MiliRustStatusCommand() {
        super("mili-rust-status", PERM);
    }

    @Override
    public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
        return source.getSender().hasPermission(PERM);
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
        CommandSender sender = context.getSender();
        sender.sendMessage(Component.text("=== Mili Rust Runtime ===", NamedTextColor.GOLD));

        // JNI 通道
        sender.sendMessage(Component.text("  JNI: ", NamedTextColor.GRAY)
                .append(Component.text(RustRuntime.jniAvailable() ? "available" : "unavailable",
                        RustRuntime.jniAvailable() ? NamedTextColor.GREEN : NamedTextColor.RED)));

        // Daemon 状态
        RustRuntime.State state = RustRuntime.state();
        sender.sendMessage(Component.text("  Daemon: ", NamedTextColor.GRAY)
                .append(Component.text(state.name(), NamedTextColor.YELLOW)));

        var daemon = RustRuntime.daemon();
        if (daemon != null) {
            sender.sendMessage(Component.text("  Daemon alive: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(daemon.isAlive()), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("  Consecutive failures: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(daemon.consecutiveFailures()), NamedTextColor.WHITE)));
        }

        // 跨区辅助
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("=== CrossRegionHelper ===", NamedTextColor.GOLD));
        var stats = CrossRegionHelper.getStats();
        for (var entry : stats.entrySet()) {
            sender.sendMessage(Component.text("  " + entry.getKey() + ": ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(entry.getValue()), NamedTextColor.WHITE)));
        }
        return true;
    }
}
