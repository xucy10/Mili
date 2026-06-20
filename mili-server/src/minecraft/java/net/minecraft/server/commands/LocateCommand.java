package net.minecraft.server.commands;

import com.google.common.base.Stopwatch;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.time.Duration;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;

public class LocateCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DynamicCommandExceptionType ERROR_STRUCTURE_NOT_FOUND = new DynamicCommandExceptionType(
        structureType -> Component.translatableEscape("commands.locate.structure.not_found", structureType)
    );
    private static final DynamicCommandExceptionType ERROR_STRUCTURE_INVALID = new DynamicCommandExceptionType(
        structureType -> Component.translatableEscape("commands.locate.structure.invalid", structureType)
    );
    private static final DynamicCommandExceptionType ERROR_BIOME_NOT_FOUND = new DynamicCommandExceptionType(
        biomeType -> Component.translatableEscape("commands.locate.biome.not_found", biomeType)
    );
    private static final DynamicCommandExceptionType ERROR_POI_NOT_FOUND = new DynamicCommandExceptionType(
        biomeType -> Component.translatableEscape("commands.locate.poi.not_found", biomeType)
    );
    private static final int MAX_STRUCTURE_SEARCH_RADIUS = 100;
    private static final int MAX_BIOME_SEARCH_RADIUS = 6400;
    private static final int BIOME_SAMPLE_RESOLUTION_HORIZONTAL = 32;
    private static final int BIOME_SAMPLE_RESOLUTION_VERTICAL = 64;
    private static final int POI_SEARCH_RADIUS = 256;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("locate")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("structure")
                        .then(
                            Commands.argument("structure", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.STRUCTURE))
                                .executes(
                                    commandContext -> locateStructure(
                                        commandContext.getSource(),
                                        ResourceOrTagKeyArgument.getResourceOrTagKey(commandContext, "structure", Registries.STRUCTURE, ERROR_STRUCTURE_INVALID)
                                    )
                                )
                        )
                )
                .then(
                    Commands.literal("biome")
                        .then(
                            Commands.argument("biome", ResourceOrTagArgument.resourceOrTag(context, Registries.BIOME))
                                .executes(
                                    context1 -> locateBiome(context1.getSource(), ResourceOrTagArgument.getResourceOrTag(context1, "biome", Registries.BIOME))
                                )
                        )
                )
                .then(
                    Commands.literal("poi")
                        .then(
                            Commands.argument("poi", ResourceOrTagArgument.resourceOrTag(context, Registries.POINT_OF_INTEREST_TYPE))
                                .executes(
                                    context1 -> locatePoi(
                                        context1.getSource(), ResourceOrTagArgument.getResourceOrTag(context1, "poi", Registries.POINT_OF_INTEREST_TYPE)
                                    )
                                )
                        )
                )
        );
    }

    private static Optional<? extends HolderSet.ListBacked<Structure>> getHolders(
        ResourceOrTagKeyArgument.Result<Structure> structure, Registry<Structure> structureRegistry
    ) {
        return structure.unwrap()
            .map(resourceKey -> structureRegistry.get((ResourceKey<Structure>)resourceKey).map(holder -> HolderSet.direct(holder)), structureRegistry::get);
    }

    private static int locateStructure(CommandSourceStack source, ResourceOrTagKeyArgument.Result<Structure> structure) throws CommandSyntaxException {
        Registry<Structure> registry = source.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
        HolderSet<Structure> holderSet = (HolderSet<Structure>)getHolders(structure, registry)
            .orElseThrow(() -> ERROR_STRUCTURE_INVALID.create(structure.asPrintable()));
        BlockPos blockPos = BlockPos.containing(source.getPosition());
        ServerLevel level = source.getLevel();
        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
        Pair<BlockPos, Holder<Structure>> pair = level.getChunkSource().getGenerator().findNearestMapStructure(level, holderSet, blockPos, 100, false);
        stopwatch.stop();
        if (pair == null) {
            throw ERROR_STRUCTURE_NOT_FOUND.create(structure.asPrintable());
        } else {
            return showLocateResult(source, structure, blockPos, pair, "commands.locate.structure.success", false, stopwatch.elapsed());
        }
    }

    private static int locateBiome(CommandSourceStack source, ResourceOrTagArgument.Result<Biome> biome) throws CommandSyntaxException {
        BlockPos blockPos = BlockPos.containing(source.getPosition());
        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
        Pair<BlockPos, Holder<Biome>> pair = source.getLevel().findClosestBiome3d(biome, blockPos, 6400, 32, 64);
        stopwatch.stop();
        if (pair == null) {
            throw ERROR_BIOME_NOT_FOUND.create(biome.asPrintable());
        } else {
            return showLocateResult(source, biome, blockPos, pair, "commands.locate.biome.success", true, stopwatch.elapsed());
        }
    }

    private static int locatePoi(CommandSourceStack source, ResourceOrTagArgument.Result<PoiType> poiType) throws CommandSyntaxException {
        BlockPos blockPos = BlockPos.containing(source.getPosition());
        ServerLevel level = source.getLevel();
        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
        Optional<Pair<Holder<PoiType>, BlockPos>> optional = level.getPoiManager().findClosestWithType(poiType, blockPos, 256, PoiManager.Occupancy.ANY);
        stopwatch.stop();
        if (optional.isEmpty()) {
            throw ERROR_POI_NOT_FOUND.create(poiType.asPrintable());
        } else {
            return showLocateResult(source, poiType, blockPos, optional.get().swap(), "commands.locate.poi.success", false, stopwatch.elapsed());
        }
    }

    public static int showLocateResult(
        CommandSourceStack source,
        ResourceOrTagArgument.Result<?> result,
        BlockPos sourcePosition,
        Pair<BlockPos, ? extends Holder<?>> resultWithPosition,
        String translationKey,
        boolean absoluteY,
        Duration duration
    ) {
        String string = result.unwrap()
            .map(reference -> result.asPrintable(), named -> result.asPrintable() + " (" + resultWithPosition.getSecond().getRegisteredName() + ")");
        return showLocateResult(source, sourcePosition, resultWithPosition, translationKey, absoluteY, string, duration);
    }

    public static int showLocateResult(
        CommandSourceStack source,
        ResourceOrTagKeyArgument.Result<?> result,
        BlockPos sourcePosition,
        Pair<BlockPos, ? extends Holder<?>> resultWithPosition,
        String translationKey,
        boolean absoluteY,
        Duration duration
    ) {
        String string = result.unwrap()
            .map(
                resourceKey -> resourceKey.identifier().toString(),
                tagKey -> "#" + tagKey.location() + " (" + resultWithPosition.getSecond().getRegisteredName() + ")"
            );
        return showLocateResult(source, sourcePosition, resultWithPosition, translationKey, absoluteY, string, duration);
    }

    private static int showLocateResult(
        CommandSourceStack source,
        BlockPos sourcePosition,
        Pair<BlockPos, ? extends Holder<?>> resultWithoutPosition,
        String translationKey,
        boolean absoluteY,
        String elementName,
        Duration duration
    ) {
        BlockPos blockPos = resultWithoutPosition.getFirst();
        int i = absoluteY
            ? Mth.floor(Mth.sqrt((float)sourcePosition.distSqr(blockPos)))
            : Mth.floor(dist(sourcePosition.getX(), sourcePosition.getZ(), blockPos.getX(), blockPos.getZ()));
        String string = absoluteY ? String.valueOf(blockPos.getY()) : "~";
        Component component = ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", blockPos.getX(), string, blockPos.getZ()))
            .withStyle(
                style -> style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent.SuggestCommand("/tp @s " + blockPos.getX() + " " + string + " " + blockPos.getZ()))
                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.coordinates.tooltip")))
            );
        source.sendSuccess(() -> Component.translatable(translationKey, elementName, component, i), false);
        LOGGER.info("Locating element {} took {} ms", elementName, duration.toMillis());
        return i;
    }

    private static float dist(int x1, int z1, int x2, int z2) {
        int i = x2 - x1;
        int i1 = z2 - z1;
        return (float) Math.hypot(i, i1); // Paper - Fix MC-177381
    }
}
