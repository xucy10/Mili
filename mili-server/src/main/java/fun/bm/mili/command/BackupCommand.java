package fun.bm.mili.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fun.bm.mili.config.modules.function.AutoBackupConfig;
import fun.bm.mili.utils.AutoBackupManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class BackupCommand extends RootNode {
    private static final String PERM_BASE = "mili.admin.backup";

    public BackupCommand() {
        super("backup", PERM_BASE);
        children(
                new BackupNowCommand(),
                new BackupStatusCommand(),
                new BackupReloadCommand()
        );
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
        sendHelp(context.getSender());
        return true;
    }

    public static void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Mili Backup ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /backup now [world] ", NamedTextColor.GRAY)
                .append(Component.text("- Backup all or specified world", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /backup status ", NamedTextColor.GRAY)
                .append(Component.text("- View backup system status", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /backup reload ", NamedTextColor.GRAY)
                .append(Component.text("- Reload backup config", NamedTextColor.WHITE)));
    }

    private static class BackupNowCommand extends org.leavesmc.leaves.command.LiteralNode {
        BackupNowCommand() {
            super("now");
        }

        @Override
        public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
            return source.getSender().hasPermission(PERM_BASE);
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
            CommandSender sender = context.getSender();
            String worldName = context.getStringOrDefault("world", null);
            if (worldName != null) {
                World w = Bukkit.getWorld(worldName);
                if (w == null) {
                    sender.sendMessage(Component.text("World not found: " + worldName, NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text("Backing up world: " + w.getName() + "...", NamedTextColor.YELLOW));
            } else {
                sender.sendMessage(Component.text("Backing up all worlds...", NamedTextColor.YELLOW));
            }
            AutoBackupManager.backupNow(worldName).thenAccept(success ->
                    sender.sendMessage(success
                            ? Component.text("Backup completed! " + AutoBackupManager.getLastBackupResult(), NamedTextColor.GREEN)
                            : Component.text("Backup failed! " + AutoBackupManager.getLastBackupResult(), NamedTextColor.RED))
            );
            return true;
        }
    }

    private static class BackupStatusCommand extends org.leavesmc.leaves.command.LiteralNode {
        BackupStatusCommand() {
            super("status");
        }

        @Override
        public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
            return source.getSender().hasPermission(PERM_BASE);
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
            CommandSender sender = context.getSender();
            sender.sendMessage(Component.text("=== Auto Backup Status ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("  Enabled: ", NamedTextColor.GRAY).append(Component.text(AutoBackupConfig.enabled, NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("  Running: ", NamedTextColor.GRAY).append(Component.text(AutoBackupManager.isRunning(), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("  Interval: ", NamedTextColor.GRAY).append(Component.text(AutoBackupConfig.intervalMinutes + " min", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("  Max Backups: ", NamedTextColor.GRAY).append(Component.text(AutoBackupConfig.maxBackups, NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("  Last Backup: ", NamedTextColor.GRAY).append(Component.text(
                    AutoBackupManager.getLastBackupTime() > 0
                            ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(AutoBackupManager.getLastBackupTime()))
                            : "Never", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("  Last Result: ", NamedTextColor.GRAY).append(Component.text(AutoBackupManager.getLastBackupResult(), NamedTextColor.WHITE)));
            return true;
        }
    }

    private static class BackupReloadCommand extends org.leavesmc.leaves.command.LiteralNode {
        BackupReloadCommand() {
            super("reload");
        }

        @Override
        public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
            return source.getSender().hasPermission(PERM_BASE);
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
            CommandSender sender = context.getSender();
            AutoBackupManager.stop();
            if (AutoBackupConfig.enabled) {
                AutoBackupManager.start();
            }
            sender.sendMessage(Component.text("Backup system reloaded. Enabled: " +
                    AutoBackupConfig.enabled + ", Interval: " + AutoBackupConfig.intervalMinutes + " min", NamedTextColor.GREEN));
            return true;
        }
    }
}
