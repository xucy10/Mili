package fun.bm.mili.command;

import fun.bm.mili.chunk.MiliChunkSystem;
import fun.bm.mili.utils.*;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class MiliPerfCommand implements CommandExecutor, TabCompleter {

    public void register() {
        org.bukkit.Bukkit.getServer().getCommandMap().register("mili", "miperf",
                new Command("miperf") {
                    @Override
                    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel,
                                           @NotNull String[] args) {
                        return onCommand(sender, this, commandLabel, args);
                    }

                    @Override
                    public @NotNull java.util.List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
                        return onTabComplete(sender, this, alias, args);
                    }
                });
    }

    public void unregister() {
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("mili.admin.perf")) return List.of();
        return List.of();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!sender.hasPermission("mili.admin.perf")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.WHITE + "Mili Performance Monitor" +
                ChatColor.GOLD + " ===");
        sender.sendMessage("");

        sendRegionStats(sender);
        sender.sendMessage("");
        sendChunkStats(sender);
        sender.sendMessage("");
        sendMemoryStats(sender);
        sender.sendMessage("");
        sendCrossRegionStats(sender);
        sender.sendMessage("");
        sendOptimizationStats(sender);
        sender.sendMessage("");
        sendFeatureStats(sender);

        return true;
    }

    private void sendRegionStats(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "-- Region System --");

        Map<String, Object> regionStats = RegionBalancer.getStats();
        for (Map.Entry<String, Object> entry : regionStats.entrySet()) {
            sender.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + ": " +
                    ChatColor.WHITE + entry.getValue());
        }

        Map<String, Object> smartStats = SmartRegionManager.getStats();
        for (Map.Entry<String, Object> entry : smartStats.entrySet()) {
            sender.sendMessage(ChatColor.GRAY + "  [Smart] " + entry.getKey() + ": " +
                    ChatColor.WHITE + entry.getValue());
        }
    }

    private void sendChunkStats(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "-- Chunk System --");

        Map<String, Object> chunkStats = MiliChunkSystem.getStats();
        for (Map.Entry<String, Object> entry : chunkStats.entrySet()) {
            sender.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + ": " +
                    ChatColor.WHITE + entry.getValue());
        }
    }

    private void sendMemoryStats(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "-- Memory System --");

        Map<String, Object> memStats = MemoryOptimizer.getStats();
        for (Map.Entry<String, Object> entry : memStats.entrySet()) {
            sender.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + ": " +
                    ChatColor.WHITE + entry.getValue());
        }
    }

    private void sendCrossRegionStats(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "-- Cross-Region Communication --");

        Map<String, Object> crStats = CrossRegionHelper.getStats();
        for (Map.Entry<String, Object> entry : crStats.entrySet()) {
            sender.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + ": " +
                    ChatColor.WHITE + entry.getValue());
        }
    }

    private void sendOptimizationStats(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "-- Optimizations --");

        printStats(sender, "Entity Dirty", EntityDirtyTracker.getStats());
        printStats(sender, "Dynamic VD", DynamicViewDistanceManager.getStats());
        printStats(sender, "Cross-Dim Teleport", CrossDimensionTeleportQueue.getStats());
        printStats(sender, "Async Pathfinder", AsyncPathfinder.getStats());
        printStats(sender, "Redstone Dirty", RedstoneDirtyTracker.getStats());
        printStats(sender, "Chunk Delta", ChunkDeltaCompressor.getStats());
        printStats(sender, "Light Callback", LightCallbackManager.getStats());
        printStats(sender, "Entity Density", EntityDensityTracker.getStats());
        printStats(sender, "Mmap Storage", MmapRegionStorage.getStats());
        printStats(sender, "Network", NetworkOptimizer.getStats());
        printStats(sender, "TechMC", TechnicalMCOptimizer.getStats());
    }

    private void sendFeatureStats(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "-- Features --");

        printStats(sender, "Redstone Stats", RedstoneStats.getStats());
        printStats(sender, "Player Heatmap", PlayerHeatmap.getStats());
        printStats(sender, "Auto Backup", Map.of(
                "Running", AutoBackupManager.isRunning(),
                "Last Backup", AutoBackupManager.getLastBackupResult()));
        printStats(sender, "Structure Proj", StructureProjectionManager.getStats());
    }

    private void printStats(CommandSender sender, String prefix, Map<String, Object> stats) {
        if (stats.isEmpty()) return;
        sender.sendMessage(ChatColor.AQUA + "  [" + prefix + "]");
        for (Map.Entry<String, Object> entry : stats.entrySet()) {
            sender.sendMessage(ChatColor.GRAY + "    " + entry.getKey() + ": " +
                    ChatColor.WHITE + entry.getValue());
        }
    }
}