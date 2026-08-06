package fun.bm.mili.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fun.bm.mili.utils.PlayerHeatmap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;

import java.io.IOException;
import java.util.Map;

public class HeatmapCommand extends RootNode {
    private static final String PERM_BASE = "mili.admin.heatmap";

    public HeatmapCommand() {
        super("heatmap", PERM_BASE);
        children(
                new HeatmapResetCommand(),
                new HeatmapExportCommand()
        );
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
        sendStats(context.getSender());
        return true;
    }

    private static void sendStats(CommandSender sender) {
        sender.sendMessage(Component.text("=== Player Activity Heatmap ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.empty());
        Map<String, Object> stats = PlayerHeatmap.getStats();
        if (stats.isEmpty()) {
            sender.sendMessage(Component.text("  No data collected yet.", NamedTextColor.GRAY));
            return;
        }
        for (Map.Entry<String, Object> entry : stats.entrySet()) {
            sender.sendMessage(Component.text("  " + entry.getKey() + ": ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(entry.getValue()), NamedTextColor.WHITE)));
        }
    }

    private static class HeatmapResetCommand extends org.leavesmc.leaves.command.LiteralNode {
        HeatmapResetCommand() {
            super("reset");
        }

        @Override
        public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
            return source.getSender().hasPermission(PERM_BASE);
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
            PlayerHeatmap.reset();
            context.getSender().sendMessage(Component.text("Heatmap data reset.", NamedTextColor.GREEN));
            return true;
        }
    }

    private static class HeatmapExportCommand extends org.leavesmc.leaves.command.LiteralNode {
        HeatmapExportCommand() {
            super("export");
        }

        @Override
        public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
            return source.getSender().hasPermission(PERM_BASE);
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
            CommandSender sender = context.getSender();
            String worldName = context.getStringOrDefault("world", null);
            if (worldName == null) {
                sender.sendMessage(Component.text("Usage: /heatmap export <world>", NamedTextColor.RED));
                return true;
            }
            try {
                PlayerHeatmap.exportToFile(worldName);
                sender.sendMessage(Component.text("Heatmap exported for world: " + worldName, NamedTextColor.GREEN));
            } catch (IOException e) {
                sender.sendMessage(Component.text("Export failed: " + e.getMessage(), NamedTextColor.RED));
            }
            return true;
        }
    }
}
