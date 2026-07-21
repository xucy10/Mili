package fun.bm.mili.command;

import fun.bm.mili.config.modules.function.AutoBackupConfig;
import fun.bm.mili.utils.AutoBackupManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class BackupCommand implements CommandExecutor {

    public void register() {
        org.bukkit.Bukkit.getServer().getCommandMap().register("mili", "backup",
                new Command("backup") {
                    @Override
                    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel,
                                           @NotNull String[] args) {
                        return onCommand(sender, this, commandLabel, args);
                    }
                });
    }

    public void unregister() {
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!sender.hasPermission("mili.admin.backup")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendStatus(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "now" -> {
                sender.sendMessage(ChatColor.YELLOW + "Starting backup...");
                AutoBackupManager.backupNow().thenAccept(success -> {
                    sender.sendMessage(success
                            ? ChatColor.GREEN + "Backup completed! " + AutoBackupManager.getLastBackupResult()
                            : ChatColor.RED + "Backup failed! " + AutoBackupManager.getLastBackupResult());
                });
            }
            case "status" -> sendStatus(sender);
            case "reload" -> {
                sender.sendMessage(ChatColor.YELLOW + "Backup system reloaded.");
            }
            default -> {
                sender.sendMessage(ChatColor.GOLD + "=== Mili Backup ===");
                sender.sendMessage(ChatColor.GRAY + "  /backup - Show status");
                sender.sendMessage(ChatColor.GRAY + "  /backup now - Force backup");
                sender.sendMessage(ChatColor.GRAY + "  /backup status - Show status");
            }
        }
        return true;
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
