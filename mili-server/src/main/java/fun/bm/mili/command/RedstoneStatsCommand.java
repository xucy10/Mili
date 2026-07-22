package fun.bm.mili.command;

import fun.bm.mili.utils.RedstoneStats;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class RedstoneStatsCommand implements CommandExecutor, TabCompleter {

    public void register() {
        org.bukkit.Bukkit.getServer().getCommandMap().register("mili", "redstone-stats",
                new Command("redstone-stats") {
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
        if (!sender.hasPermission("mili.admin.redstone-stats")) return List.of();
        if (args.length == 1 && "reset".startsWith(args[0].toLowerCase())) {
            return List.of("reset");
        }
        return List.of();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!sender.hasPermission("mili.admin.redstone-stats")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reset")) {
            RedstoneStats.reset();
            sender.sendMessage(ChatColor.GREEN + "Redstone statistics reset.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.WHITE + "Redstone Statistics" +
                ChatColor.GOLD + " ===");
        sender.sendMessage("");

        Map<String, Object> stats = RedstoneStats.getStats();
        for (Map.Entry<String, Object> entry : stats.entrySet()) {
            sender.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + ": " +
                    ChatColor.WHITE + entry.getValue());
        }

        return true;
    }
}
