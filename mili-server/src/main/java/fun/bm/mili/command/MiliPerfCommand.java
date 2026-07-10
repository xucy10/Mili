package fun.bm.mili.command;

import fun.bm.mili.chunk.MiliChunkSystem;
import fun.bm.mili.utils.*;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class MiliPerfCommand implements CommandExecutor {

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

        return true;
    }

    private void sendRegionStats(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "-- Region System --");

        Map<String, Integer> regionStats = RegionBalancer.getStats();
        for (Map.Entry<String, Integer> entry : regionStats.entrySet()) {
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
}