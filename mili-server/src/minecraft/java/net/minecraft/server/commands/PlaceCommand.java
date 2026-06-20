package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Optional;
import net.minecraft.IdentifierException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.TemplateMirrorArgument;
import net.minecraft.commands.arguments.TemplateRotationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class PlaceCommand {
    private static final SimpleCommandExceptionType ERROR_FEATURE_FAILED = new SimpleCommandExceptionType(
        Component.translatable("commands.place.feature.failed")
    );
    private static final SimpleCommandExceptionType ERROR_JIGSAW_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.place.jigsaw.failed"));
    private static final SimpleCommandExceptionType ERROR_STRUCTURE_FAILED = new SimpleCommandExceptionType(
        Component.translatable("commands.place.structure.failed")
    );
    private static final DynamicCommandExceptionType ERROR_TEMPLATE_INVALID = new DynamicCommandExceptionType(
        template -> Component.translatableEscape("commands.place.template.invalid", template)
    );
    private static final SimpleCommandExceptionType ERROR_TEMPLATE_FAILED = new SimpleCommandExceptionType(
        Component.translatable("commands.place.template.failed")
    );
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TEMPLATES = (context, builder) -> {
        StructureTemplateManager structureManager = context.getSource().getLevel().getStructureManager();
        return SharedSuggestionProvider.suggestResource(structureManager.listTemplates(), builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("place")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("feature")
                        .then(
                            Commands.argument("feature", ResourceKeyArgument.key(Registries.CONFIGURED_FEATURE))
                                .executes(
                                    context -> placeFeature(
                                        context.getSource(),
                                        ResourceKeyArgument.getConfiguredFeature(context, "feature"),
                                        BlockPos.containing(context.getSource().getPosition())
                                    )
                                )
                                .then(
                                    Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(
                                            context -> placeFeature(
                                                context.getSource(),
                                                ResourceKeyArgument.getConfiguredFeature(context, "feature"),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos")
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("jigsaw")
                        .then(
                            Commands.argument("pool", ResourceKeyArgument.key(Registries.TEMPLATE_POOL))
                                .then(
                                    Commands.argument("target", IdentifierArgument.id())
                                        .then(
                                            Commands.argument("max_depth", IntegerArgumentType.integer(1, 20))
                                                .executes(
                                                    context -> placeJigsaw(
                                                        context.getSource(),
                                                        ResourceKeyArgument.getStructureTemplatePool(context, "pool"),
                                                        IdentifierArgument.getId(context, "target"),
                                                        IntegerArgumentType.getInteger(context, "max_depth"),
                                                        BlockPos.containing(context.getSource().getPosition())
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("position", BlockPosArgument.blockPos())
                                                        .executes(
                                                            context -> placeJigsaw(
                                                                context.getSource(),
                                                                ResourceKeyArgument.getStructureTemplatePool(context, "pool"),
                                                                IdentifierArgument.getId(context, "target"),
                                                                IntegerArgumentType.getInteger(context, "max_depth"),
                                                                BlockPosArgument.getLoadedBlockPos(context, "position")
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("structure")
                        .then(
                            Commands.argument("structure", ResourceKeyArgument.key(Registries.STRUCTURE))
                                .executes(
                                    context -> placeStructure(
                                        context.getSource(),
                                        ResourceKeyArgument.getStructure(context, "structure"),
                                        BlockPos.containing(context.getSource().getPosition())
                                    )
                                )
                                .then(
                                    Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(
                                            context -> placeStructure(
                                                context.getSource(),
                                                ResourceKeyArgument.getStructure(context, "structure"),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos")
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("template")
                        .then(
                            Commands.argument("template", IdentifierArgument.id())
                                .suggests(SUGGEST_TEMPLATES)
                                .executes(
                                    context -> placeTemplate(
                                        context.getSource(),
                                        IdentifierArgument.getId(context, "template"),
                                        BlockPos.containing(context.getSource().getPosition()),
                                        Rotation.NONE,
                                        Mirror.NONE,
                                        1.0F,
                                        0,
                                        false
                                    )
                                )
                                .then(
                                    Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(
                                            context -> placeTemplate(
                                                context.getSource(),
                                                IdentifierArgument.getId(context, "template"),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                Rotation.NONE,
                                                Mirror.NONE,
                                                1.0F,
                                                0,
                                                false
                                            )
                                        )
                                        .then(
                                            Commands.argument("rotation", TemplateRotationArgument.templateRotation())
                                                .executes(
                                                    context -> placeTemplate(
                                                        context.getSource(),
                                                        IdentifierArgument.getId(context, "template"),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                        TemplateRotationArgument.getRotation(context, "rotation"),
                                                        Mirror.NONE,
                                                        1.0F,
                                                        0,
                                                        false
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("mirror", TemplateMirrorArgument.templateMirror())
                                                        .executes(
                                                            context -> placeTemplate(
                                                                context.getSource(),
                                                                IdentifierArgument.getId(context, "template"),
                                                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                                TemplateRotationArgument.getRotation(context, "rotation"),
                                                                TemplateMirrorArgument.getMirror(context, "mirror"),
                                                                1.0F,
                                                                0,
                                                                false
                                                            )
                                                        )
                                                        .then(
                                                            Commands.argument("integrity", FloatArgumentType.floatArg(0.0F, 1.0F))
                                                                .executes(
                                                                    context -> placeTemplate(
                                                                        context.getSource(),
                                                                        IdentifierArgument.getId(context, "template"),
                                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                                        TemplateRotationArgument.getRotation(context, "rotation"),
                                                                        TemplateMirrorArgument.getMirror(context, "mirror"),
                                                                        FloatArgumentType.getFloat(context, "integrity"),
                                                                        0,
                                                                        false
                                                                    )
                                                                )
                                                                .then(
                                                                    Commands.argument("seed", IntegerArgumentType.integer())
                                                                        .executes(
                                                                            context -> placeTemplate(
                                                                                context.getSource(),
                                                                                IdentifierArgument.getId(context, "template"),
                                                                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                                                TemplateRotationArgument.getRotation(context, "rotation"),
                                                                                TemplateMirrorArgument.getMirror(context, "mirror"),
                                                                                FloatArgumentType.getFloat(context, "integrity"),
                                                                                IntegerArgumentType.getInteger(context, "seed"),
                                                                                false
                                                                            )
                                                                        )
                                                                        .then(
                                                                            Commands.literal("strict")
                                                                                .executes(
                                                                                    context -> placeTemplate(
                                                                                        context.getSource(),
                                                                                        IdentifierArgument.getId(context, "template"),
                                                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                                                        TemplateRotationArgument.getRotation(context, "rotation"),
                                                                                        TemplateMirrorArgument.getMirror(context, "mirror"),
                                                                                        FloatArgumentType.getFloat(context, "integrity"),
                                                                                        IntegerArgumentType.getInteger(context, "seed"),
                                                                                        true
                                                                                    )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    // Folia start - region threading
    private static void sendMessage(CommandSourceStack src, CommandSyntaxException ex) {
        src.sendFailure((Component)ex.getRawMessage());
    }
    // Folia end - region threading

    public static int placeFeature(CommandSourceStack source, Holder.Reference<ConfiguredFeature<?, ?>> feature, BlockPos pos) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ConfiguredFeature<?, ?> configuredFeature = feature.value();
        ChunkPos chunkPos = new ChunkPos(pos);
        checkLoaded(level, new ChunkPos(chunkPos.x - 1, chunkPos.z - 1), new ChunkPos(chunkPos.x + 1, chunkPos.z + 1));
        // Folia start - region threading
        level.moonrise$loadChunksAsync(
                pos, 16, net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                ca.spottedleaf.concurrentutil.util.Priority.NORMAL,
                (chunks) -> {
                    try {
                        // Folia end - region threading
        if (!configuredFeature.place(level, level.getChunkSource().getGenerator(), level.getRandom(), pos)) {
            throw ERROR_FEATURE_FAILED.create();
        } else {
            String string = feature.key().identifier().toString();
            source.sendSuccess(() -> Component.translatable("commands.place.feature.success", string, pos.getX(), pos.getY(), pos.getZ()), true);
            return; // Folia - region threading
        }
        // Folia start - region threading
                } catch (CommandSyntaxException ex) {
                    sendMessage(source, ex);
                }
            }
        );
        return 1;
        // Folia end - region threading
    }

    public static int placeJigsaw(CommandSourceStack source, Holder<StructureTemplatePool> templatePool, Identifier target, int maxDepth, BlockPos pos) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ChunkPos chunkPos = new ChunkPos(pos);
        checkLoaded(level, chunkPos, chunkPos);
        // Folia start - region threading
        level.moonrise$loadChunksAsync(
                pos, 16, net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                ca.spottedleaf.concurrentutil.util.Priority.NORMAL,
                (chunks) -> {
                    try {
                        // Folia end - region threading
        if (!JigsawPlacement.generateJigsaw(level, templatePool, target, maxDepth, pos, false)) {
            throw ERROR_JIGSAW_FAILED.create();
        } else {
            source.sendSuccess(() -> Component.translatable("commands.place.jigsaw.success", pos.getX(), pos.getY(), pos.getZ()), true);
            return; // Folia - region threading
        }
        // Folia start - region threading
                } catch (CommandSyntaxException ex) {
                    sendMessage(source, ex);
                }
            }
        );
        return 1;
        // Folia end - region threading
    }

    public static int placeStructure(CommandSourceStack source, Holder.Reference<Structure> structure, BlockPos pos) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        Structure structure1 = structure.value();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        // Folia start - region threading
        level.moonrise$loadChunksAsync(
                pos, 16, net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                ca.spottedleaf.concurrentutil.util.Priority.NORMAL,
                (chunks) -> {
                    try {
                        // Folia end - region threading
        StructureStart structureStart = structure1.generate(
            structure,
            level.dimension(),
            source.registryAccess(),
            generator,
            generator.getBiomeSource(),
            level.getChunkSource().randomState(),
            level.getStructureManager(),
            level.getSeed(),
            new ChunkPos(pos),
            0,
            level,
            biome -> true
        );
        if (!structureStart.isValid()) {
            throw ERROR_STRUCTURE_FAILED.create();
        } else {
            structureStart.generationEventCause = org.bukkit.event.world.AsyncStructureGenerateEvent.Cause.COMMAND; // CraftBukkit - set AsyncStructureGenerateEvent.Cause.COMMAND as generation cause
            BoundingBox boundingBox = structureStart.getBoundingBox();
            ChunkPos chunkPos = new ChunkPos(SectionPos.blockToSectionCoord(boundingBox.minX()), SectionPos.blockToSectionCoord(boundingBox.minZ()));
            ChunkPos chunkPos1 = new ChunkPos(SectionPos.blockToSectionCoord(boundingBox.maxX()), SectionPos.blockToSectionCoord(boundingBox.maxZ()));
            checkLoaded(level, chunkPos, chunkPos1);
            ChunkPos.rangeClosed(chunkPos, chunkPos1)
                .forEach(
                    chunkPos2 -> structureStart.placeInChunk(
                        level,
                        level.structureManager(),
                        generator,
                        level.getRandom(),
                        new BoundingBox(
                            chunkPos2.getMinBlockX(),
                            level.getMinY(),
                            chunkPos2.getMinBlockZ(),
                            chunkPos2.getMaxBlockX(),
                            level.getMaxY() + 1,
                            chunkPos2.getMaxBlockZ()
                        ),
                        chunkPos2
                    )
                );
            String string = structure.key().identifier().toString();
            source.sendSuccess(() -> Component.translatable("commands.place.structure.success", string, pos.getX(), pos.getY(), pos.getZ()), true);
            return; // Folia - region threading
        }
        // Folia start - region threading
                } catch (CommandSyntaxException ex) {
                    sendMessage(source, ex);
                }
            }
        );
        return 1;
        // Folia end - region threading
    }

    public static int placeTemplate(
        CommandSourceStack source, Identifier template, BlockPos pos, Rotation rotation, Mirror mirror, float integrity, int seed, boolean strict
    ) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        // Folia start - region threading
        level.moonrise$loadChunksAsync(
                pos, 16, net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                ca.spottedleaf.concurrentutil.util.Priority.NORMAL,
                (chunks) -> {
                    try {
                        // Folia end - region threading
        StructureTemplateManager structureManager = level.getStructureManager();

        Optional<StructureTemplate> optional;
        try {
            optional = structureManager.get(template);
        } catch (IdentifierException var14) {
            throw ERROR_TEMPLATE_INVALID.create(template);
        }

        if (optional.isEmpty()) {
            throw ERROR_TEMPLATE_INVALID.create(template);
        } else {
            StructureTemplate structureTemplate = optional.get();
            checkLoaded(level, new ChunkPos(pos), new ChunkPos(pos.offset(structureTemplate.getSize())));
            StructurePlaceSettings structurePlaceSettings = new StructurePlaceSettings().setMirror(mirror).setRotation(rotation).setKnownShape(strict);
            if (integrity < 1.0F) {
                structurePlaceSettings.clearProcessors().addProcessor(new BlockRotProcessor(integrity)).setRandom(StructureBlockEntity.createRandom(seed));
            }

            boolean flag = structureTemplate.placeInWorld(
                level,
                pos,
                pos,
                structurePlaceSettings,
                StructureBlockEntity.createRandom(seed),
                Block.UPDATE_CLIENTS | (strict ? Block.UPDATE_SKIP_ALL_SIDEEFFECTS : 0)
            );
            if (!flag) {
                throw ERROR_TEMPLATE_FAILED.create();
            } else {
                source.sendSuccess(
                    () -> Component.translatable("commands.place.template.success", Component.translationArg(template), pos.getX(), pos.getY(), pos.getZ()),
                    true
                );
                return; // Folia - region threading
            }
        }
        // Folia start - region threading
                } catch (CommandSyntaxException ex) {
                    sendMessage(source, ex);
                }
            }
        );
        return 1;
        // Folia end - region threading
    }

    private static void checkLoaded(ServerLevel level, ChunkPos start, ChunkPos end) throws CommandSyntaxException {
        if (ChunkPos.rangeClosed(start, end).filter(chunkPos -> !level.isLoaded(chunkPos.getWorldPosition())).findAny().isPresent()) {
            throw BlockPosArgument.ERROR_NOT_LOADED.create();
        }
    }
}
