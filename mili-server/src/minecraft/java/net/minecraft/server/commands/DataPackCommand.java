package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.DataResult.Error;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.util.FileUtil;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

public class DataPackCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_PACK = new DynamicCommandExceptionType(
        pack -> Component.translatableEscape("commands.datapack.unknown", pack)
    );
    private static final DynamicCommandExceptionType ERROR_PACK_ALREADY_ENABLED = new DynamicCommandExceptionType(
        pack -> Component.translatableEscape("commands.datapack.enable.failed", pack)
    );
    private static final DynamicCommandExceptionType ERROR_PACK_ALREADY_DISABLED = new DynamicCommandExceptionType(
        pack -> Component.translatableEscape("commands.datapack.disable.failed", pack)
    );
    private static final DynamicCommandExceptionType ERROR_CANNOT_DISABLE_FEATURE = new DynamicCommandExceptionType(
        pack -> Component.translatableEscape("commands.datapack.disable.failed.feature", pack)
    );
    private static final Dynamic2CommandExceptionType ERROR_PACK_FEATURES_NOT_ENABLED = new Dynamic2CommandExceptionType(
        (pack, flags) -> Component.translatableEscape("commands.datapack.enable.failed.no_flags", pack, flags)
    );
    private static final DynamicCommandExceptionType ERROR_PACK_INVALID_NAME = new DynamicCommandExceptionType(
        name -> Component.translatableEscape("commands.datapack.create.invalid_name", name)
    );
    private static final DynamicCommandExceptionType ERROR_PACK_INVALID_FULL_NAME = new DynamicCommandExceptionType(
        name -> Component.translatableEscape("commands.datapack.create.invalid_full_name", name)
    );
    private static final DynamicCommandExceptionType ERROR_PACK_ALREADY_EXISTS = new DynamicCommandExceptionType(
        object -> Component.translatableEscape("commands.datapack.create.already_exists", object)
    );
    private static final Dynamic2CommandExceptionType ERROR_PACK_METADATA_ENCODE_FAILURE = new Dynamic2CommandExceptionType(
        (object, object1) -> Component.translatableEscape("commands.datapack.create.metadata_encode_failure", object, object1)
    );
    private static final DynamicCommandExceptionType ERROR_PACK_IO_FAILURE = new DynamicCommandExceptionType(
        object -> Component.translatableEscape("commands.datapack.create.io_failure", object)
    );
    private static final SuggestionProvider<CommandSourceStack> SELECTED_PACKS = (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(
        commandContext.getSource().getServer().getPackRepository().getSelectedIds().stream().map(StringArgumentType::escapeIfRequired), suggestionsBuilder
    );
    private static final SuggestionProvider<CommandSourceStack> UNSELECTED_PACKS = (commandContext, suggestionsBuilder) -> {
        PackRepository packRepository = commandContext.getSource().getServer().getPackRepository();
        Collection<String> selectedIds = packRepository.getSelectedIds();
        FeatureFlagSet featureFlagSet = commandContext.getSource().enabledFeatures();
        return SharedSuggestionProvider.suggest(
            packRepository.getAvailablePacks()
                .stream()
                .filter(pack -> pack.getRequestedFeatures().isSubsetOf(featureFlagSet))
                .map(Pack::getId)
                .filter(string -> !selectedIds.contains(string))
                .map(StringArgumentType::escapeIfRequired),
            suggestionsBuilder
        );
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(
            Commands.literal("datapack")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                /*.then( // Luminol - Add back read-only datapack command
                    Commands.literal("enable")
                        .then(
                            Commands.argument("name", StringArgumentType.string())
                                .suggests(UNSELECTED_PACKS)
                                .executes(
                                    context -> enablePack(
                                        context.getSource(),
                                        getPack(context, "name", true),
                                        (currentPacks, pack) -> pack.getDefaultPosition().insert(currentPacks, pack, Pack::selectionConfig, false)
                                    )
                                )
                                .then(
                                    Commands.literal("after")
                                        .then(
                                            Commands.argument("existing", StringArgumentType.string())
                                                .suggests(SELECTED_PACKS)
                                                .executes(
                                                    commandContext -> enablePack(
                                                        commandContext.getSource(),
                                                        getPack(commandContext, "name", true),
                                                        (currentPacks, pack) -> currentPacks.add(
                                                            currentPacks.indexOf(getPack(commandContext, "existing", false)) + 1, pack
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("before")
                                        .then(
                                            Commands.argument("existing", StringArgumentType.string())
                                                .suggests(SELECTED_PACKS)
                                                .executes(
                                                    context -> enablePack(
                                                        context.getSource(),
                                                        getPack(context, "name", true),
                                                        (currentPacks, pack) -> currentPacks.add(
                                                            currentPacks.indexOf(getPack(context, "existing", false)), pack
                                                        )
                                                    )
                                                )
                                        )
                                )
                                .then(Commands.literal("last").executes(context -> enablePack(context.getSource(), getPack(context, "name", true), List::add)))
                                .then(
                                    Commands.literal("first")
                                        .executes(
                                            context -> enablePack(
                                                context.getSource(), getPack(context, "name", true), (currentPacks, pack) -> currentPacks.add(0, pack)
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("disable")
                        .then(
                            Commands.argument("name", StringArgumentType.string())
                                .suggests(SELECTED_PACKS)
                                .executes(commandContext -> disablePack(commandContext.getSource(), getPack(commandContext, "name", false)))
                        )
                )*/ // Luminol - Add back read-only datapack command
                .then(
                    Commands.literal("list")
                        .executes(commandContext -> listPacks(commandContext.getSource()))
                        // .then(Commands.literal("available").executes(commandContext -> listAvailablePacks(commandContext.getSource()))) // Luminol - Add back read-only datapack command
                        .then(Commands.literal("enabled").executes(commandContext -> listEnabledPacks(commandContext.getSource())))
                )
                .then(
                    Commands.literal("create")
                        .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                        .then(
                            Commands.argument("id", StringArgumentType.string())
                                .then(
                                    Commands.argument("description", ComponentArgument.textComponent(buildContext))
                                        .executes(
                                            commandContext -> createPack(
                                                commandContext.getSource(),
                                                StringArgumentType.getString(commandContext, "id"),
                                                ComponentArgument.getResolvedComponent(commandContext, "description")
                                            )
                                        )
                                )
                        )
                )
        );
    }

    private static int createPack(CommandSourceStack source, String id, Component description) throws CommandSyntaxException {
        Path worldPath = source.getServer().getWorldPath(LevelResource.DATAPACK_DIR);
        if (!FileUtil.isValidPathSegment(id)) {
            throw ERROR_PACK_INVALID_NAME.create(id);
        } else if (!FileUtil.isPathPartPortable(id)) {
            throw ERROR_PACK_INVALID_FULL_NAME.create(id);
        } else {
            Path path = worldPath.resolve(id);
            if (Files.exists(path)) {
                throw ERROR_PACK_ALREADY_EXISTS.create(id);
            } else {
                PackMetadataSection packMetadataSection = new PackMetadataSection(
                    description, SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).minorRange()
                );
                DataResult<JsonElement> dataResult = PackMetadataSection.SERVER_TYPE.codec().encodeStart(JsonOps.INSTANCE, packMetadataSection);
                Optional<Error<JsonElement>> optional = dataResult.error();
                if (optional.isPresent()) {
                    throw ERROR_PACK_METADATA_ENCODE_FAILURE.create(id, optional.get().message());
                } else {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.add(PackMetadataSection.SERVER_TYPE.name(), dataResult.getOrThrow());

                    try {
                        Files.createDirectory(path);
                        Files.createDirectory(path.resolve(PackType.SERVER_DATA.getDirectory()));

                        try (
                            BufferedWriter bufferedWriter = Files.newBufferedWriter(path.resolve("pack.mcmeta"), StandardCharsets.UTF_8);
                            JsonWriter jsonWriter = new JsonWriter(bufferedWriter);
                        ) {
                            jsonWriter.setSerializeNulls(false);
                            jsonWriter.setIndent("  ");
                            GsonHelper.writeValue(jsonWriter, jsonObject, null);
                        }
                    } catch (IOException var17) {
                        LOGGER.warn("Failed to create pack at {}", worldPath.toAbsolutePath(), var17);
                        throw ERROR_PACK_IO_FAILURE.create(id);
                    }

                    source.sendSuccess(() -> Component.translatable("commands.datapack.create.success", id), true);
                    return 1;
                }
            }
        }
    }

    private static int enablePack(CommandSourceStack source, Pack pack, DataPackCommand.Inserter priorityCallback) throws CommandSyntaxException {
        PackRepository packRepository = source.getServer().getPackRepository();
        List<Pack> list = Lists.newArrayList(packRepository.getSelectedPacks());
        priorityCallback.apply(list, pack);
        source.sendSuccess(() -> Component.translatable("commands.datapack.modify.enable", pack.getChatLink(true)), true);
        ReloadCommand.reloadPacks(list.stream().map(Pack::getId).collect(Collectors.toList()), source);
        return list.size();
    }

    private static int disablePack(CommandSourceStack source, Pack pack) {
        PackRepository packRepository = source.getServer().getPackRepository();
        List<Pack> list = Lists.newArrayList(packRepository.getSelectedPacks());
        list.remove(pack);
        source.sendSuccess(() -> Component.translatable("commands.datapack.modify.disable", pack.getChatLink(true)), true);
        ReloadCommand.reloadPacks(list.stream().map(Pack::getId).collect(Collectors.toList()), source);
        return list.size();
    }

    private static int listPacks(CommandSourceStack source) {
        return listEnabledPacks(source) ;// + listAvailablePacks(source); // Luminol - Add back read-only datapack command
    }

    private static int listAvailablePacks(CommandSourceStack source) {
        PackRepository packRepository = source.getServer().getPackRepository();
        packRepository.reload();
        Collection<Pack> selectedPacks = packRepository.getSelectedPacks();
        Collection<Pack> availablePacks = packRepository.getAvailablePacks();
        FeatureFlagSet featureFlagSet = source.enabledFeatures();
        List<Pack> list = availablePacks.stream()
            .filter(pack -> !selectedPacks.contains(pack) && pack.getRequestedFeatures().isSubsetOf(featureFlagSet))
            .toList();
        if (list.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.datapack.list.available.none"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.datapack.list.available.success", list.size(), ComponentUtils.formatList(list, pack -> pack.getChatLink(false))
                ),
                false
            );
        }

        return list.size();
    }

    private static int listEnabledPacks(CommandSourceStack source) {
        PackRepository packRepository = source.getServer().getPackRepository();
        // packRepository.reload(); // Luminol - Add back read-only datapack command
        Collection<? extends Pack> selectedPacks = packRepository.getSelectedPacks();
        if (selectedPacks.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.datapack.list.enabled.none"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.datapack.list.enabled.success", selectedPacks.size(), ComponentUtils.formatList(selectedPacks, pack -> pack.getChatLink(true))
                ),
                false
            );
        }

        return selectedPacks.size();
    }

    private static Pack getPack(CommandContext<CommandSourceStack> context, String name, boolean enabling) throws CommandSyntaxException {
        String string = StringArgumentType.getString(context, name);
        PackRepository packRepository = context.getSource().getServer().getPackRepository();
        Pack pack = packRepository.getPack(string);
        if (pack == null) {
            throw ERROR_UNKNOWN_PACK.create(string);
        } else {
            boolean flag = packRepository.getSelectedPacks().contains(pack);
            if (enabling && flag) {
                throw ERROR_PACK_ALREADY_ENABLED.create(string);
            } else if (!enabling && !flag) {
                throw ERROR_PACK_ALREADY_DISABLED.create(string);
            } else {
                FeatureFlagSet featureFlagSet = context.getSource().enabledFeatures();
                FeatureFlagSet requestedFeatures = pack.getRequestedFeatures();
                if (!enabling && !requestedFeatures.isEmpty() && pack.getPackSource() == PackSource.FEATURE) {
                    throw ERROR_CANNOT_DISABLE_FEATURE.create(string);
                } else if (!requestedFeatures.isSubsetOf(featureFlagSet)) {
                    throw ERROR_PACK_FEATURES_NOT_ENABLED.create(string, FeatureFlags.printMissingFlags(featureFlagSet, requestedFeatures));
                } else {
                    return pack;
                }
            }
        }
    }

    interface Inserter {
        void apply(List<Pack> currentPacks, Pack pack) throws CommandSyntaxException;
    }
}
