package fun.bm.mili.command;

import fun.bm.mili.config.modules.function.AutoBackupConfig;
import fun.bm.mili.utils.AutoBackupManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BackupCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("now", "status", "reload");

    public void register() {
        org.bukkit.Bukkit.getServer().getCommandMap().register("mili", "backup",
                new Command("backup") {
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
        if (!sender.hasPermission("mili.admin.backup")) return List.of();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(partial)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("now")) {
            String partial = args[1].toLowerCase();
            return Bukkit.getWorlds().stream().map(World::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial)).toList();
        }
        return List.of();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!sender.hasPermission("mili.admin.backup")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "now" -> {
                String worldName = args.length >= 2 ? args[1] : null;
                if (worldName != null) {
                    World w = Bukkit.getWorld(worldName);
                    if (w == null) {
                        sender.sendMessage(ChatColor.RED + "World not found: " + worldName);
                        return true;
                    }
                    sender.sendMessage(ChatColor.YELLOW + "Backing up world: " + w.getName() + "...");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Backing up all worlds...");
                }
                AutoBackupManager.backupNow(worldName).thenAccept(success -> {
                    sender.sendMessage(success
                            ? ChatColor.GREEN + "Backup completed! " + AutoBackupManager.getLastBackupResult()
                            : ChatColor.RED + "Backup failed! " + AutoBackupManager.getLastBackupResult());
                });
            }
            case "status" -> sendStatus(sender);
            case "reload" -> {
                AutoBackupManager.stop();
                if (AutoBackupConfig.enabled) {
                    AutoBackupManager.start();
                }
                sender.sendMessage(ChatColor.GREEN + "Backup system reloaded. Enabled: " +
                        AutoBackupConfig.enabled + ", Interval: " + AutoBackupConfig.intervalMinutes + " min");
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Mili Backup ===");
        sender.sendMessage(ChatColor.GRAY + "  /backup now " + ChatColor.WHITE + "- 备份所有世界");
        sender.sendMessage(ChatColor.GRAY + "  /backup now <世界名> " + ChatColor.WHITE + "- 备份指定世界");
        sender.sendMessage(ChatColor.GRAY + "  /backup status " + ChatColor.WHITE + "- 查看备份系统状态");
        sender.sendMessage(ChatColor.GRAY + "  /backup reload " + ChatColor.WHITE + "- 重载备份配置");
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.WHITE + "Auto Backup Status" +
                ChatColor.GOLD + " ===");
        sender.sendMessage(ChatColor.GRAY + "  Enabled: " + ChatColor.WHITE + AutoBackupConfig.enabled);
        sender.sendMessage(ChatColor.GRAY + "  Running: " + ChatColor.WHITE + AutoBackupManager.isRunning());
        sender.sendMessage(ChatColor.GRAY + "  Interval: " + ChatColor.WHITE + AutoBackupConfig.intervalMinutes + " min");
        sender.sendMessage(ChatColor.GRAY + "  Max Backups: " + ChatColor.WHITE + AutoBackupConfig.maxBackups);
        sender.sendMessage(ChatColor.GRAY + "  Last Backup: " + ChatColor.WHITE +
                (AutoBackupManager.getLastBackupTime() > 0
                        ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new java.util.Date(AutoBackupManager.getLastBackupTime()))
                        : "Never"));
        sender.sendMessage(ChatColor.GRAY + "  Last Result: " + ChatColor.WHITE +
                AutoBackupManager.getLastBackupResult());
    }
}
