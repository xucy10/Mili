package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.ObjectiveCriteriaArgument;
import net.minecraft.commands.arguments.OperationArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.ScoreboardSlotArgument;
import net.minecraft.commands.arguments.StyleArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jspecify.annotations.Nullable;

public class ScoreboardCommand {
    private static final SimpleCommandExceptionType ERROR_OBJECTIVE_ALREADY_EXISTS = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.objectives.add.duplicate")
    );
    private static final SimpleCommandExceptionType ERROR_DISPLAY_SLOT_ALREADY_EMPTY = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.objectives.display.alreadyEmpty")
    );
    private static final SimpleCommandExceptionType ERROR_DISPLAY_SLOT_ALREADY_SET = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.objectives.display.alreadySet")
    );
    private static final SimpleCommandExceptionType ERROR_TRIGGER_ALREADY_ENABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.players.enable.failed")
    );
    private static final SimpleCommandExceptionType ERROR_NOT_TRIGGER = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.players.enable.invalid")
    );
    private static final Dynamic2CommandExceptionType ERROR_NO_VALUE = new Dynamic2CommandExceptionType(
        (objective, target) -> Component.translatableEscape("commands.scoreboard.players.get.null", objective, target)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("scoreboard")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("objectives")
                        .then(Commands.literal("list").executes(commandContext -> listObjectives(commandContext.getSource())))
                        .then(
                            Commands.literal("add")
                                .then(
                                    Commands.argument("objective", StringArgumentType.word())
                                        .then(
                                            Commands.argument("criteria", ObjectiveCriteriaArgument.criteria())
                                                .executes(
                                                    context1 -> addObjective(
                                                        context1.getSource(),
                                                        StringArgumentType.getString(context1, "objective"),
                                                        ObjectiveCriteriaArgument.getCriteria(context1, "criteria"),
                                                        Component.literal(StringArgumentType.getString(context1, "objective"))
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("displayName", ComponentArgument.textComponent(context))
                                                        .executes(
                                                            context1 -> addObjective(
                                                                context1.getSource(),
                                                                StringArgumentType.getString(context1, "objective"),
                                                                ObjectiveCriteriaArgument.getCriteria(context1, "criteria"),
                                                                ComponentArgument.getResolvedComponent(context1, "displayName")
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("modify")
                                .then(
                                    Commands.argument("objective", ObjectiveArgument.objective())
                                        .then(
                                            Commands.literal("displayname")
                                                .then(
                                                    Commands.argument("displayName", ComponentArgument.textComponent(context))
                                                        .executes(
                                                            context1 -> setDisplayName(
                                                                context1.getSource(),
                                                                ObjectiveArgument.getObjective(context1, "objective"),
                                                                ComponentArgument.getResolvedComponent(context1, "displayName")
                                                            )
                                                        )
                                                )
                                        )
                                        .then(createRenderTypeModify())
                                        .then(
                                            Commands.literal("displayautoupdate")
                                                .then(
                                                    Commands.argument("value", BoolArgumentType.bool())
                                                        .executes(
                                                            context1 -> setDisplayAutoUpdate(
                                                                context1.getSource(),
                                                                ObjectiveArgument.getObjective(context1, "objective"),
                                                                BoolArgumentType.getBool(context1, "value")
                                                            )
                                                        )
                                                )
                                        )
                                        .then(
                                            addNumberFormats(
                                                context,
                                                Commands.literal("numberformat"),
                                                (context1, format) -> setObjectiveFormat(
                                                    context1.getSource(), ObjectiveArgument.getObjective(context1, "objective"), format
                                                )
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("remove")
                                .then(
                                    Commands.argument("objective", ObjectiveArgument.objective())
                                        .executes(
                                            commandContext -> removeObjective(
                                                commandContext.getSource(), ObjectiveArgument.getObjective(commandContext, "objective")
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("setdisplay")
                                .then(
                                    Commands.argument("slot", ScoreboardSlotArgument.displaySlot())
                                        .executes(context1 -> clearDisplaySlot(context1.getSource(), ScoreboardSlotArgument.getDisplaySlot(context1, "slot")))
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .executes(
                                                    context1 -> setDisplaySlot(
                                                        context1.getSource(),
                                                        ScoreboardSlotArgument.getDisplaySlot(context1, "slot"),
                                                        ObjectiveArgument.getObjective(context1, "objective")
                                                    )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("players")
                        .then(
                            Commands.literal("list")
                                .executes(context1 -> listTrackedPlayers(context1.getSource()))
                                .then(
                                    Commands.argument("target", ScoreHolderArgument.scoreHolder())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .executes(context1 -> listTrackedPlayerScores(context1.getSource(), ScoreHolderArgument.getName(context1, "target")))
                                )
                        )
                        .then(
                            Commands.literal("set")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("score", IntegerArgumentType.integer())
                                                        .executes(
                                                            context1 -> setScore(
                                                                context1.getSource(),
                                                                ScoreHolderArgument.getNamesWithDefaultWildcard(context1, "targets"),
                                                                ObjectiveArgument.getWritableObjective(context1, "objective"),
                                                                IntegerArgumentType.getInteger(context1, "score")
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("get")
                                .then(
                                    Commands.argument("target", ScoreHolderArgument.scoreHolder())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .executes(
                                                    context1 -> getScore(
                                                        context1.getSource(),
                                                        ScoreHolderArgument.getName(context1, "target"),
                                                        ObjectiveArgument.getObjective(context1, "objective")
                                                    )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("add")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("score", IntegerArgumentType.integer(0))
                                                        .executes(
                                                            context1 -> addScore(
                                                                context1.getSource(),
                                                                ScoreHolderArgument.getNamesWithDefaultWildcard(context1, "targets"),
                                                                ObjectiveArgument.getWritableObjective(context1, "objective"),
                                                                IntegerArgumentType.getInteger(context1, "score")
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("remove")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("score", IntegerArgumentType.integer(0))
                                                        .executes(
                                                            context1 -> removeScore(
                                                                context1.getSource(),
                                                                ScoreHolderArgument.getNamesWithDefaultWildcard(context1, "targets"),
                                                                ObjectiveArgument.getWritableObjective(context1, "objective"),
                                                                IntegerArgumentType.getInteger(context1, "score")
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("reset")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .executes(
                                            context1 -> resetScores(context1.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(context1, "targets"))
                                        )
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .executes(
                                                    commandContext -> resetScore(
                                                        commandContext.getSource(),
                                                        ScoreHolderArgument.getNamesWithDefaultWildcard(commandContext, "targets"),
                                                        ObjectiveArgument.getObjective(commandContext, "objective")
                                                    )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("enable")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .suggests(
                                                    (commandContext, suggestionsBuilder) -> suggestTriggers(
                                                        commandContext.getSource(),
                                                        ScoreHolderArgument.getNamesWithDefaultWildcard(commandContext, "targets"),
                                                        suggestionsBuilder
                                                    )
                                                )
                                                .executes(
                                                    commandContext -> enableTrigger(
                                                        commandContext.getSource(),
                                                        ScoreHolderArgument.getNamesWithDefaultWildcard(commandContext, "targets"),
                                                        ObjectiveArgument.getObjective(commandContext, "objective")
                                                    )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("display")
                                .then(
                                    Commands.literal("name")
                                        .then(
                                            Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                                .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                .then(
                                                    Commands.argument("objective", ObjectiveArgument.objective())
                                                        .then(
                                                            Commands.argument("name", ComponentArgument.textComponent(context))
                                                                .executes(
                                                                    commandContext -> setScoreDisplay(
                                                                        commandContext.getSource(),
                                                                        ScoreHolderArgument.getNamesWithDefaultWildcard(commandContext, "targets"),
                                                                        ObjectiveArgument.getObjective(commandContext, "objective"),
                                                                        ComponentArgument.getResolvedComponent(commandContext, "name")
                                                                    )
                                                                )
                                                        )
                                                        .executes(
                                                            commandContext -> setScoreDisplay(
                                                                commandContext.getSource(),
                                                                ScoreHolderArgument.getNamesWithDefaultWildcard(commandContext, "targets"),
                                                                ObjectiveArgument.getObjective(commandContext, "objective"),
                                                                null
                                                            )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("numberformat")
                                        .then(
                                            Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                                .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                .then(
                                                    addNumberFormats(
                                                        context,
                                                        Commands.argument("objective", ObjectiveArgument.objective()),
                                                        (context1, format) -> setScoreNumberFormat(
                                                            context1.getSource(),
                                                            ScoreHolderArgument.getNamesWithDefaultWildcard(context1, "targets"),
                                                            ObjectiveArgument.getObjective(context1, "objective"),
                                                            format
                                                        )
                                                    )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("operation")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("targetObjective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("operation", OperationArgument.operation())
                                                        .then(
                                                            Commands.argument("source", ScoreHolderArgument.scoreHolders())
                                                                .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                                .then(
                                                                    Commands.argument("sourceObjective", ObjectiveArgument.objective())
                                                                        .executes(
                                                                            commandContext -> performOperation(
                                                                                commandContext.getSource(),
                                                                                ScoreHolderArgument.getNamesWithDefaultWildcard(commandContext, "targets"),
                                                                                ObjectiveArgument.getWritableObjective(commandContext, "targetObjective"),
                                                                                OperationArgument.getOperation(commandContext, "operation"),
                                                                                ScoreHolderArgument.getNamesWithDefaultWildcard(commandContext, "source"),
                                                                                ObjectiveArgument.getObjective(commandContext, "sourceObjective")
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

    private static ArgumentBuilder<CommandSourceStack, ?> addNumberFormats(
        CommandBuildContext context, ArgumentBuilder<CommandSourceStack, ?> argumentBuilder, ScoreboardCommand.NumberFormatCommandExecutor executor
    ) {
        return argumentBuilder.then(Commands.literal("blank").executes(commandContext -> executor.run(commandContext, BlankFormat.INSTANCE)))
            .then(Commands.literal("fixed").then(Commands.argument("contents", ComponentArgument.textComponent(context)).executes(commandContext -> {
                Component resolvedComponent = ComponentArgument.getResolvedComponent(commandContext, "contents");
                return executor.run(commandContext, new FixedFormat(resolvedComponent));
            })))
            .then(Commands.literal("styled").then(Commands.argument("style", StyleArgument.style(context)).executes(commandContext -> {
                Style style = StyleArgument.getStyle(commandContext, "style");
                return executor.run(commandContext, new StyledFormat(style));
            })))
            .executes(commandContext -> executor.run(commandContext, null));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createRenderTypeModify() {
        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("rendertype");

        for (ObjectiveCriteria.RenderType renderType : ObjectiveCriteria.RenderType.values()) {
            literalArgumentBuilder.then(
                Commands.literal(renderType.getId())
                    .executes(
                        commandContext -> setRenderType(commandContext.getSource(), ObjectiveArgument.getObjective(commandContext, "objective"), renderType)
                    )
            );
        }

        return literalArgumentBuilder;
    }

    private static CompletableFuture<Suggestions> suggestTriggers(CommandSourceStack source, Collection<ScoreHolder> targets, SuggestionsBuilder suggestions) {
        List<String> list = Lists.newArrayList();
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (Objective objective : scoreboard.getObjectives()) {
            if (objective.getCriteria() == ObjectiveCriteria.TRIGGER) {
                boolean flag = false;

                for (ScoreHolder scoreHolder : targets) {
                    ReadOnlyScoreInfo playerScoreInfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective);
                    if (playerScoreInfo == null || playerScoreInfo.isLocked()) {
                        flag = true;
                        break;
                    }
                }

                if (flag) {
                    list.add(objective.getName());
                }
            }
        }

        return SharedSuggestionProvider.suggest(list, suggestions);
    }

    private static int getScore(CommandSourceStack source, ScoreHolder scoreHolder, Objective objective) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        ReadOnlyScoreInfo playerScoreInfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective);
        if (playerScoreInfo == null) {
            throw ERROR_NO_VALUE.create(objective.getName(), scoreHolder.getFeedbackDisplayName());
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.scoreboard.players.get.success",
                    scoreHolder.getFeedbackDisplayName(),
                    playerScoreInfo.value(),
                    objective.getFormattedDisplayName()
                ),
                false
            );
            return playerScoreInfo.value();
        }
    }

    private static Component getFirstTargetName(Collection<ScoreHolder> scores) {
        return scores.iterator().next().getFeedbackDisplayName();
    }

    private static int performOperation(
        CommandSourceStack source,
        Collection<ScoreHolder> targets,
        Objective targetObjectives,
        OperationArgument.Operation operation,
        Collection<ScoreHolder> sourceEntities,
        Objective sourceObjective
    ) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        int i = 0;

        for (ScoreHolder scoreHolder : targets) {
            ScoreAccess playerScore = scoreboard.getOrCreatePlayerScore(scoreHolder, targetObjectives);

            for (ScoreHolder scoreHolder1 : sourceEntities) {
                ScoreAccess playerScore1 = scoreboard.getOrCreatePlayerScore(scoreHolder1, sourceObjective);
                operation.apply(playerScore, playerScore1);
            }

            i += playerScore.get();
        }

        if (targets.size() == 1) {
            int i1 = i;
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.scoreboard.players.operation.success.single", targetObjectives.getFormattedDisplayName(), getFirstTargetName(targets), i1
                ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.scoreboard.players.operation.success.multiple", targetObjectives.getFormattedDisplayName(), targets.size()
                ),
                true
            );
        }

        return i;
    }

    private static int enableTrigger(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective) throws CommandSyntaxException {
        if (objective.getCriteria() != ObjectiveCriteria.TRIGGER) {
            throw ERROR_NOT_TRIGGER.create();
        } else {
            Scoreboard scoreboard = source.getServer().getScoreboard();
            int i = 0;

            for (ScoreHolder scoreHolder : targets) {
                ScoreAccess playerScore = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);
                if (playerScore.locked()) {
                    playerScore.unlock();
                    i++;
                }
            }

            if (i == 0) {
                throw ERROR_TRIGGER_ALREADY_ENABLED.create();
            } else {
                if (targets.size() == 1) {
                    source.sendSuccess(
                        () -> Component.translatable(
                            "commands.scoreboard.players.enable.success.single", objective.getFormattedDisplayName(), getFirstTargetName(targets)
                        ),
                        true
                    );
                } else {
                    source.sendSuccess(
                        () -> Component.translatable("commands.scoreboard.players.enable.success.multiple", objective.getFormattedDisplayName(), targets.size()),
                        true
                    );
                }

                return i;
            }
        }
    }

    private static int resetScores(CommandSourceStack source, Collection<ScoreHolder> targets) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreHolder : targets) {
            scoreboard.resetAllPlayerScores(scoreHolder);
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.players.reset.all.single", getFirstTargetName(targets)), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.players.reset.all.multiple", targets.size()), true);
        }

        return targets.size();
    }

    private static int resetScore(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreHolder : targets) {
            scoreboard.resetSinglePlayerScore(scoreHolder, objective);
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.scoreboard.players.reset.specific.single", objective.getFormattedDisplayName(), getFirstTargetName(targets)
                ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.reset.specific.multiple", objective.getFormattedDisplayName(), targets.size()), true
            );
        }

        return targets.size();
    }

    private static int setScore(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, int newValue) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreHolder : targets) {
            scoreboard.getOrCreatePlayerScore(scoreHolder, objective).set(newValue);
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.scoreboard.players.set.success.single", objective.getFormattedDisplayName(), getFirstTargetName(targets), newValue
                ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.set.success.multiple", objective.getFormattedDisplayName(), targets.size(), newValue),
                true
            );
        }

        return newValue * targets.size();
    }

    private static int setScoreDisplay(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, @Nullable Component displayName) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreHolder : targets) {
            scoreboard.getOrCreatePlayerScore(scoreHolder, objective).display(displayName);
        }

        if (displayName == null) {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                        "commands.scoreboard.players.display.name.clear.success.single", getFirstTargetName(targets), objective.getFormattedDisplayName()
                    ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable(
                        "commands.scoreboard.players.display.name.clear.success.multiple", targets.size(), objective.getFormattedDisplayName()
                    ),
                    true
                );
            }
        } else if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.scoreboard.players.display.name.set.success.single",
                    displayName,
                    getFirstTargetName(targets),
                    objective.getFormattedDisplayName()
                ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.scoreboard.players.display.name.set.success.multiple", displayName, targets.size(), objective.getFormattedDisplayName()
                ),
                true
            );
        }

        return targets.size();
    }

    private static int setScoreNumberFormat(
        CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, @Nullable NumberFormat numberFormat
    ) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreHolder : targets) {
            scoreboard.getOrCreatePlayerScore(scoreHolder, objective).numberFormatOverride(numberFormat);
        }

        if (numberFormat == null) {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                        "commands.scoreboard.players.display.numberFormat.clear.success.single",
                        getFirstTargetName(targets),
                        objective.getFormattedDisplayName()
                    ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable(
                        "commands.scoreboard.players.display.numberFormat.clear.success.multiple", targets.size(), objective.getFormattedDisplayName()
                    ),
                    true
                );
            }
        } else if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.scoreboard.players.display.numberFormat.set.success.single", getFirstTargetName(targets), objective.getFormattedDisplayName()
                ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.scoreboard.players.display.numberFormat.set.success.multiple", targets.size(), objective.getFormattedDisplayName()
                ),
                true
            );
        }

        return targets.size();
    }

    private static int addScore(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, int amount) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        int i = 0;

        for (ScoreHolder scoreHolder : targets) {
            ScoreAccess playerScore = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);
            playerScore.set(playerScore.get() + amount);
            i += playerScore.get();
        }

        if (targets.size() == 1) {
            int i1 = i;
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.scoreboard.players.add.success.single", amount, objective.getFormattedDisplayName(), getFirstTargetName(targets), i1
                ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.add.success.multiple", amount, objective.getFormattedDisplayName(), targets.size()),
                true
            );
        }

        return i;
    }

    private static int removeScore(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, int amount) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        int i = 0;

        for (ScoreHolder scoreHolder : targets) {
            ScoreAccess playerScore = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);
            playerScore.set(playerScore.get() - amount);
            i += playerScore.get();
        }

        if (targets.size() == 1) {
            int i1 = i;
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.scoreboard.players.remove.success.single", amount, objective.getFormattedDisplayName(), getFirstTargetName(targets), i1
                ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.remove.success.multiple", amount, objective.getFormattedDisplayName(), targets.size()),
                true
            );
        }

        return i;
    }

    private static int listTrackedPlayers(CommandSourceStack source) {
        Collection<ScoreHolder> trackedPlayers = source.getServer().getScoreboard().getTrackedPlayers();
        if (trackedPlayers.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.players.list.empty"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.scoreboard.players.list.success",
                    trackedPlayers.size(),
                    ComponentUtils.formatList(trackedPlayers, ScoreHolder::getFeedbackDisplayName)
                ),
                false
            );
        }

        return trackedPlayers.size();
    }

    private static int listTrackedPlayerScores(CommandSourceStack source, ScoreHolder score) {
        Object2IntMap<Objective> map = source.getServer().getScoreboard().listPlayerScores(score);
        if (map.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.players.list.entity.empty", score.getFeedbackDisplayName()), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.list.entity.success", score.getFeedbackDisplayName(), map.size()), false
            );
            Object2IntMaps.fastForEach(
                map,
                entry -> source.sendSuccess(
                    () -> Component.translatable(
                        "commands.scoreboard.players.list.entity.entry", ((Objective)entry.getKey()).getFormattedDisplayName(), entry.getIntValue()
                    ),
                    false
                )
            );
        }

        return map.size();
    }

    private static int clearDisplaySlot(CommandSourceStack source, DisplaySlot slot) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        if (scoreboard.getDisplayObjective(slot) == null) {
            throw ERROR_DISPLAY_SLOT_ALREADY_EMPTY.create();
        } else {
            scoreboard.setDisplayObjective(slot, null);
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.display.cleared", slot.getSerializedName()), true);
            return 0;
        }
    }

    private static int setDisplaySlot(CommandSourceStack source, DisplaySlot slot, Objective objective) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        if (scoreboard.getDisplayObjective(slot) == objective) {
            throw ERROR_DISPLAY_SLOT_ALREADY_SET.create();
        } else {
            scoreboard.setDisplayObjective(slot, objective);
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.objectives.display.set", slot.getSerializedName(), objective.getDisplayName()), true
            );
            return 0;
        }
    }

    private static int setDisplayName(CommandSourceStack source, Objective objective, Component displayName) {
        if (!objective.getDisplayName().equals(displayName)) {
            objective.setDisplayName(displayName);
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.objectives.modify.displayname", objective.getName(), objective.getFormattedDisplayName()),
                true
            );
        }

        return 0;
    }

    private static int setDisplayAutoUpdate(CommandSourceStack source, Objective objective, boolean displayAutoUpdate) {
        if (objective.displayAutoUpdate() != displayAutoUpdate) {
            objective.setDisplayAutoUpdate(displayAutoUpdate);
            if (displayAutoUpdate) {
                source.sendSuccess(
                    () -> Component.translatable(
                        "commands.scoreboard.objectives.modify.displayAutoUpdate.enable", objective.getName(), objective.getFormattedDisplayName()
                    ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable(
                        "commands.scoreboard.objectives.modify.displayAutoUpdate.disable", objective.getName(), objective.getFormattedDisplayName()
                    ),
                    true
                );
            }
        }

        return 0;
    }

    private static int setObjectiveFormat(CommandSourceStack source, Objective objective, @Nullable NumberFormat format) {
        objective.setNumberFormat(format);
        if (format != null) {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.modify.objectiveFormat.set", objective.getName()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.modify.objectiveFormat.clear", objective.getName()), true);
        }

        return 0;
    }

    private static int setRenderType(CommandSourceStack source, Objective objective, ObjectiveCriteria.RenderType renderType) {
        if (objective.getRenderType() != renderType) {
            objective.setRenderType(renderType);
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.modify.rendertype", objective.getFormattedDisplayName()), true);
        }

        return 0;
    }

    private static int removeObjective(CommandSourceStack source, Objective objective) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        scoreboard.removeObjective(objective);
        source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.remove.success", objective.getFormattedDisplayName()), true);
        return scoreboard.getObjectives().size();
    }

    private static int addObjective(CommandSourceStack source, String name, ObjectiveCriteria criteria, Component displayName) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        if (scoreboard.getObjective(name) != null) {
            throw ERROR_OBJECTIVE_ALREADY_EXISTS.create();
        } else {
            scoreboard.addObjective(name, criteria, displayName, criteria.getDefaultRenderType(), false, null);
            Objective objective = scoreboard.getObjective(name);
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.add.success", objective.getFormattedDisplayName()), true);
            return scoreboard.getObjectives().size();
        }
    }

    private static int listObjectives(CommandSourceStack source) {
        Collection<Objective> objectives = source.getServer().getScoreboard().getObjectives();
        if (objectives.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.list.empty"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.scoreboard.objectives.list.success", objectives.size(), ComponentUtils.formatList(objectives, Objective::getFormattedDisplayName)
                ),
                false
            );
        }

        return objectives.size();
    }

    @FunctionalInterface
    public interface NumberFormatCommandExecutor {
        int run(CommandContext<CommandSourceStack> context, @Nullable NumberFormat format) throws CommandSyntaxException;
    }
}
