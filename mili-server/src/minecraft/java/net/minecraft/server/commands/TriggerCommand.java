package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class TriggerCommand {
    private static final SimpleCommandExceptionType ERROR_NOT_PRIMED = new SimpleCommandExceptionType(
        Component.translatable("commands.trigger.failed.unprimed")
    );
    private static final SimpleCommandExceptionType ERROR_INVALID_OBJECTIVE = new SimpleCommandExceptionType(
        Component.translatable("commands.trigger.failed.invalid")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("trigger")
                .then(
                    Commands.argument("objective", ObjectiveArgument.objective())
                        .suggests((context, builder) -> suggestObjectives(context.getSource(), builder))
                        .executes(
                            context -> simpleTrigger(
                                context.getSource(), context.getSource().getPlayerOrException(), ObjectiveArgument.getObjective(context, "objective")
                            )
                        )
                        .then(
                            Commands.literal("add")
                                .then(
                                    Commands.argument("value", IntegerArgumentType.integer())
                                        .executes(
                                            context -> addValue(
                                                context.getSource(),
                                                context.getSource().getPlayerOrException(),
                                                ObjectiveArgument.getObjective(context, "objective"),
                                                IntegerArgumentType.getInteger(context, "value")
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("set")
                                .then(
                                    Commands.argument("value", IntegerArgumentType.integer())
                                        .executes(
                                            context -> setValue(
                                                context.getSource(),
                                                context.getSource().getPlayerOrException(),
                                                ObjectiveArgument.getObjective(context, "objective"),
                                                IntegerArgumentType.getInteger(context, "value")
                                            )
                                        )
                                )
                        )
                )
        );
    }

    public static CompletableFuture<Suggestions> suggestObjectives(CommandSourceStack source, SuggestionsBuilder builder) {
        ScoreHolder entity = source.getEntity();
        List<String> list = Lists.newArrayList();
        if (entity != null) {
            Scoreboard scoreboard = source.getServer().getScoreboard();

            for (Objective objective : scoreboard.getObjectives()) {
                if (objective.getCriteria() == ObjectiveCriteria.TRIGGER) {
                    ReadOnlyScoreInfo playerScoreInfo = scoreboard.getPlayerScoreInfo(entity, objective);
                    if (playerScoreInfo != null && !playerScoreInfo.isLocked()) {
                        list.add(objective.getName());
                    }
                }
            }
        }

        return SharedSuggestionProvider.suggest(list, builder);
    }

    private static int addValue(CommandSourceStack source, ServerPlayer player, Objective objective, int value) throws CommandSyntaxException {
        ScoreAccess score = getScore(source.getServer().getScoreboard(), player, objective);
        int i = score.add(value);
        source.sendSuccess(() -> Component.translatable("commands.trigger.add.success", objective.getFormattedDisplayName(), value), true);
        return i;
    }

    private static int setValue(CommandSourceStack source, ServerPlayer player, Objective objective, int value) throws CommandSyntaxException {
        ScoreAccess score = getScore(source.getServer().getScoreboard(), player, objective);
        score.set(value);
        source.sendSuccess(() -> Component.translatable("commands.trigger.set.success", objective.getFormattedDisplayName(), value), true);
        return value;
    }

    private static int simpleTrigger(CommandSourceStack source, ServerPlayer player, Objective objective) throws CommandSyntaxException {
        ScoreAccess score = getScore(source.getServer().getScoreboard(), player, objective);
        int i = score.add(1);
        source.sendSuccess(() -> Component.translatable("commands.trigger.simple.success", objective.getFormattedDisplayName()), true);
        return i;
    }

    private static ScoreAccess getScore(Scoreboard scoreboard, ScoreHolder scoreHolder, Objective objective) throws CommandSyntaxException {
        if (objective.getCriteria() != ObjectiveCriteria.TRIGGER) {
            throw ERROR_INVALID_OBJECTIVE.create();
        } else {
            ReadOnlyScoreInfo playerScoreInfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective);
            if (playerScoreInfo != null && !playerScoreInfo.isLocked()) {
                ScoreAccess playerScore = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);
                playerScore.lock();
                return playerScore;
            } else {
                throw ERROR_NOT_PRIMED.create();
            }
        }
    }
}
