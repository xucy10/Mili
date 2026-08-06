package fun.bm.mili.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fun.bm.mili.chunk.MiliChunkSystem;
import fun.bm.mili.utils.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.RootNode;

import java.util.Map;

public class MiliPerfCommand extends RootNode {
    private static final String PERM_BASE = "mili.admin.perf";

    public MiliPerfCommand() {
        super("miperf", PERM_BASE);
    }

    @Override
    public boolean requires(@NotNull io.papermc.paper.command.brigadier.CommandSourceStack source) {
        return source.getSender().hasPermission(PERM_BASE);
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
        CommandSender sender = context.getSender();
        sender.sendMessage(Component.text("=== Mili Performance Monitor ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.empty());

        sendRegionStats(sender);
        sender.sendMessage(Component.empty());
        sendChunkStats(sender);
        sender.sendMessage(Component.empty());
        sendMemoryStats(sender);
        sender.sendMessage(Component.empty());
        sendCrossRegionStats(sender);
        sender.sendMessage(Component.empty());
        sendOptimizationStats(sender);
        sender.sendMessage(Component.empty());
        sendFeatureStats(sender);
        return true;
    }

    private static void sendRegionStats(CommandSender sender) {
        sender.sendMessage(Component.text("-- Region System --", NamedTextColor.YELLOW));
        printStats(sender, "", RegionBalancer.getStats());
        printStats(sender, "[Smart] ", SmartRegionManager.getStats());
    }

    private static void sendChunkStats(CommandSender sender) {
        sender.sendMessage(Component.text("-- Chunk System --", NamedTextColor.YELLOW));
        printStats(sender, "", MiliChunkSystem.getStats());
    }

    private static void sendMemoryStats(CommandSender sender) {
        sender.sendMessage(Component.text("-- Memory System --", NamedTextColor.YELLOW));
        printStats(sender, "", MemoryOptimizer.getStats());
    }

    private static void sendCrossRegionStats(CommandSender sender) {
        sender.sendMessage(Component.text("-- Cross-Region Communication --", NamedTextColor.YELLOW));
        printStats(sender, "", CrossRegionHelper.getStats());
    }

    private static void sendOptimizationStats(CommandSender sender) {
        sender.sendMessage(Component.text("-- Optimizations --", NamedTextColor.YELLOW));
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

    private static void sendFeatureStats(CommandSender sender) {
        sender.sendMessage(Component.text("-- Features --", NamedTextColor.YELLOW));
        printStats(sender, "Redstone Stats", RedstoneStats.getStats());
        printStats(sender, "Player Heatmap", PlayerHeatmap.getStats());
        printStats(sender, "Auto Backup", Map.of(
                "Running", AutoBackupManager.isRunning(),
                "Last Backup", AutoBackupManager.getLastBackupResult()));
        printStats(sender, "Structure Proj", StructureProjectionManager.getStats());
    }

    private static void printStats(CommandSender sender, String prefix, Map<String, Object> stats) {
        if (stats.isEmpty()) return;
        if (!prefix.isEmpty()) {
            sender.sendMessage(Component.text("  [" + prefix + "]", NamedTextColor.AQUA));
            for (Map.Entry<String, Object> entry : stats.entrySet()) {
                sender.sendMessage(Component.text("    " + entry.getKey() + ": ", NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(entry.getValue()), NamedTextColor.WHITE)));
            }
        } else {
            for (Map.Entry<String, Object> entry : stats.entrySet()) {
                sender.sendMessage(Component.text("  " + entry.getKey() + ": ", NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(entry.getValue()), NamedTextColor.WHITE)));
            }
        }
    }
}
