package net.minecraft.server.commands;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.HeightmapTypeArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.SlotsArgument;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.SwizzleArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomModifierExecutor;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.execution.tasks.FallthroughTask;
import net.minecraft.commands.execution.tasks.IsolatedCall;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.DataCommands;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.Stopwatch;
import net.minecraft.world.Stopwatches;
import net.minecraft.world.entity.Attackable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SlotProvider;
import net.minecraft.world.entity.Targeting;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.inventory.SlotRange;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ExecuteCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TEST_AREA = 32768;
    private static final Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE = new Dynamic2CommandExceptionType(
        (maxBlocks, specifiedBlocks) -> Component.translatableEscape("commands.execute.blocks.toobig", maxBlocks, specifiedBlocks)
    );
    private static final SimpleCommandExceptionType ERROR_CONDITIONAL_FAILED = new SimpleCommandExceptionType(
        Component.translatable("commands.execute.conditional.fail")
    );
    private static final DynamicCommandExceptionType ERROR_CONDITIONAL_FAILED_COUNT = new DynamicCommandExceptionType(
        count -> Component.translatableEscape("commands.execute.conditional.fail_count", count)
    );
    @VisibleForTesting
    public static final Dynamic2CommandExceptionType ERROR_FUNCTION_CONDITION_INSTANTATION_FAILURE = new Dynamic2CommandExceptionType(
        (function, message) -> Component.translatableEscape("commands.execute.function.instantiationFailure", function, message)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        LiteralCommandNode<CommandSourceStack> literalCommandNode = dispatcher.register(
            Commands.literal("execute").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        );
        dispatcher.register(
            Commands.literal("execute")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("run").redirect(dispatcher.getRoot()))
                .then(addConditionals(literalCommandNode, Commands.literal("if"), true, buildContext))
                .then(addConditionals(literalCommandNode, Commands.literal("unless"), false, buildContext))
                .then(Commands.literal("as").then(Commands.argument("targets", EntityArgument.entities()).fork(literalCommandNode, context -> {
                    List<CommandSourceStack> list = Lists.newArrayList();

                    for (Entity entity : EntityArgument.getOptionalEntities(context, "targets")) {
                        list.add(context.getSource().withEntity(entity));
                    }

                    return list;
                })))
                .then(Commands.literal("at").then(Commands.argument("targets", EntityArgument.entities()).fork(literalCommandNode, context -> {
                    List<CommandSourceStack> list = Lists.newArrayList();

                    for (Entity entity : EntityArgument.getOptionalEntities(context, "targets")) {
                        list.add(
                            context.getSource().withLevel((ServerLevel)entity.level()).withPosition(entity.position()).withRotation(entity.getRotationVector())
                        );
                    }

                    return list;
                })))
                .then(
                    Commands.literal("store")
                        .then(wrapStores(literalCommandNode, Commands.literal("result"), true))
                        .then(wrapStores(literalCommandNode, Commands.literal("success"), false))
                )
                .then(
                    Commands.literal("positioned")
                        .then(
                            Commands.argument("pos", Vec3Argument.vec3())
                                .redirect(
                                    literalCommandNode,
                                    context -> context.getSource()
                                        .withPosition(Vec3Argument.getVec3(context, "pos"))
                                        .withAnchor(EntityAnchorArgument.Anchor.FEET)
                                )
                        )
                        .then(Commands.literal("as").then(Commands.argument("targets", EntityArgument.entities()).fork(literalCommandNode, context -> {
                            List<CommandSourceStack> list = Lists.newArrayList();

                            for (Entity entity : EntityArgument.getOptionalEntities(context, "targets")) {
                                list.add(context.getSource().withPosition(entity.position()));
                            }

                            return list;
                        })))
                        .then(
                            Commands.literal("over")
                                .then(Commands.argument("heightmap", HeightmapTypeArgument.heightmap()).redirect(literalCommandNode, context -> {
                                    Vec3 position = context.getSource().getPosition();
                                    ServerLevel level = context.getSource().getLevel();
                                    double x = position.x();
                                    double z = position.z();
                                    if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                                        throw BlockPosArgument.ERROR_NOT_LOADED.create();
                                    } else {
                                        int height = level.getHeight(HeightmapTypeArgument.getHeightmap(context, "heightmap"), Mth.floor(x), Mth.floor(z));
                                        return context.getSource().withPosition(new Vec3(x, height, z));
                                    }
                                }))
                        )
                )
                .then(
                    Commands.literal("rotated")
                        .then(
                            Commands.argument("rot", RotationArgument.rotation())
                                .redirect(
                                    literalCommandNode,
                                    context -> context.getSource().withRotation(RotationArgument.getRotation(context, "rot").getRotation(context.getSource()))
                                )
                        )
                        .then(Commands.literal("as").then(Commands.argument("targets", EntityArgument.entities()).fork(literalCommandNode, context -> {
                            List<CommandSourceStack> list = Lists.newArrayList();

                            for (Entity entity : EntityArgument.getOptionalEntities(context, "targets")) {
                                list.add(context.getSource().withRotation(entity.getRotationVector()));
                            }

                            return list;
                        })))
                )
                .then(
                    Commands.literal("facing")
                        .then(
                            Commands.literal("entity")
                                .then(
                                    Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("anchor", EntityAnchorArgument.anchor()).fork(literalCommandNode, context -> {
                                            List<CommandSourceStack> list = Lists.newArrayList();
                                            EntityAnchorArgument.Anchor anchor = EntityAnchorArgument.getAnchor(context, "anchor");

                                            for (Entity entity : EntityArgument.getOptionalEntities(context, "targets")) {
                                                list.add(context.getSource().facing(entity, anchor));
                                            }

                                            return list;
                                        }))
                                )
                        )
                        .then(
                            Commands.argument("pos", Vec3Argument.vec3())
                                .redirect(literalCommandNode, context -> context.getSource().facing(Vec3Argument.getVec3(context, "pos")))
                        )
                )
                .then(
                    Commands.literal("align")
                        .then(
                            Commands.argument("axes", SwizzleArgument.swizzle())
                                .redirect(
                                    literalCommandNode,
                                    context -> context.getSource()
                                        .withPosition(context.getSource().getPosition().align(SwizzleArgument.getSwizzle(context, "axes")))
                                )
                        )
                )
                .then(
                    Commands.literal("anchored")
                        .then(
                            Commands.argument("anchor", EntityAnchorArgument.anchor())
                                .redirect(literalCommandNode, context -> context.getSource().withAnchor(EntityAnchorArgument.getAnchor(context, "anchor")))
                        )
                )
                .then(
                    Commands.literal("in")
                        .then(
                            Commands.argument("dimension", DimensionArgument.dimension())
                                .redirect(literalCommandNode, context -> context.getSource().withLevel(DimensionArgument.getDimension(context, "dimension")))
                        )
                )
                .then(
                    Commands.literal("summon")
                        .then(
                            Commands.argument("entity", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                                .suggests(SuggestionProviders.cast(SuggestionProviders.SUMMONABLE_ENTITIES))
                                .redirect(
                                    literalCommandNode,
                                    context -> spawnEntityAndRedirect(context.getSource(), ResourceArgument.getSummonableEntityType(context, "entity"))
                                )
                        )
                )
                .then(createRelationOperations(literalCommandNode, Commands.literal("on")))
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> wrapStores(
        LiteralCommandNode<CommandSourceStack> parent, LiteralArgumentBuilder<CommandSourceStack> literal, boolean storingResult
    ) {
        literal.then(
            Commands.literal("score")
                .then(
                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                        .then(
                            Commands.argument("objective", ObjectiveArgument.objective())
                                .redirect(
                                    parent,
                                    context -> storeValue(
                                        context.getSource(),
                                        ScoreHolderArgument.getNamesWithDefaultWildcard(context, "targets"),
                                        ObjectiveArgument.getObjective(context, "objective"),
                                        storingResult
                                    )
                                )
                        )
                )
        );
        literal.then(
            Commands.literal("bossbar")
                .then(
                    Commands.argument("id", IdentifierArgument.id())
                        .suggests(BossBarCommands.SUGGEST_BOSS_BAR)
                        .then(
                            Commands.literal("value")
                                .redirect(parent, context -> storeValue(context.getSource(), BossBarCommands.getBossBar(context), true, storingResult))
                        )
                        .then(
                            Commands.literal("max")
                                .redirect(parent, context -> storeValue(context.getSource(), BossBarCommands.getBossBar(context), false, storingResult))
                        )
                )
        );

        for (DataCommands.DataProvider dataProvider : DataCommands.TARGET_PROVIDERS) {
            dataProvider.wrap(
                literal,
                argumentBuilder -> argumentBuilder.then(
                    Commands.argument("path", NbtPathArgument.nbtPath())
                        .then(
                            Commands.literal("int")
                                .then(
                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                        .redirect(
                                            parent,
                                            context -> storeData(
                                                context.getSource(),
                                                dataProvider.access(context),
                                                NbtPathArgument.getPath(context, "path"),
                                                i -> IntTag.valueOf((int)(i * DoubleArgumentType.getDouble(context, "scale"))),
                                                storingResult
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("float")
                                .then(
                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                        .redirect(
                                            parent,
                                            context -> storeData(
                                                context.getSource(),
                                                dataProvider.access(context),
                                                NbtPathArgument.getPath(context, "path"),
                                                i -> FloatTag.valueOf((float)(i * DoubleArgumentType.getDouble(context, "scale"))),
                                                storingResult
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("short")
                                .then(
                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                        .redirect(
                                            parent,
                                            context -> storeData(
                                                context.getSource(),
                                                dataProvider.access(context),
                                                NbtPathArgument.getPath(context, "path"),
                                                i -> ShortTag.valueOf((short)(i * DoubleArgumentType.getDouble(context, "scale"))),
                                                storingResult
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("long")
                                .then(
                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                        .redirect(
                                            parent,
                                            context -> storeData(
                                                context.getSource(),
                                                dataProvider.access(context),
                                                NbtPathArgument.getPath(context, "path"),
                                                i -> LongTag.valueOf((long)(i * DoubleArgumentType.getDouble(context, "scale"))),
                                                storingResult
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("double")
                                .then(
                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                        .redirect(
                                            parent,
                                            context -> storeData(
                                                context.getSource(),
                                                dataProvider.access(context),
                                                NbtPathArgument.getPath(context, "path"),
                                                i -> DoubleTag.valueOf(i * DoubleArgumentType.getDouble(context, "scale")),
                                                storingResult
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("byte")
                                .then(
                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                        .redirect(
                                            parent,
                                            context -> storeData(
                                                context.getSource(),
                                                dataProvider.access(context),
                                                NbtPathArgument.getPath(context, "path"),
                                                i -> ByteTag.valueOf((byte)(i * DoubleArgumentType.getDouble(context, "scale"))),
                                                storingResult
                                            )
                                        )
                                )
                        )
                )
            );
        }

        return literal;
    }

    private static CommandSourceStack storeValue(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, boolean storingResult) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        return source.withCallback((success, result) -> {
            for (ScoreHolder scoreHolder : targets) {
                ScoreAccess playerScore = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);
                int i = storingResult ? result : (success ? 1 : 0);
                playerScore.set(i);
            }
        }, CommandResultCallback::chain);
    }

    private static CommandSourceStack storeValue(CommandSourceStack source, CustomBossEvent bar, boolean storingValue, boolean storingResult) {
        return source.withCallback((success, result) -> {
            int i = storingResult ? result : (success ? 1 : 0);
            if (storingValue) {
                bar.setValue(i);
            } else {
                bar.setMax(i);
            }
        }, CommandResultCallback::chain);
    }

    private static CommandSourceStack storeData(
        CommandSourceStack source, DataAccessor accessor, NbtPathArgument.NbtPath path, IntFunction<Tag> tagConverter, boolean storingResult
    ) {
        return source.withCallback((success, result) -> {
            try {
                CompoundTag data = accessor.getData();
                int i = storingResult ? result : (success ? 1 : 0);
                path.set(data, tagConverter.apply(i));
                accessor.setData(data);
            } catch (CommandSyntaxException var8) {
            }
        }, CommandResultCallback::chain);
    }

    private static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        LevelChunk chunkNow = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        return chunkNow != null && chunkNow.getFullStatus() == FullChunkStatus.ENTITY_TICKING && level.areEntitiesLoaded(chunkPos.toLong());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addConditionals(
        CommandNode<CommandSourceStack> parent, LiteralArgumentBuilder<CommandSourceStack> literal, boolean isIf, CommandBuildContext buildContext
    ) {
        literal.then(
                Commands.literal("block")
                    .then(
                        Commands.argument("pos", BlockPosArgument.blockPos())
                            .then(
                                addConditional(
                                    parent,
                                    Commands.argument("block", BlockPredicateArgument.blockPredicate(buildContext)),
                                    isIf,
                                    context -> BlockPredicateArgument.getBlockPredicate(context, "block")
                                        .test(new BlockInWorld(context.getSource().getLevel(), BlockPosArgument.getLoadedBlockPos(context, "pos"), true))
                                )
                            )
                    )
            )
            .then(
                Commands.literal("biome")
                    .then(
                        Commands.argument("pos", BlockPosArgument.blockPos())
                            .then(
                                addConditional(
                                    parent,
                                    Commands.argument("biome", ResourceOrTagArgument.resourceOrTag(buildContext, Registries.BIOME)),
                                    isIf,
                                    context -> ResourceOrTagArgument.getResourceOrTag(context, "biome", Registries.BIOME)
                                        .test(context.getSource().getLevel().getBiome(BlockPosArgument.getLoadedBlockPos(context, "pos")))
                                )
                            )
                    )
            )
            .then(
                Commands.literal("loaded")
                    .then(
                        addConditional(
                            parent,
                            Commands.argument("pos", BlockPosArgument.blockPos()),
                            isIf,
                            context -> isChunkLoaded(context.getSource().getLevel(), BlockPosArgument.getBlockPos(context, "pos"))
                        )
                    )
            )
            .then(
                Commands.literal("dimension")
                    .then(
                        addConditional(
                            parent,
                            Commands.argument("dimension", DimensionArgument.dimension()),
                            isIf,
                            context -> DimensionArgument.getDimension(context, "dimension") == context.getSource().getLevel()
                        )
                    )
            )
            .then(
                Commands.literal("score")
                    .then(
                        Commands.argument("target", ScoreHolderArgument.scoreHolder())
                            .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                            .then(
                                Commands.argument("targetObjective", ObjectiveArgument.objective())
                                    .then(
                                        Commands.literal("=")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            parent,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            isIf,
                                                            context -> checkScore(context, (value1, value2) -> value1 == value2)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal("<")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            parent,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            isIf,
                                                            context -> checkScore(context, (value1, value2) -> value1 < value2)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal("<=")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            parent,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            isIf,
                                                            context -> checkScore(context, (value1, value2) -> value1 <= value2)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal(">")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            parent,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            isIf,
                                                            context -> checkScore(context, (value1, value2) -> value1 > value2)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal(">=")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            parent,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            isIf,
                                                            context -> checkScore(context, (value1, value2) -> value1 >= value2)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal("matches")
                                            .then(
                                                addConditional(
                                                    parent,
                                                    Commands.argument("range", RangeArgument.intRange()),
                                                    isIf,
                                                    context -> checkScore(context, RangeArgument.Ints.getRange(context, "range"))
                                                )
                                            )
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("blocks")
                    .then(
                        Commands.argument("start", BlockPosArgument.blockPos())
                            .then(
                                Commands.argument("end", BlockPosArgument.blockPos())
                                    .then(
                                        Commands.argument("destination", BlockPosArgument.blockPos())
                                            .then(addIfBlocksConditional(parent, Commands.literal("all"), isIf, false))
                                            .then(addIfBlocksConditional(parent, Commands.literal("masked"), isIf, true))
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("entity")
                    .then(
                        Commands.argument("entities", EntityArgument.entities())
                            .fork(parent, context -> expect(context, isIf, !EntityArgument.getOptionalEntities(context, "entities").isEmpty()))
                            .executes(createNumericConditionalHandler(isIf, context -> EntityArgument.getOptionalEntities(context, "entities").size()))
                    )
            )
            .then(
                Commands.literal("predicate")
                    .then(
                        addConditional(
                            parent,
                            Commands.argument("predicate", ResourceOrIdArgument.lootPredicate(buildContext)),
                            isIf,
                            context -> checkCustomPredicate(context.getSource(), ResourceOrIdArgument.getLootPredicate(context, "predicate"))
                        )
                    )
            )
            .then(
                Commands.literal("function")
                    .then(
                        Commands.argument("name", FunctionArgument.functions())
                            .suggests(FunctionCommand.SUGGEST_FUNCTION)
                            .fork(parent, new ExecuteCommand.ExecuteIfFunctionCustomModifier(isIf))
                    )
            )
            .then(
                Commands.literal("items")
                    .then(
                        Commands.literal("entity")
                            .then(
                                Commands.argument("entities", EntityArgument.entities())
                                    .then(
                                        Commands.argument("slots", SlotsArgument.slots())
                                            .then(
                                                Commands.argument("item_predicate", ItemPredicateArgument.itemPredicate(buildContext))
                                                    .fork(
                                                        parent,
                                                        context -> expect(
                                                            context,
                                                            isIf,
                                                            countItems(
                                                                    EntityArgument.getEntities(context, "entities"),
                                                                    SlotsArgument.getSlots(context, "slots"),
                                                                    ItemPredicateArgument.getItemPredicate(context, "item_predicate")
                                                                )
                                                                > 0
                                                        )
                                                    )
                                                    .executes(
                                                        createNumericConditionalHandler(
                                                            isIf,
                                                            context -> countItems(
                                                                EntityArgument.getEntities(context, "entities"),
                                                                SlotsArgument.getSlots(context, "slots"),
                                                                ItemPredicateArgument.getItemPredicate(context, "item_predicate")
                                                            )
                                                        )
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        Commands.literal("block")
                            .then(
                                Commands.argument("pos", BlockPosArgument.blockPos())
                                    .then(
                                        Commands.argument("slots", SlotsArgument.slots())
                                            .then(
                                                Commands.argument("item_predicate", ItemPredicateArgument.itemPredicate(buildContext))
                                                    .fork(
                                                        parent,
                                                        context -> expect(
                                                            context,
                                                            isIf,
                                                            countItems(
                                                                    context.getSource(),
                                                                    BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                                    SlotsArgument.getSlots(context, "slots"),
                                                                    ItemPredicateArgument.getItemPredicate(context, "item_predicate")
                                                                )
                                                                > 0
                                                        )
                                                    )
                                                    .executes(
                                                        createNumericConditionalHandler(
                                                            isIf,
                                                            context -> countItems(
                                                                context.getSource(),
                                                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                                SlotsArgument.getSlots(context, "slots"),
                                                                ItemPredicateArgument.getItemPredicate(context, "item_predicate")
                                                            )
                                                        )
                                                    )
                                            )
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("stopwatch")
                    .then(
                        Commands.argument("id", IdentifierArgument.id())
                            .suggests(StopwatchCommand.SUGGEST_STOPWATCHES)
                            .then(
                                addConditional(
                                    parent,
                                    Commands.argument("range", RangeArgument.floatRange()),
                                    isIf,
                                    context -> checkStopwatch(context, RangeArgument.Floats.getRange(context, "range"))
                                )
                            )
                    )
            );

        for (DataCommands.DataProvider dataProvider : DataCommands.SOURCE_PROVIDERS) {
            literal.then(
                dataProvider.wrap(
                    Commands.literal("data"),
                    argumentBuilder -> argumentBuilder.then(
                        Commands.argument("path", NbtPathArgument.nbtPath())
                            .fork(
                                parent,
                                context -> expect(context, isIf, checkMatchingData(dataProvider.access(context), NbtPathArgument.getPath(context, "path")) > 0)
                            )
                            .executes(
                                createNumericConditionalHandler(
                                    isIf, context -> checkMatchingData(dataProvider.access(context), NbtPathArgument.getPath(context, "path"))
                                )
                            )
                    )
                )
            );
        }

        return literal;
    }

    private static int countItems(Iterable<? extends SlotProvider> targets, SlotRange slotRange, Predicate<ItemStack> filter) {
        int i = 0;

        for (SlotProvider slotProvider : targets) {
            IntList list = slotRange.slots();

            for (int i1 = 0; i1 < list.size(); i1++) {
                int _int = list.getInt(i1);
                SlotAccess slot = slotProvider.getSlot(_int);
                if (slot != null) {
                    ItemStack itemStack = slot.get();
                    if (filter.test(itemStack)) {
                        i += itemStack.getCount();
                    }
                }
            }
        }

        return i;
    }

    private static int countItems(CommandSourceStack source, BlockPos pos, SlotRange slotRange, Predicate<ItemStack> filter) throws CommandSyntaxException {
        int i = 0;
        Container container = ItemCommands.getContainer(source, pos, ItemCommands.ERROR_SOURCE_NOT_A_CONTAINER);
        int containerSize = container.getContainerSize();
        IntList list = slotRange.slots();

        for (int i1 = 0; i1 < list.size(); i1++) {
            int _int = list.getInt(i1);
            if (_int >= 0 && _int < containerSize) {
                ItemStack item = container.getItem(_int);
                if (filter.test(item)) {
                    i += item.getCount();
                }
            }
        }

        return i;
    }

    private static Command<CommandSourceStack> createNumericConditionalHandler(boolean isIf, ExecuteCommand.CommandNumericPredicate predicate) {
        return isIf ? context -> {
            int i = predicate.test(context);
            if (i > 0) {
                context.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass_count", i), false);
                return i;
            } else {
                throw ERROR_CONDITIONAL_FAILED.create();
            }
        } : context -> {
            int i = predicate.test(context);
            if (i == 0) {
                context.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass"), false);
                return 1;
            } else {
                throw ERROR_CONDITIONAL_FAILED_COUNT.create(i);
            }
        };
    }

    private static int checkMatchingData(DataAccessor accessor, NbtPathArgument.NbtPath path) throws CommandSyntaxException {
        return path.countMatching(accessor.getData());
    }

    private static boolean checkScore(CommandContext<CommandSourceStack> context, ExecuteCommand.IntBiPredicate predicate) throws CommandSyntaxException {
        ScoreHolder name = ScoreHolderArgument.getName(context, "target");
        Objective objective = ObjectiveArgument.getObjective(context, "targetObjective");
        ScoreHolder name1 = ScoreHolderArgument.getName(context, "source");
        Objective objective1 = ObjectiveArgument.getObjective(context, "sourceObjective");
        Scoreboard scoreboard = context.getSource().getServer().getScoreboard();
        ReadOnlyScoreInfo playerScoreInfo = scoreboard.getPlayerScoreInfo(name, objective);
        ReadOnlyScoreInfo playerScoreInfo1 = scoreboard.getPlayerScoreInfo(name1, objective1);
        return playerScoreInfo != null && playerScoreInfo1 != null && predicate.test(playerScoreInfo.value(), playerScoreInfo1.value());
    }

    private static boolean checkScore(CommandContext<CommandSourceStack> context, MinMaxBounds.Ints bounds) throws CommandSyntaxException {
        ScoreHolder name = ScoreHolderArgument.getName(context, "target");
        Objective objective = ObjectiveArgument.getObjective(context, "targetObjective");
        Scoreboard scoreboard = context.getSource().getServer().getScoreboard();
        ReadOnlyScoreInfo playerScoreInfo = scoreboard.getPlayerScoreInfo(name, objective);
        return playerScoreInfo != null && bounds.matches(playerScoreInfo.value());
    }

    private static boolean checkStopwatch(CommandContext<CommandSourceStack> context, MinMaxBounds.Doubles bounds) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(context, "id");
        Stopwatches stopwatches = context.getSource().getServer().getStopwatches();
        Stopwatch stopwatch = stopwatches.get(id);
        if (stopwatch == null) {
            throw StopwatchCommand.ERROR_DOES_NOT_EXIST.create(id);
        } else {
            long l = Stopwatches.currentTime();
            double d = stopwatch.elapsedSeconds(l);
            return bounds.matches(d);
        }
    }

    private static boolean checkCustomPredicate(CommandSourceStack source, Holder<LootItemCondition> condition) {
        ServerLevel level = source.getLevel();
        LootParams lootParams = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, source.getPosition())
            .withOptionalParameter(LootContextParams.THIS_ENTITY, source.getEntity())
            .create(LootContextParamSets.COMMAND);
        LootContext lootContext = new LootContext.Builder(lootParams).create(Optional.empty());
        lootContext.pushVisitedElement(LootContext.createVisitedEntry(condition.value()));
        return condition.value().test(lootContext);
    }

    private static Collection<CommandSourceStack> expect(CommandContext<CommandSourceStack> context, boolean actual, boolean expected) {
        return (Collection<CommandSourceStack>)(expected == actual ? Collections.singleton(context.getSource()) : Collections.emptyList());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addConditional(
        CommandNode<CommandSourceStack> commandNode, ArgumentBuilder<CommandSourceStack, ?> builder, boolean value, ExecuteCommand.CommandPredicate test
    ) {
        return builder.fork(commandNode, context -> expect(context, value, test.test(context))).executes(context -> {
            if (value == test.test(context)) {
                context.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass"), false);
                return 1;
            } else {
                throw ERROR_CONDITIONAL_FAILED.create();
            }
        });
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addIfBlocksConditional(
        CommandNode<CommandSourceStack> commandNode, ArgumentBuilder<CommandSourceStack, ?> literal, boolean isIf, boolean isMasked
    ) {
        return literal.fork(commandNode, context -> expect(context, isIf, checkRegions(context, isMasked).isPresent()))
            .executes(isIf ? context -> checkIfRegions(context, isMasked) : context -> checkUnlessRegions(context, isMasked));
    }

    private static int checkIfRegions(CommandContext<CommandSourceStack> context, boolean isMasked) throws CommandSyntaxException {
        OptionalInt optionalInt = checkRegions(context, isMasked);
        if (optionalInt.isPresent()) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass_count", optionalInt.getAsInt()), false);
            return optionalInt.getAsInt();
        } else {
            throw ERROR_CONDITIONAL_FAILED.create();
        }
    }

    private static int checkUnlessRegions(CommandContext<CommandSourceStack> context, boolean isMasked) throws CommandSyntaxException {
        OptionalInt optionalInt = checkRegions(context, isMasked);
        if (optionalInt.isPresent()) {
            throw ERROR_CONDITIONAL_FAILED_COUNT.create(optionalInt.getAsInt());
        } else {
            context.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass"), false);
            return 1;
        }
    }

    private static OptionalInt checkRegions(CommandContext<CommandSourceStack> context, boolean isMasked) throws CommandSyntaxException {
        return checkRegions(
            context.getSource().getLevel(),
            BlockPosArgument.getLoadedBlockPos(context, "start"),
            BlockPosArgument.getLoadedBlockPos(context, "end"),
            BlockPosArgument.getLoadedBlockPos(context, "destination"),
            isMasked
        );
    }

    private static OptionalInt checkRegions(ServerLevel level, BlockPos begin, BlockPos end, BlockPos destination, boolean isMasked) throws CommandSyntaxException {
        BoundingBox boundingBox = BoundingBox.fromCorners(begin, end);
        BoundingBox boundingBox1 = BoundingBox.fromCorners(destination, destination.offset(boundingBox.getLength()));
        BlockPos blockPos = new BlockPos(
            boundingBox1.minX() - boundingBox.minX(), boundingBox1.minY() - boundingBox.minY(), boundingBox1.minZ() - boundingBox.minZ()
        );
        int i = boundingBox.getXSpan() * boundingBox.getYSpan() * boundingBox.getZSpan();
        if (i > 32768) {
            throw ERROR_AREA_TOO_LARGE.create(32768, i);
        } else {
            int i1 = 0;
            RegistryAccess registryAccess = level.registryAccess();

            try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
                for (int z = boundingBox.minZ(); z <= boundingBox.maxZ(); z++) {
                    for (int y = boundingBox.minY(); y <= boundingBox.maxY(); y++) {
                        for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
                            BlockPos blockPos1 = new BlockPos(x, y, z);
                            BlockPos blockPos2 = blockPos1.offset(blockPos);
                            BlockState blockState = level.getBlockState(blockPos1);
                            if (!isMasked || !blockState.is(Blocks.AIR)) {
                                if (blockState != level.getBlockState(blockPos2)) {
                                    return OptionalInt.empty();
                                }

                                BlockEntity blockEntity = level.getBlockEntity(blockPos1);
                                BlockEntity blockEntity1 = level.getBlockEntity(blockPos2);
                                if (blockEntity != null) {
                                    if (blockEntity1 == null) {
                                        return OptionalInt.empty();
                                    }

                                    if (blockEntity1.getType() != blockEntity.getType()) {
                                        return OptionalInt.empty();
                                    }

                                    if (!blockEntity.components().equals(blockEntity1.components())) {
                                        return OptionalInt.empty();
                                    }

                                    TagValueOutput tagValueOutput = TagValueOutput.createWithContext(
                                        scopedCollector.forChild(blockEntity.problemPath()), registryAccess
                                    );
                                    blockEntity.saveCustomOnly(tagValueOutput);
                                    CompoundTag compoundTag = tagValueOutput.buildResult();
                                    TagValueOutput tagValueOutput1 = TagValueOutput.createWithContext(
                                        scopedCollector.forChild(blockEntity1.problemPath()), registryAccess
                                    );
                                    blockEntity1.saveCustomOnly(tagValueOutput1);
                                    CompoundTag compoundTag1 = tagValueOutput1.buildResult();
                                    if (!compoundTag.equals(compoundTag1)) {
                                        return OptionalInt.empty();
                                    }
                                }

                                i1++;
                            }
                        }
                    }
                }
            }

            return OptionalInt.of(i1);
        }
    }

    private static RedirectModifier<CommandSourceStack> expandOneToOneEntityRelation(Function<Entity, Optional<Entity>> relation) {
        return commandContext -> {
            CommandSourceStack commandSourceStack = commandContext.getSource();
            Entity entity = commandSourceStack.getEntity();
            return entity == null
                ? List.of()
                : relation.apply(entity)
                    .filter(entity1 -> !entity1.isRemoved())
                    .map(entity1 -> List.of(commandSourceStack.withEntity(entity1)))
                    .orElse(List.of());
        };
    }

    private static RedirectModifier<CommandSourceStack> expandOneToManyEntityRelation(Function<Entity, Stream<Entity>> relation) {
        return commandContext -> {
            CommandSourceStack commandSourceStack = commandContext.getSource();
            Entity entity = commandSourceStack.getEntity();
            return entity == null ? List.of() : relation.apply(entity).filter(entity1 -> !entity1.isRemoved()).map(commandSourceStack::withEntity).toList();
        };
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createRelationOperations(
        CommandNode<CommandSourceStack> node, LiteralArgumentBuilder<CommandSourceStack> argumentBuilder
    ) {
        return argumentBuilder.then(
                Commands.literal("owner")
                    .fork(
                        node,
                        expandOneToOneEntityRelation(
                            entity -> entity instanceof OwnableEntity ownableEntity ? Optional.ofNullable(ownableEntity.getOwner()) : Optional.empty()
                        )
                    )
            )
            .then(
                Commands.literal("leasher")
                    .fork(
                        node,
                        expandOneToOneEntityRelation(
                            entity -> entity instanceof Leashable leashable ? Optional.ofNullable(leashable.getLeashHolder()) : Optional.empty()
                        )
                    )
            )
            .then(
                Commands.literal("target")
                    .fork(
                        node,
                        expandOneToOneEntityRelation(
                            entity -> entity instanceof Targeting targeting ? Optional.ofNullable(targeting.getTarget()) : Optional.empty()
                        )
                    )
            )
            .then(
                Commands.literal("attacker")
                    .fork(
                        node,
                        expandOneToOneEntityRelation(
                            entity -> entity instanceof Attackable attackable ? Optional.ofNullable(attackable.getLastAttacker()) : Optional.empty()
                        )
                    )
            )
            .then(Commands.literal("vehicle").fork(node, expandOneToOneEntityRelation(entity -> Optional.ofNullable(entity.getVehicle()))))
            .then(Commands.literal("controller").fork(node, expandOneToOneEntityRelation(entity -> Optional.ofNullable(entity.getControllingPassenger()))))
            .then(
                Commands.literal("origin")
                    .fork(
                        node,
                        expandOneToOneEntityRelation(
                            entity -> entity instanceof TraceableEntity traceableEntity ? Optional.ofNullable(traceableEntity.getOwner()) : Optional.empty()
                        )
                    )
            )
            .then(Commands.literal("passengers").fork(node, expandOneToManyEntityRelation(entity -> entity.getPassengers().stream())));
    }

    private static CommandSourceStack spawnEntityAndRedirect(CommandSourceStack source, Holder.Reference<EntityType<?>> entityType) throws CommandSyntaxException {
        Entity entity = SummonCommand.createEntity(source, entityType, source.getPosition(), new CompoundTag(), true);
        return source.withEntity(entity);
    }

    public static <T extends ExecutionCommandSource<T>> void scheduleFunctionConditionsAndTest(
        T originalSource,
        List<T> sources,
        Function<T, T> sourceModifier,
        IntPredicate successCheck,
        ContextChain<T> contextChain,
        @Nullable CompoundTag arguments,
        ExecutionControl<T> executionControl,
        InCommandFunction<CommandContext<T>, Collection<CommandFunction<T>>> functionsGetter,
        ChainModifiers chainModifiers
    ) {
        List<T> list = new ArrayList<>(sources.size());

        Collection<CommandFunction<T>> collection;
        try {
            collection = functionsGetter.apply(contextChain.getTopContext().copyFor(originalSource));
        } catch (CommandSyntaxException var18) {
            originalSource.handleError(var18, chainModifiers.isForked(), executionControl.tracer());
            return;
        }

        int size = collection.size();
        if (size != 0) {
            List<InstantiatedFunction<T>> list1 = new ArrayList<>(size);

            try {
                for (CommandFunction<T> commandFunction : collection) {
                    try {
                        list1.add(commandFunction.instantiate(arguments, originalSource.dispatcher()));
                    } catch (FunctionInstantiationException var17) {
                        throw ERROR_FUNCTION_CONDITION_INSTANTATION_FAILURE.create(commandFunction.id(), var17.messageComponent());
                    }
                }
            } catch (CommandSyntaxException var19) {
                originalSource.handleError(var19, chainModifiers.isForked(), executionControl.tracer());
            }

            for (T executionCommandSource : sources) {
                T executionCommandSource1 = (T)sourceModifier.apply(executionCommandSource.clearCallbacks());
                CommandResultCallback commandResultCallback = (success, result) -> {
                    if (successCheck.test(result)) {
                        list.add(executionCommandSource);
                    }
                };
                executionControl.queueNext(
                    new IsolatedCall<>(
                        executionControl1 -> {
                            for (InstantiatedFunction<T> instantiatedFunction : list1) {
                                executionControl1.queueNext(
                                    new CallFunction<>(instantiatedFunction, executionControl1.currentFrame().returnValueConsumer(), true)
                                        .bind(executionCommandSource1)
                                );
                            }

                            executionControl1.queueNext(FallthroughTask.instance());
                        },
                        commandResultCallback
                    )
                );
            }

            ContextChain<T> contextChain1 = contextChain.nextStage();
            String input = contextChain.getTopContext().getInput();
            executionControl.queueNext(new BuildContexts.Continuation<>(input, contextChain1, chainModifiers, originalSource, list));
        }
    }

    @FunctionalInterface
    interface CommandNumericPredicate {
        int test(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface CommandPredicate {
        boolean test(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    static class ExecuteIfFunctionCustomModifier implements CustomModifierExecutor.ModifierAdapter<CommandSourceStack> {
        private final IntPredicate check;

        ExecuteIfFunctionCustomModifier(boolean invert) {
            this.check = invert ? i -> i != 0 : i -> i == 0;
        }

        @Override
        public void apply(
            CommandSourceStack originalSource,
            List<CommandSourceStack> sources,
            ContextChain<CommandSourceStack> contextChain,
            ChainModifiers chainModifiers,
            ExecutionControl<CommandSourceStack> executionControl
        ) {
            ExecuteCommand.scheduleFunctionConditionsAndTest(
                originalSource,
                sources,
                FunctionCommand::modifySenderForExecution,
                this.check,
                contextChain,
                null,
                executionControl,
                context -> FunctionArgument.getFunctions(context, "name"),
                chainModifiers
            );
        }
    }

    @FunctionalInterface
    interface IntBiPredicate {
        boolean test(int value1, int value2);
    }
}
