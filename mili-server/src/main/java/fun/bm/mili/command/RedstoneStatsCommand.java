package fun.bm.mili.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fun.bm.mili.utils.RedstoneStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;

import java.util.Map;

public class RedstoneStatsCommand extends RootNode {
    private static final String PERM_BASE = "mili.admin.redstone-stats";

    public RedstoneStatsCommand() {
        super("redstone-stats", PERM_BASE);
        children(new ResetCommand());
    }

    @Override
    public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
        return source.getSender().hasPermission(PERM_BASE);
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
        CommandSender sender = context.getSender();
        sender.sendMessage(Component.text("=== Redstone Statistics ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.empty());
        Map<String, Object> stats = RedstoneStats.getStats();
        for (Map.Entry<String, Object> entry : stats.entrySet()) {
            sender.sendMessage(Component.text("  " + entry.getKey() + ": ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(entry.getValue()), NamedTextColor.WHITE)));
        }
        return true;
    }

    private static class ResetCommand extends org.leavesmc.leaves.command.LiteralNode {
        ResetCommand() {
            super("reset");
        }

        @Override
        public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
            return source.getSender().hasPermission(PERM_BASE);
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
            RedstoneStats.reset();
            context.getSender().sendMessage(Component.text("Redstone statistics reset.", NamedTextColor.GREEN));
            return true;
        }
    }
}
