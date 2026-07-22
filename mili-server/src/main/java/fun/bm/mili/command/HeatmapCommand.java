package fun.bm.mili.command;

import fun.bm.mili.utils.PlayerHeatmap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HeatmapCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reset", "export");

    public void register() {
        org.bukkit.Bukkit.getServer().getCommandMap().register("mili", "heatmap",
                new Command("heatmap") {
                    @Override
                    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel,
                                           @NotNull String[] args) {
                        return onCommand(sender, this, commandLabel, args);
                    }

                    @Override
                    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
                        return onTabComplete(sender, this, alias, args);
                    }
                });
    }

    public void unregister() {
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("mili.admin.heatmap")) return List.of();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(partial)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("export")) {
            String partial = args[1].toLowerCase();
            return Bukkit.getWorlds().stream().map(World::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial)).toList();
        }
        return List.of();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!sender.hasPermission("mili.admin.heatmap")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendStats(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reset" -> {
                PlayerHeatmap.reset();
                sender.sendMessage(ChatColor.GREEN + "Heatmap data reset.");
            }
            case "export" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /heatmap export <world>");
                    return true;
                }
                try {
                    PlayerHeatmap.exportToFile(args[1]);
                    sender.sendMessage(ChatColor.GREEN + "Heatmap exported for world: " + args[1]);
                } catch (IOException e) {
                    sender.sendMessage(ChatColor.RED + "Export failed: " + e.getMessage());
                }
            }
            default -> sendStats(sender);
        }
        return true;
    }

    private void sendStats(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.WHITE + "Player Activity Heatmap" +
                ChatColor.GOLD + " ===");
        sender.sendMessage("");

        Map<String, Object> stats = PlayerHeatmap.getStats();
        if (stats.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  No data collected yet.");
            return;
        }

        for (Map.Entry<String, Object> entry : stats.entrySet()) {
            sender.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + ": " +
                    ChatColor.WHITE + entry.getValue());
        }
    }
}
