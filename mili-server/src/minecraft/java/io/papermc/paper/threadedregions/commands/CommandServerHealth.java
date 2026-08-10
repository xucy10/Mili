package io.papermc.paper.threadedregions.commands;

import ca.spottedleaf.moonrise.common.time.TickData;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
// Luminol start - Improved Server Health Report
import java.util.concurrent.TimeUnit;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import org.jetbrains.annotations.NotNull;
// Luminol end

public final class CommandServerHealth extends Command {

    private static final ThreadLocal<DecimalFormat> TWO_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0.00");
    });
    private static final ThreadLocal<DecimalFormat> ONE_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0.0");
    });
    private static final ThreadLocal<DecimalFormat> NO_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0");
    });

    private static final TextColor HEADER = TextColor.color(79, 164, 240);
    private static final TextColor PRIMARY = TextColor.color(48, 145, 237);
    private static final TextColor SECONDARY = TextColor.color(104, 177, 240);
    private static final TextColor INFORMATION = TextColor.color(180, 220, 255); // Luminol start - Improved Server Health Report
    private static final TextColor LIST = TextColor.color(33, 97, 188);

    public CommandServerHealth() {
        super("tps");
        this.setUsage("/<command> [server/region] [lowest regions to display]");
        this.setDescription("Reports information about server health.");
        this.setPermission("bukkit.command.tps");
    }

    private static Component formatRegionInfo(final String prefix, final double util, final double mspt, final double tps,
                                              final boolean newline) {
        return Component.text()
                .append(Component.text(prefix, PRIMARY, TextDecoration.BOLD))
                .append(Component.text(ONE_DECIMAL_PLACES.get().format(util * 100.0), CommandUtil.getUtilisationColourRegion(util)))
                .append(Component.text("% util", PRIMARY))
                .append(Component.text(" | ", SECONDARY))
                .append(Component.text(TWO_DECIMAL_PLACES.get().format(mspt), CommandUtil.getColourForMSPT(mspt)))
                .append(Component.text(" mspt", PRIMARY))
                .append(Component.text(" | ", SECONDARY))
                .append(Component.text(TWO_DECIMAL_PLACES.get().format(tps), CommandUtil.getColourForTPS(tps)))
                .append(Component.text(" TPS" + (newline ? "\n" : ""), PRIMARY))
                .build();
    }

    private static Component formatRegionStats(final TickRegions.RegionStats stats, final boolean newline) {
        return Component.text()
                .append(Component.text("Chunks: ", PRIMARY))
                .append(Component.text(NO_DECIMAL_PLACES.get().format((long)stats.getChunkCount()), INFORMATION))
                .append(Component.text("  Players: ", PRIMARY))
                .append(Component.text(NO_DECIMAL_PLACES.get().format((long)stats.getPlayerCount()), INFORMATION))
                .append(Component.text("  Entities: ", PRIMARY))
                .append(Component.text(NO_DECIMAL_PLACES.get().format((long)stats.getEntityCount()) + (newline ? "\n" : ""), INFORMATION))
                .build();
    }

    private static boolean executeRegion(final CommandSender sender, final String commandLabel, final String[] args) {
        final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region =
                TickRegionScheduler.getCurrentRegion();
        if (region == null) {
            sender.sendMessage(Component.text("You are not in a region currently", NamedTextColor.RED));
            return true;
        }

        final long currTime = System.nanoTime();

        final TickData.TickReportData report15s = region.getData().getRegionSchedulingHandle().getTickReport15s(currTime);
        final TickData.TickReportData report1m = region.getData().getRegionSchedulingHandle().getTickReport1m(currTime);

        final ServerLevel world = region.regioniser.world;
        final ChunkPos chunkCenter = region.getCenterChunk();
        final int centerBlockX = ((chunkCenter.x << 4) | 7);
        final int centerBlockZ = ((chunkCenter.z << 4) | 7);

        final double util15s = report15s.utilisation();
        final double tps15s = report15s.tpsData().segmentAll().average();
        final double mspt15s = report15s.timePerTickData().segmentAll().average() / 1.0E6;

        final double util1m = report1m.utilisation();
        final double tps1m = report1m.tpsData().segmentAll().average();
        final double mspt1m = report1m.timePerTickData().segmentAll().average() / 1.0E6;

        final String location = world.getWorld().getName() + " (" + centerBlockX + ", " + centerBlockZ + ")";

        final Component line = Component.text()
                .append(Component.text("Region around block ", PRIMARY))
                .append(Component.text(location, INFORMATION))
                .append(Component.text(":\n", PRIMARY))

                .append(formatRegionInfo("15s: ", util15s, mspt15s, tps15s, true))
                .append(formatRegionInfo("1m:  ", util1m, mspt1m, tps1m, true))
                .append(formatRegionStats(region.getData().getRegionStats(), false))
                .build();

        sender.sendMessage(line);

        return true;
    }

    private static boolean executeServer(final CommandSender sender, final String commandLabel, final String[] args) {
        final int lowestRegionsCount;
        if (args.length < 2) {
            lowestRegionsCount = 3;
        } else {
            try {
                lowestRegionsCount = Integer.parseInt(args[1]);
            } catch (final NumberFormatException ex) {
                sender.sendMessage(Component.text("Highest utilisation count '" + args[1] + "' must be an integer", NamedTextColor.RED));
                return true;
            }
        }

        final List<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>> regions =
                new ArrayList<>();

        for (final World bukkitWorld : Bukkit.getWorlds()) {
            final ServerLevel world = ((CraftWorld)bukkitWorld).getHandle();
            world.regioniser.computeForAllRegions(regions::add);
        }

        final double minTps;
        final double medianTps;
        final double maxTps;
        double totalUtil = 0.0;

        final DoubleArrayList tpsByRegion = new DoubleArrayList();
        final List<TickData.TickReportData> reportsByRegion = new ArrayList<>();
        final int maxThreadCount = TickRegions.getScheduler().getTotalThreadCount();

        final long currTime = System.nanoTime();
        final TickData.TickReportData globalTickReport = RegionizedServer.getGlobalTickData().getTickReport15s(currTime);

        final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        final MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        final long usedMemory = heapUsage.getUsed() / (1024 * 1024);
        final long maxMemory = heapUsage.getMax() / (1024 * 1024);

        final double memPercent = (double) usedMemory / maxMemory * 100.0;
        final TextColor memColor = memPercent < 60 ? CommandUtil.getUtilisationColourRegion(0.0) : (memPercent < 85 ? NamedTextColor.YELLOW : NamedTextColor.RED);

        final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        final long uptime = runtimeBean.getUptime();
        final String uptimeStr = formatUptime(uptime);

        for (final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region : regions) {
            final TickData.TickReportData report = region.getData().getRegionSchedulingHandle().getTickReport15s(currTime);
            tpsByRegion.add(report == null ? 20.0 : report.tpsData().segmentAll().average());
            reportsByRegion.add(report);
            totalUtil += (report == null ? 0.0 : report.utilisation());
        }

        final double genRate = ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkFullTask.genRate(currTime);
        final double loadRate = ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkFullTask.loadRate(currTime);

        totalUtil += globalTickReport.utilisation();
        final TextColor utilisationColor = CommandUtil.getUtilisationColourRegion(totalUtil / (double)maxThreadCount);

        tpsByRegion.sort(null);
        if (!tpsByRegion.isEmpty()) {
            minTps = tpsByRegion.getDouble(0);
            maxTps = tpsByRegion.getDouble(tpsByRegion.size() - 1);
            final int middle = tpsByRegion.size() >> 1;
            if ((tpsByRegion.size() & 1) == 0) {
                medianTps = (tpsByRegion.getDouble(middle - 1) + tpsByRegion.getDouble(middle)) / 2.0;
            } else {
                medianTps = tpsByRegion.getDouble(middle);
            }
        } else {
            minTps = medianTps = maxTps = 20.0;
        }

        final List<ObjectObjectImmutablePair<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>, TickData.TickReportData>>
                regionsBelowThreshold = new ArrayList<>();

        for (int i = 0, len = regions.size(); i < len; ++i) {
            final TickData.TickReportData report = reportsByRegion.get(i);
            regionsBelowThreshold.add(new ObjectObjectImmutablePair<>(regions.get(i), report));
        }

        regionsBelowThreshold.sort((p1, p2) -> {
            final TickData.TickReportData report1 = p1.right();
            final TickData.TickReportData report2 = p2.right();
            final double util1 = report1 == null ? 0.0 : report1.utilisation();
            final double util2 = report2 == null ? 0.0 : report2.utilisation();
            return Double.compare(util2, util1);
        });

        long totalChunks = 0;
        long totalEntities = 0;

        for (final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region : regions) {
            final TickRegions.RegionStats stats = region.getData().getRegionStats();
            totalChunks += stats.getChunkCount();
            totalEntities += stats.getEntityCount();
        }

        final TextComponent.Builder lowestRegionsBuilder = Component.text();

        if (sender instanceof Player) {
            lowestRegionsBuilder.append(Component.text(" Click to teleport\n", SECONDARY));
        }
        for (int i = 0, len = Math.min(lowestRegionsCount, regionsBelowThreshold.size()); i < len; ++i) {
            final ObjectObjectImmutablePair<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>, TickData.TickReportData>
                    pair = regionsBelowThreshold.get(i);

            final TickData.TickReportData report = pair.right();
            final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region =
                    pair.left();

            if (report == null) continue;

            final ServerLevel world = region.regioniser.world;
            final ChunkPos chunkCenter = region.getCenterChunk();
            if (chunkCenter == null) continue;

            final int centerBlockX = ((chunkCenter.x << 4) | 7);
            final int centerBlockZ = ((chunkCenter.z << 4) | 7);
            final double util = report.utilisation();
            final double tps = report.tpsData().segmentAll().average();
            final double mspt = report.timePerTickData().segmentAll().average() / 1.0E6;

            final int yLoc = 80;
            final String location = world.getWorld().getName() + " (" + centerBlockX + ", " + centerBlockZ + ")";

            final Component line = Component.text()
                    .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                    .append(Component.text("Region at ", PRIMARY))
                    .append(Component.text(location, INFORMATION))
                    .append(Component.text(":\n", PRIMARY))

                    .append(Component.text("    ", PRIMARY))
                    .append(Component.text(ONE_DECIMAL_PLACES.get().format(util * 100.0), CommandUtil.getUtilisationColourRegion(util)))
                    .append(Component.text("% util", PRIMARY))
                    .append(Component.text(" | ", SECONDARY))
                    .append(Component.text(TWO_DECIMAL_PLACES.get().format(mspt), CommandUtil.getColourForMSPT(mspt)))
                    .append(Component.text(" mspt", PRIMARY))
                    .append(Component.text(" | ", SECONDARY))
                    .append(Component.text(TWO_DECIMAL_PLACES.get().format(tps), CommandUtil.getColourForTPS(tps)))
                    .append(Component.text(" TPS\n", PRIMARY))

                    .append(Component.text("    ", PRIMARY))
                    .append(formatRegionStats(region.getData().getRegionStats(), (i + 1) != len))
                    .build()

                    .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/minecraft:execute as @s in " + world.getWorld().getKey().toString() + " run tp " + centerBlockX + ".5 " + yLoc + " " + centerBlockZ + ".5"))
                    .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text("Click to teleport to " + location, SECONDARY)));

            lowestRegionsBuilder.append(line);
        }

        sender.sendMessage(
                Component.text()
                        .append(Component.text("Server Health Report", HEADER, TextDecoration.BOLD))
                        .append(Component.text(" (Uptime: ", SECONDARY))
                        .append(Component.text(uptimeStr, utilisationColor))
                        .append(Component.text(")\n", SECONDARY))

                        .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                        .append(Component.text("Online Players: ", PRIMARY))
                        .append(Component.text(Bukkit.getOnlinePlayers().size(), INFORMATION))
                        .append(Component.newline())


                        .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                        .append(Component.text("Total regions: ", PRIMARY))
                        .append(Component.text(regions.size(), INFORMATION))
                        .append(Component.text(", ", PRIMARY))
                        .append(Component.text("Total Chunks: ", PRIMARY))
                        .append(Component.text(NO_DECIMAL_PLACES.get().format(totalChunks), INFORMATION))
                        .append(Component.text(", ", PRIMARY))
                        .append(Component.text("Total Entities: ", PRIMARY))
                        .append(Component.text(NO_DECIMAL_PLACES.get().format(totalEntities) + "\n", INFORMATION))

                        .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                        .append(Component.text("Utilisation: ", PRIMARY))
                        .append(Component.text(ONE_DECIMAL_PLACES.get().format(totalUtil * 100.0), utilisationColor))
                        .append(Component.text("%", PRIMARY))
                        .append(Component.text(" / ", SECONDARY))
                        .append(Component.text(ONE_DECIMAL_PLACES.get().format(maxThreadCount * 100.0), INFORMATION))
                        .append(Component.text("%\n", PRIMARY))

                        .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                        .append(Component.text("Load rate: ", PRIMARY))
                        .append(Component.text(TWO_DECIMAL_PLACES.get().format(loadRate), INFORMATION))
                        .append(Component.text(", ", PRIMARY))
                        .append(Component.text("Gen rate: ", PRIMARY))
                        .append(Component.text(TWO_DECIMAL_PLACES.get().format(genRate) + "\n", INFORMATION))

                        .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                        .append(Component.text("Memory: ", PRIMARY))
                        .append(Component.text(NO_DECIMAL_PLACES.get().format(usedMemory), memColor))
                        .append(Component.text(" MB", memColor))
                        .append(Component.text(" / ", SECONDARY))
                        .append(Component.text(NO_DECIMAL_PLACES.get().format(maxMemory), INFORMATION))
                        .append(Component.text(" MB", INFORMATION))
                        .append(Component.text(" (", SECONDARY))
                        .append(Component.text(ONE_DECIMAL_PLACES.get().format(memPercent) + "%", memColor))
                        .append(Component.text(")", SECONDARY))
                        .append(Component.newline())

                        .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                        .append(Component.text("Lowest Region TPS: ", PRIMARY))
                        .append(Component.text(TWO_DECIMAL_PLACES.get().format(minTps) + "\n", CommandUtil.getColourForTPS(minTps)))

                        .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                        .append(Component.text("Median Region TPS: ", PRIMARY))
                        .append(Component.text(TWO_DECIMAL_PLACES.get().format(medianTps) + "\n", CommandUtil.getColourForTPS(medianTps)))

                        .append(Component.text(" - ", LIST, TextDecoration.BOLD))
                        .append(Component.text("Highest Region TPS: ", PRIMARY))
                        .append(Component.text(TWO_DECIMAL_PLACES.get().format(maxTps) + "\n", CommandUtil.getColourForTPS(maxTps)))

                        .append(Component.text("Highest ", HEADER, TextDecoration.BOLD))
                        .append(Component.text(Integer.toString(lowestRegionsCount), INFORMATION, TextDecoration.BOLD))
                        .append(Component.text(" utilisation regions\n", HEADER, TextDecoration.BOLD))

                        .append(lowestRegionsBuilder.build())
                        .build()
        );

        return true;
    }

    @Override
    public boolean execute(final CommandSender sender, final String commandLabel, final String[] args) {
        final String type;
        if (args.length < 1) {
            type = "server";
        } else {
            type = args[0];
        }

        switch (type.toLowerCase(Locale.ROOT)) {
            case "server": {
                return executeServer(sender, commandLabel, args);
            }
            case "region": {
                if (!(sender instanceof Entity)) {
                    sender.sendMessage(Component.text("Cannot see current region information as console", NamedTextColor.RED));
                    return true;
                }
                return executeRegion(sender, commandLabel, args);
            }
            default: {
                sender.sendMessage(Component.text("Type '" + args[0] + "' must be one of: [server, region]", NamedTextColor.RED));
                return true;
            }
        }
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String alias, final String[] args) throws IllegalArgumentException {
        if (args.length == 0) {
            if (sender instanceof Entity) {
                return CommandUtil.getSortedList(Arrays.asList("server", "region"));
            } else {
                return CommandUtil.getSortedList(Arrays.asList("server"));
            }
        } else if (args.length == 1) {
            if (sender instanceof Entity) {
                return CommandUtil.getSortedList(Arrays.asList("server", "region"), args[0]);
            } else {
                return CommandUtil.getSortedList(Arrays.asList("server"), args[0]);
            }
        }
        return new ArrayList<>();
    }

    // Luminol start - Improved Server Health Report
    private static @NotNull String formatUptime(long uptimeMillis) {
        long days = TimeUnit.MILLISECONDS.toDays(uptimeMillis);
        long hours = TimeUnit.MILLISECONDS.toHours(uptimeMillis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(uptimeMillis) % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");

        return sb.toString();
    }
    // Luminol end
}
