package io.papermc.paper.threadedregions.commands;

import ca.spottedleaf.moonrise.common.time.TickData;
import fun.bm.mili.utils.MiliI18n;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import org.jetbrains.annotations.NotNull;

/**
 * Refactored /tps command with clean sectioned output and hover tooltips.
 * <p>
 * Layout:</p>
 * <pre>
 * ═══ Server Health Report ═══
 * Uptime: 2d 3h 15m 30s
 *
 * [Performance]  section
 * [World]        section
 * [TPS Range]    section
 * [Memory]       section
 * [Highest Utilisation - Top N]  card list: rank header + aligned metrics + hover stats
 * </pre>
 */
public final class CommandServerHealth extends Command {

    private static final ThreadLocal<DecimalFormat> TWO_DECIMAL_PLACES = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));
    private static final ThreadLocal<DecimalFormat> ONE_DECIMAL_PLACES = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.0"));
    private static final ThreadLocal<DecimalFormat> NO_DECIMAL_PLACES = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0"));

    /** Half-size of a Folia region in chunks (region = (2R+1)x(2R+1) chunks). */
    private static final int REGION_CHUNK_RADIUS = 2;

    private static final TextColor HEADER = TextColor.color(79, 164, 240);
    private static final TextColor SECTION = TextColor.color(255, 170, 0);
    private static final TextColor LABEL = TextColor.color(120, 170, 220);
    private static final TextColor VALUE = NamedTextColor.WHITE;
    private static final TextColor SEPARATOR = TextColor.color(80, 80, 80);
    private static final TextColor DIM = TextColor.color(100, 100, 100);
    private static final TextColor LIST = TextColor.color(33, 97, 188);

    /** Pre-computed render data for one row in the Top-N region list. */
    private record TopRegionRender(
            ServerLevel world,
            ChunkPos chunkCenter,
            int centerBlockX,
            int centerBlockZ,
            TickRegions.RegionStats stats,
            List<String> playerNames,
            Map<String, Long> entityCounts,
            double util, double mspt, double tps,
            String utilStr, String msptStr, String tpsStr
    ) {}

    public CommandServerHealth() {
        super("tps");
        this.setUsage("/<command> [server/region] [lowest regions to display]");
        this.setDescription("Reports information about server health.");
        this.setPermission("bukkit.command.tps");
    }

    // -----------------------------------------------------------------------
    //  Region data helpers
    // -----------------------------------------------------------------------

    /** Collects player names whose chunk lies within {@code chunkRadius} of {@code center}. */
    private static List<String> getPlayerNamesInRegion(@NotNull final ServerLevel world,
                                                        @NotNull final ChunkPos center,
                                                        final int chunkRadius) {
        final List<String> names = new ArrayList<>();
        final int minX = center.x - chunkRadius;
        final int maxX = center.x + chunkRadius;
        final int minZ = center.z - chunkRadius;
        final int maxZ = center.z + chunkRadius;
        for (final ServerPlayer player : world.players()) {
            final BlockPos pos = player.blockPosition();
            final int pcx = pos.getX() >> 4;
            final int pcz = pos.getZ() >> 4;
            if (pcx >= minX && pcx <= maxX && pcz >= minZ && pcz <= maxZ) {
                names.add(player.getScoreboardName());
            }
        }
        return names;
    }

    /** Counts entity types (by descriptionId) within {@code chunkRadius} of {@code center}. */
    private static Map<String, Long> getEntityCountsInRegion(@NotNull final ServerLevel world,
                                                              @NotNull final ChunkPos center,
                                                              final int chunkRadius) {
        final Map<String, Long> counts = new HashMap<>();
        final int minX = center.x - chunkRadius;
        final int maxX = center.x + chunkRadius;
        final int minZ = center.z - chunkRadius;
        final int maxZ = center.z + chunkRadius;
        for (final Entity entity : world.getAllEntities()) {
            final ChunkPos ec = new ChunkPos(entity.blockPosition());
            if (ec.x >= minX && ec.x <= maxX && ec.z >= minZ && ec.z <= maxZ) {
                counts.merge(entity.getType().getDescriptionId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    /** Builds the hover component for the player-count label in a region. */
    private static Component buildPlayerHover(@NotNull final List<String> playerNames) {
        final TextComponent.Builder builder = Component.text()
                .append(Component.text(MiliI18n.get("mili.tpscommand.hover_players_header", "Players in region:"), LABEL));
        if (playerNames.isEmpty()) {
            builder.append(Component.newline())
                    .append(Component.text(MiliI18n.get("mili.tpscommand.hover_players_empty", "  No players"), DIM));
        } else {
            for (final String name : playerNames) {
                builder.append(Component.newline())
                        .append(Component.text(String.format(MiliI18n.get("mili.tpscommand.hover_players_line", "  %s"), name), VALUE));
            }
        }
        return builder.build();
    }

    /**
     * Builds the hover component for the entity-count label in a region. Entity
     * names are sent as translatable components so every viewer's client resolves
     * them in its own language, instead of the server's locale.
     */
    private static Component buildEntityHover(@NotNull final Map<String, Long> entityCounts, final int topN) {
        final List<Map.Entry<String, Long>> sorted = entityCounts.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .limit(topN)
                .toList();

        final long total = entityCounts.values().stream().mapToLong(Long::longValue).sum();

        // The entity name must remain a translatable component (client-localised),
        // so the "  %s: %d" line format is split around %s into a text prefix and
        // a text suffix that gets the count formatted in.
        final String lineFormat = MiliI18n.get("mili.tpscommand.hover_entity_line", "  %s: %d");
        final int nameIdx = lineFormat.indexOf("%s");
        final String linePrefix = nameIdx >= 0 ? lineFormat.substring(0, nameIdx) : "  ";
        final String lineSuffix = nameIdx >= 0 ? lineFormat.substring(nameIdx + 2) : ": %d";

        final TextComponent.Builder builder = Component.text()
                .append(Component.text(String.format(
                        MiliI18n.get("mili.tpscommand.hover_entities_header", "Top %d entities in region:", topN), topN), LABEL))
                .append(Component.newline());

        for (final Map.Entry<String, Long> entry : sorted) {
            builder.append(Component.text(linePrefix, VALUE))
                    .append(Component.translatable(entry.getKey(), VALUE))
                    .append(Component.text(String.format(lineSuffix, entry.getValue()), VALUE))
                    .append(Component.newline());
        }

        builder.append(Component.text(String.format(
                MiliI18n.get("mili.tpscommand.hover_entity_total", "  Total: %d"), total), DIM));

        return builder.build();
    }

    // -----------------------------------------------------------------------
    //  Format helpers
    // -----------------------------------------------------------------------

    /**
     * Left-pads {@code s} with leading spaces so its length equals {@code width}.
     * Used by the dynamic-width Top-N columns so digits line up across rows.
     */
    private static String padLeft(@NotNull final String s, final int width) {
        if (s.length() >= width) {
            return s;
        }
        return " ".repeat(width - s.length()) + s;
    }

    private static Component sectionTitle(@NotNull final String key, @NotNull final String fallback) {
        return Component.text()
                .append(Component.text("[", DIM))
                .append(Component.text(MiliI18n.get(key, fallback), SECTION, TextDecoration.BOLD))
                .append(Component.text("]", DIM))
                .build();
    }

    private static Component labelValue(@NotNull final String labelKey, @NotNull final String labelFallback,
                                         @NotNull final Component value) {
        final String label = MiliI18n.get(labelKey, labelFallback);
        final TextComponent.Builder builder = Component.text();
        if (!label.isEmpty()) {
            builder.append(Component.text(label, LABEL))
                    .append(Component.text(": ", SEPARATOR));
        }
        builder.append(value);
        return builder.build();
    }

    /** Region info line: util% | mspt | tps. */
    private static Component formatRegionInfo(final String prefix, final double util, final double mspt, final double tps) {
        return Component.text()
                .append(Component.text(prefix, LABEL, TextDecoration.BOLD))
                .append(Component.text(ONE_DECIMAL_PLACES.get().format(util * 100.0), CommandUtil.getUtilisationColourRegion(util)))
                .append(Component.text(MiliI18n.get("mili.tpscommand.util_short", "% util"), LABEL))
                .append(Component.text(" | ", SEPARATOR))
                .append(Component.text(TWO_DECIMAL_PLACES.get().format(mspt), CommandUtil.getColourForMSPT(mspt)))
                .append(Component.text(MiliI18n.get("mili.tpscommand.mspt_short", " MSPT"), LABEL))
                .append(Component.text(" | ", SEPARATOR))
                .append(Component.text(TWO_DECIMAL_PLACES.get().format(tps), CommandUtil.getColourForTPS(tps)))
                .append(Component.text(MiliI18n.get("mili.tpscommand.tps_short", " TPS"), LABEL))
                .build();
    }

    /** Region stats line with hover tooltips on Players and Entities counts. */
    private static Component formatRegionStatsWithHover(final int chunkCount, int playerCount, int entityCount,
                                                        @NotNull final Component playerHover,
                                                        @NotNull final Component entityHover,
                                                        final boolean newline) {
        final String nl = newline ? "\n" : "";
        // Use abbreviated labels with hover hints
        final Component playerLabel = Component.text()
                .append(Component.text(MiliI18n.get("mili.tpscommand.players", "Players"), LABEL))
                .append(Component.text(": ", SEPARATOR))
                .append(Component.text(NO_DECIMAL_PLACES.get().format(playerCount), VALUE))
                .hoverEvent(HoverEvent.showText(playerHover))
                .build();

        final Component entityLabel = Component.text()
                .append(Component.text(MiliI18n.get("mili.tpscommand.entities", "Entities"), LABEL))
                .append(Component.text(": ", SEPARATOR))
                .append(Component.text(NO_DECIMAL_PLACES.get().format(entityCount), VALUE))
                .hoverEvent(HoverEvent.showText(entityHover))
                .build();

        return Component.text()
                .append(Component.text(MiliI18n.get("mili.tpscommand.chunks", "Chunks"), LABEL))
                .append(Component.text(": ", SEPARATOR))
                .append(Component.text(NO_DECIMAL_PLACES.get().format(chunkCount), VALUE))
                .append(Component.text("  ", SEPARATOR))
                .append(playerLabel)
                .append(Component.text("  ", SEPARATOR))
                .append(entityLabel)
                .append(Component.text(nl, SEPARATOR))
                .build();
    }

    // -----------------------------------------------------------------------
    //  Command execution
    // -----------------------------------------------------------------------

    private static boolean executeRegion(final CommandSender sender, final String commandLabel, final String[] args) {
        final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region =
                TickRegionScheduler.getCurrentRegion();
        if (region == null) {
            sender.sendMessage(Component.text(MiliI18n.get("mili.tpscommand.not_in_region", "You are not in a region currently"), NamedTextColor.RED));
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

        final List<String> playerNames = getPlayerNamesInRegion(world, chunkCenter, REGION_CHUNK_RADIUS);
        final Map<String, Long> entityCounts = getEntityCountsInRegion(world, chunkCenter, REGION_CHUNK_RADIUS);

        final Component playerHover = buildPlayerHover(playerNames);
        final Component entityHover = buildEntityHover(entityCounts, 5);

        final TickRegions.RegionStats stats = region.getData().getRegionStats();

        sender.sendMessage(
                Component.text()
                        .append(Component.text(MiliI18n.get("mili.tpscommand.header", "Server Health Report"), HEADER, TextDecoration.BOLD))
                        .append(Component.text(" - ", SEPARATOR))
                        .append(Component.text(MiliI18n.get("mili.tpscommand.region_around", "Region around block "), LABEL))
                        .append(Component.text(location, VALUE))
                        .append(Component.text("\n\n", SEPARATOR))

                        .append(formatRegionInfo(MiliI18n.get("mili.tpscommand.tps_15s", "15s") + ": ", util15s, mspt15s, tps15s))
                        .append(Component.newline())
                        .append(formatRegionInfo(MiliI18n.get("mili.tpscommand.tps_1m", "1m") + ": ", util1m, mspt1m, tps1m))
                        .append(Component.newline())
                        .append(formatRegionStatsWithHover(
                                stats.getChunkCount(), stats.getPlayerCount(), stats.getEntityCount(),
                                playerHover, entityHover, false))
                        .build()
        );

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
                sender.sendMessage(Component.text(MiliI18n.get("mili.tpscommand.invalid_count", "Highest utilisation count '%s' must be an integer", args[1]), NamedTextColor.RED));
                return true;
            }
        }

        final List<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>> regions = new ArrayList<>();

        for (final World bukkitWorld : Bukkit.getWorlds()) {
            final ServerLevel world = ((CraftWorld) bukkitWorld).getHandle();
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
        final TextColor utilisationColor = CommandUtil.getUtilisationColourRegion(totalUtil / (double) maxThreadCount);

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
            regionsBelowThreshold.add(new ObjectObjectImmutablePair<>(regions.get(i), reportsByRegion.get(i)));
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
        int totalPlayers = Bukkit.getOnlinePlayers().size();

        for (final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region : regions) {
            final TickRegions.RegionStats stats = region.getData().getRegionStats();
            totalChunks += stats.getChunkCount();
            totalEntities += stats.getEntityCount();
        }

        // ---------- Build top-N region cards with hovers ----------
        // Two-pass rendering: pass 1 collects formatted strings while computing
        // dynamic column widths (util / MSPT / TPS), so the card metrics lines
        // line up regardless of digit count (e.g. when util exceeds 100%). Pass 2
        // renders with the resolved widths.

        final List<TopRegionRender> topRenders = new ArrayList<>();
        int utilMaxLen = 0;
        int msptMaxLen = 0;
        int tpsMaxLen = 0;

        final int topCount = Math.min(lowestRegionsCount, regionsBelowThreshold.size());
        for (int i = 0; i < topCount; ++i) {
            final ObjectObjectImmutablePair<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>, TickData.TickReportData>
                    pair = regionsBelowThreshold.get(i);

            final TickData.TickReportData report = pair.right();
            final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region = pair.left();

            if (report == null) continue;

            final ServerLevel world = region.regioniser.world;
            final ChunkPos chunkCenter = region.getCenterChunk();
            if (chunkCenter == null) continue;

            final int centerBlockX = ((chunkCenter.x << 4) | 7);
            final int centerBlockZ = ((chunkCenter.z << 4) | 7);
            final double util = report.utilisation();
            final double tps = report.tpsData().segmentAll().average();
            final double mspt = report.timePerTickData().segmentAll().average() / 1.0E6;
            final TickRegions.RegionStats stats = region.getData().getRegionStats();

            // RegionStats already knows the player count; skip the world-wide scan
            // entirely for empty regions.
            final List<String> playerNames = stats.getPlayerCount() == 0
                    ? List.of()
                    : getPlayerNamesInRegion(world, chunkCenter, REGION_CHUNK_RADIUS);
            final Map<String, Long> entityCounts = getEntityCountsInRegion(world, chunkCenter, REGION_CHUNK_RADIUS);

            final String utilStr = ONE_DECIMAL_PLACES.get().format(util * 100.0);
            final String msptStr = TWO_DECIMAL_PLACES.get().format(mspt);
            final String tpsStr = TWO_DECIMAL_PLACES.get().format(tps);

            utilMaxLen = Math.max(utilMaxLen, utilStr.length());
            msptMaxLen = Math.max(msptMaxLen, msptStr.length());
            tpsMaxLen = Math.max(tpsMaxLen, tpsStr.length());

            topRenders.add(new TopRegionRender(
                    world, chunkCenter, centerBlockX, centerBlockZ,
                    stats, playerNames, entityCounts,
                    util, mspt, tps,
                    utilStr, msptStr, tpsStr));
        }

        final TextComponent.Builder topRegionsBuilder = Component.text();

        final String cardIndent = "      ";
        for (int i = 0, len = topRenders.size(); i < len; ++i) {
            final TopRegionRender r = topRenders.get(i);

            final String location = r.world().getWorld().getName() + " (" + r.centerBlockX() + ", " + r.centerBlockZ() + ")";

            final String paddedUtil = padLeft(r.utilStr(), utilMaxLen);
            final String paddedMspt = padLeft(r.msptStr(), msptMaxLen);
            final String paddedTps = padLeft(r.tpsStr(), tpsMaxLen);

            final TextColor utilColor = CommandUtil.getUtilisationColourRegion(r.util());
            final TextColor msptColor = CommandUtil.getColourForMSPT(r.mspt());
            final TextColor tpsColor = CommandUtil.getColourForTPS(r.tps());

            final Component playerHover = buildPlayerHover(r.playerNames());
            final Component entityHover = buildEntityHover(r.entityCounts(), 5);

            final Component card = Component.text()
                    // Rank + location header
                    .append(Component.text("  ", LIST, TextDecoration.BOLD))
                    .append(Component.text("#" + (i + 1), SECTION, TextDecoration.BOLD))
                    .append(Component.text("  ", SEPARATOR))
                    .append(Component.text(location, VALUE, TextDecoration.BOLD))
                    .append(Component.text("\n", SEPARATOR))

                    // Metrics line, columns aligned by the pass-1 widths
                    .append(Component.text(cardIndent, SEPARATOR))
                    .append(Component.text(MiliI18n.get("mili.tpscommand.utilisation", "Utilisation") + " ", LABEL))
                    .append(Component.text(paddedUtil, utilColor))
                    .append(Component.text("%", LABEL))
                    .append(Component.text("   ", SEPARATOR))
                    .append(Component.text("MSPT ", LABEL))
                    .append(Component.text(paddedMspt, msptColor))
                    .append(Component.text("   ", SEPARATOR))
                    .append(Component.text("TPS ", LABEL))
                    .append(Component.text(paddedTps, tpsColor))
                    .append(Component.newline())

                    // Stats line with player/entity hovers
                    .append(Component.text(cardIndent, SEPARATOR))
                    .append(formatRegionStatsWithHover(
                            r.stats().getChunkCount(),
                            r.stats().getPlayerCount(),
                            r.stats().getEntityCount(),
                            playerHover, entityHover, (i + 1) != len))
                    .build();

            topRegionsBuilder.append(card);
        }

        // ---------- Assemble final component ----------

        sender.sendMessage(
                Component.text()
                        // Header
                        .append(Component.text("═══ ", DIM))
                        .append(Component.text(MiliI18n.get("mili.tpscommand.header", "Server Health Report"), HEADER, TextDecoration.BOLD))
                        .append(Component.text(" ═══\n", DIM))
                        .append(labelValue("uptime.label", "", Component.text(uptimeStr, utilisationColor)))
                        .append(Component.newline())
                        .append(Component.newline())

                        // ── Performance section ──
                        .append(sectionTitle("mili.tpscommand.section_performance", "Performance"))
                        .append(Component.newline())
                        .append(Component.text("  ", SEPARATOR))
                        .append(labelValue("tps.label",
                                "TPS",
                                Component.text(TWO_DECIMAL_PLACES.get().format(globalTickReport.tpsData().segmentAll().average()), CommandUtil.getColourForTPS(globalTickReport.tpsData().segmentAll().average()))))
                        .append(Component.newline())
                        .append(Component.text("  ", SEPARATOR))
                        .append(labelValue("mili.tpscommand.utilisation", "Utilisation",
                                Component.text(ONE_DECIMAL_PLACES.get().format(totalUtil * 100.0), utilisationColor)
                                        .append(Component.text("%", LABEL))
                                        .append(Component.text(" / ", SEPARATOR))
                                        .append(Component.text(ONE_DECIMAL_PLACES.get().format(maxThreadCount * 100.0), VALUE))
                                        .append(Component.text("%", LABEL))))
                        .append(Component.newline())
                        .append(Component.text("  ", SEPARATOR))
                        .append(labelValue("mili.tpscommand.load_rate", "Load rate",
                                Component.text(TWO_DECIMAL_PLACES.get().format(loadRate), VALUE)))
                        .append(Component.text(" | ", SEPARATOR))
                        .append(labelValue("mili.tpscommand.gen_rate", "Gen rate",
                                Component.text(TWO_DECIMAL_PLACES.get().format(genRate), VALUE)))
                        .append(Component.newline())
                        .append(Component.newline())

                        // ── World section ──
                        .append(sectionTitle("mili.tpscommand.section_world", "World"))
                        .append(Component.newline())
                        .append(Component.text("  ", SEPARATOR))
                        .append(labelValue("mili.tpscommand.online_players", "Players",
                                Component.text(Integer.toString(totalPlayers), VALUE)))
                        .append(Component.text(" | ", SEPARATOR))
                        .append(labelValue("mili.tpscommand.total_entities", "Entities",
                                Component.text(NO_DECIMAL_PLACES.get().format(totalEntities), VALUE)))
                        .append(Component.newline())
                        .append(Component.text("  ", SEPARATOR))
                        .append(labelValue("mili.tpscommand.total_chunks", "Chunks",
                                Component.text(NO_DECIMAL_PLACES.get().format(totalChunks), VALUE)))
                        .append(Component.text(" | ", SEPARATOR))
                        .append(labelValue("mili.tpscommand.total_regions", "Regions",
                                Component.text(Integer.toString(regions.size()), VALUE)))
                        .append(Component.newline())
                        .append(Component.newline())

                        // ── TPS Range section ──
                        .append(sectionTitle("mili.tpscommand.section_tps_range", "TPS Range"))
                        .append(Component.newline())
                        .append(Component.text("  ", SEPARATOR))
                        .append(labelValue("mili.tpscommand.lowest_tps", "Lowest",
                                Component.text(TWO_DECIMAL_PLACES.get().format(minTps), CommandUtil.getColourForTPS(minTps))))
                        .append(Component.text(" | ", SEPARATOR))
                        .append(labelValue("mili.tpscommand.median_tps", "Median",
                                Component.text(TWO_DECIMAL_PLACES.get().format(medianTps), CommandUtil.getColourForTPS(medianTps))))
                        .append(Component.text(" | ", SEPARATOR))
                        .append(labelValue("mili.tpscommand.highest_tps", "Highest",
                                Component.text(TWO_DECIMAL_PLACES.get().format(maxTps), CommandUtil.getColourForTPS(maxTps))))
                        .append(Component.newline())
                        .append(Component.newline())

                        // ── Memory section ──
                        .append(sectionTitle("mili.tpscommand.section_memory", "Memory"))
                        .append(Component.newline())
                        .append(Component.text("  ", SEPARATOR))
                        .append(Component.text(NO_DECIMAL_PLACES.get().format(usedMemory), memColor))
                        .append(Component.text(" MB", memColor))
                        .append(Component.text(" / ", SEPARATOR))
                        .append(Component.text(NO_DECIMAL_PLACES.get().format(maxMemory), VALUE))
                        .append(Component.text(" MB", VALUE))
                        .append(Component.text(" (", SEPARATOR))
                        .append(Component.text(ONE_DECIMAL_PLACES.get().format(memPercent) + "%", memColor))
                        .append(Component.text(")", SEPARATOR))
                        .append(Component.newline())
                        .append(Component.newline())

                        // ── Highest Utilisation section ──
                        .append(sectionTitle("mili.tpscommand.section_top_regions", "Highest Utilisation"))
                        .append(Component.text(" (", SEPARATOR))
                        .append(Component.text("Top ", DIM))
                        .append(Component.text(Integer.toString(Math.min(lowestRegionsCount, regionsBelowThreshold.size())), VALUE, TextDecoration.BOLD))
                        .append(Component.text(")\n", SEPARATOR))
                        .append(topRegionsBuilder)
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
            case "server":
                return executeServer(sender, commandLabel, args);
            case "region":
                if (!(sender instanceof Entity)) {
                    sender.sendMessage(Component.text(MiliI18n.get("mili.tpscommand.console_no_region", "Cannot see current region information as console"), NamedTextColor.RED));
                    return true;
                }
                return executeRegion(sender, commandLabel, args);
            default:
                sender.sendMessage(Component.text(MiliI18n.get("mili.tpscommand.invalid_type", "Type '%s' must be one of: [server, region]", args[0]), NamedTextColor.RED));
                return true;
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

    private static @NotNull String formatUptime(long uptimeMillis) {
        long days = TimeUnit.MILLISECONDS.toDays(uptimeMillis);
        long hours = TimeUnit.MILLISECONDS.toHours(uptimeMillis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(uptimeMillis) % 60;

        final String daySuffix = MiliI18n.get("mili.tpscommand.uptime_days", "d ");
        final String hourSuffix = MiliI18n.get("mili.tpscommand.uptime_hours", "h ");
        final String minuteSuffix = MiliI18n.get("mili.tpscommand.uptime_minutes", "m ");
        final String secondSuffix = MiliI18n.get("mili.tpscommand.uptime_seconds", "s");

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(daySuffix);
        if (hours > 0) sb.append(hours).append(hourSuffix);
        if (minutes > 0) sb.append(minutes).append(minuteSuffix);
        sb.append(seconds).append(secondSuffix);

        return sb.toString();
    }
}
