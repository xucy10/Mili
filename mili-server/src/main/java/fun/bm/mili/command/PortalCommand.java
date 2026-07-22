package fun.bm.mili.command;

import fun.bm.mili.portal.PortalLinkManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PortalCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("list", "remove", "clear", "info", "reload");

    public void register() {
        org.bukkit.Bukkit.getServer().getCommandMap().register("mili", "portal",
                new Command("portal") {
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
        if (!sender.hasPermission("mili.admin.portal")) return List.of();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(partial)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            String partial = args[1].toLowerCase();
            return PortalLinkManager.getAllPairs().keySet().stream()
                    .filter(k -> k.toLowerCase().startsWith(partial)).toList();
        }
        return List.of();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!sender.hasPermission("mili.admin.portal")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> {
                Map<String, PortalLinkManager.PortalPair> pairs = PortalLinkManager.getAllPairs();
                sender.sendMessage(ChatColor.GOLD + "=== Portal Links (" + pairs.size() + ") ===");
                for (Map.Entry<String, PortalLinkManager.PortalPair> entry : pairs.entrySet()) {
                    PortalLinkManager.PortalPair p = entry.getValue();
                    sender.sendMessage(ChatColor.GRAY + "  " + ChatColor.WHITE + entry.getKey());
                    sender.sendMessage(ChatColor.GRAY + "    From: " + ChatColor.AQUA +
                            p.getSourceWorld() + " (" + p.getSourceX() + ", " + p.getSourceY() + ", " + p.getSourceZ() + ")");
                    sender.sendMessage(ChatColor.GRAY + "    To:   " + ChatColor.GREEN +
                            p.getDestWorld() + " (" + p.getDestX() + ", " + p.getDestY() + ", " + p.getDestZ() + ")");
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /portal remove <key>");
                    sender.sendMessage(ChatColor.GRAY + "Use /portal list to see keys");
                    return true;
                }
                String key = args[1];
                if (PortalLinkManager.removePair(key)) {
                    sender.sendMessage(ChatColor.GREEN + "Removed portal pair: " + key);
                } else {
                    sender.sendMessage(ChatColor.RED + "Portal pair not found: " + key);
                }
            }
            case "clear" -> {
                int count = PortalLinkManager.getAllPairs().size();
                for (String key : PortalLinkManager.getAllPairs().keySet()) {
                    PortalLinkManager.removePair(key);
                }
                sender.sendMessage(ChatColor.GREEN + "Cleared all " + count + " portal pairs.");
            }
            case "info" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                    return true;
                }
                Location loc = player.getLocation();
                String key = PortalLinkManager.locationKey(loc);
                PortalLinkManager.PortalPair pair = PortalLinkManager.findPair(loc);
                sender.sendMessage(ChatColor.GOLD + "=== Portal Info ===");
                sender.sendMessage(ChatColor.GRAY + "  Position: " + ChatColor.WHITE + key);
                if (pair != null) {
                    sender.sendMessage(ChatColor.GRAY + "  Linked To: " + ChatColor.GREEN +
                            pair.getDestWorld() + " (" + pair.getDestX() + ", " + pair.getDestY() + ", " + pair.getDestZ() + ")");
                } else {
                    sender.sendMessage(ChatColor.GRAY + "  No portal link found at this position.");
                }
                sender.sendMessage(ChatColor.GRAY + "  Search Radius: " + ChatColor.WHITE + PortalLinkManager.getSearchRadius());
            }
            case "reload" -> {
                PortalLinkManager.load();
                sender.sendMessage(ChatColor.GREEN + "Portal links reloaded from disk.");
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Mili Portal ===");
        sender.sendMessage(ChatColor.GRAY + "  /portal list - List all portal pairs");
        sender.sendMessage(ChatColor.GRAY + "  /portal remove <key> - Remove a portal pair");
        sender.sendMessage(ChatColor.GRAY + "  /portal clear - Remove all portal pairs");
        sender.sendMessage(ChatColor.GRAY + "  /portal info - Show portal info at your position");
        sender.sendMessage(ChatColor.GRAY + "  /portal reload - Reload portal links from disk");
    }
}
