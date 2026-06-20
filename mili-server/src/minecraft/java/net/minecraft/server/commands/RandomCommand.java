package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequences;
import org.jspecify.annotations.Nullable;

public class RandomCommand {
    private static final SimpleCommandExceptionType ERROR_RANGE_TOO_LARGE = new SimpleCommandExceptionType(
        Component.translatable("commands.random.error.range_too_large")
    );
    private static final SimpleCommandExceptionType ERROR_RANGE_TOO_SMALL = new SimpleCommandExceptionType(
        Component.translatable("commands.random.error.range_too_small")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("random")
                .then(drawRandomValueTree("value", false))
                .then(drawRandomValueTree("roll", true))
                .then(
                    Commands.literal("reset")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(
                            Commands.literal("*")
                                .executes(context -> resetAllSequences(context.getSource()))
                                .then(
                                    Commands.argument("seed", IntegerArgumentType.integer())
                                        .executes(
                                            context -> resetAllSequencesAndSetNewDefaults(
                                                context.getSource(), IntegerArgumentType.getInteger(context, "seed"), true, true
                                            )
                                        )
                                        .then(
                                            Commands.argument("includeWorldSeed", BoolArgumentType.bool())
                                                .executes(
                                                    context -> resetAllSequencesAndSetNewDefaults(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "seed"),
                                                        BoolArgumentType.getBool(context, "includeWorldSeed"),
                                                        true
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("includeSequenceId", BoolArgumentType.bool())
                                                        .executes(
                                                            context -> resetAllSequencesAndSetNewDefaults(
                                                                context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "seed"),
                                                                BoolArgumentType.getBool(context, "includeWorldSeed"),
                                                                BoolArgumentType.getBool(context, "includeSequenceId")
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.argument("sequence", IdentifierArgument.id())
                                .suggests(RandomCommand::suggestRandomSequence)
                                .executes(context -> resetSequence(context.getSource(), IdentifierArgument.getId(context, "sequence")))
                                .then(
                                    Commands.argument("seed", IntegerArgumentType.integer())
                                        .executes(
                                            context -> resetSequence(
                                                context.getSource(),
                                                IdentifierArgument.getId(context, "sequence"),
                                                IntegerArgumentType.getInteger(context, "seed"),
                                                true,
                                                true
                                            )
                                        )
                                        .then(
                                            Commands.argument("includeWorldSeed", BoolArgumentType.bool())
                                                .executes(
                                                    context -> resetSequence(
                                                        context.getSource(),
                                                        IdentifierArgument.getId(context, "sequence"),
                                                        IntegerArgumentType.getInteger(context, "seed"),
                                                        BoolArgumentType.getBool(context, "includeWorldSeed"),
                                                        true
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("includeSequenceId", BoolArgumentType.bool())
                                                        .executes(
                                                            context -> resetSequence(
                                                                context.getSource(),
                                                                IdentifierArgument.getId(context, "sequence"),
                                                                IntegerArgumentType.getInteger(context, "seed"),
                                                                BoolArgumentType.getBool(context, "includeWorldSeed"),
                                                                BoolArgumentType.getBool(context, "includeSequenceId")
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> drawRandomValueTree(String subcommand, boolean displayResult) {
        return Commands.literal(subcommand)
            .then(
                Commands.argument("range", RangeArgument.intRange())
                    .executes(context -> randomSample(context.getSource(), RangeArgument.Ints.getRange(context, "range"), null, displayResult))
                    .then(
                        Commands.argument("sequence", IdentifierArgument.id())
                            .suggests(RandomCommand::suggestRandomSequence)
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes(
                                context -> randomSample(
                                    context.getSource(),
                                    RangeArgument.Ints.getRange(context, "range"),
                                    IdentifierArgument.getId(context, "sequence"),
                                    displayResult
                                )
                            )
                    )
            );
    }

    private static CompletableFuture<Suggestions> suggestRandomSequence(CommandContext<CommandSourceStack> context, SuggestionsBuilder suggestionsBuilder) {
        List<String> list = Lists.newArrayList();
        context.getSource().getLevel().getRandomSequences().forAllSequences((identifier, randomSequence) -> list.add(identifier.toString()));
        return SharedSuggestionProvider.suggest(list, suggestionsBuilder);
    }

    private static int randomSample(CommandSourceStack source, MinMaxBounds.Ints range, @Nullable Identifier sequence, boolean displayResult) throws CommandSyntaxException {
        RandomSource randomSequence;
        if (sequence != null) {
            randomSequence = source.getLevel().getRandomSequence(sequence);
        } else {
            randomSequence = source.getLevel().getRandom();
        }

        int i = range.min().orElse(Integer.MIN_VALUE);
        int i1 = range.max().orElse(Integer.MAX_VALUE);
        long l = (long)i1 - i;
        if (l == 0L) {
            throw ERROR_RANGE_TOO_SMALL.create();
        } else if (l >= 2147483647L) {
            throw ERROR_RANGE_TOO_LARGE.create();
        } else {
            int i2 = Mth.randomBetweenInclusive(randomSequence, i, i1);
            if (displayResult) {
                source.getServer()
                    .getPlayerList()
                    .broadcastSystemMessage(Component.translatable("commands.random.roll", source.getDisplayName(), i2, i, i1), false);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.random.sample.success", i2), false);
            }

            return i2;
        }
    }

    private static int resetSequence(CommandSourceStack source, Identifier sequence) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        level.getRandomSequences().reset(sequence, level.getSeed());
        source.sendSuccess(() -> Component.translatable("commands.random.reset.success", Component.translationArg(sequence)), false);
        return 1;
    }

    private static int resetSequence(CommandSourceStack source, Identifier sequence, int seed, boolean includeWorldSeed, boolean includeSequenceId) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        level.getRandomSequences().reset(sequence, level.getSeed(), seed, includeWorldSeed, includeSequenceId);
        source.sendSuccess(() -> Component.translatable("commands.random.reset.success", Component.translationArg(sequence)), false);
        return 1;
    }

    private static int resetAllSequences(CommandSourceStack source) {
        int i = source.getLevel().getRandomSequences().clear();
        source.sendSuccess(() -> Component.translatable("commands.random.reset.all.success", i), false);
        return i;
    }

    private static int resetAllSequencesAndSetNewDefaults(CommandSourceStack source, int seed, boolean includeWorldSeed, boolean includeSequenceId) {
        RandomSequences randomSequences = source.getLevel().getRandomSequences();
        randomSequences.setSeedDefaults(seed, includeWorldSeed, includeSequenceId);
        int i = randomSequences.clear();
        source.sendSuccess(() -> Component.translatable("commands.random.reset.all.success", i), false);
        return i;
    }
}
