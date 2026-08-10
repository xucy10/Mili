package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Stopwatch;
import net.minecraft.world.Stopwatches;

public class StopwatchCommand {
    private static final DynamicCommandExceptionType ERROR_ALREADY_EXISTS = new DynamicCommandExceptionType(
        object -> Component.translatableEscape("commands.stopwatch.already_exists", object)
    );
    public static final DynamicCommandExceptionType ERROR_DOES_NOT_EXIST = new DynamicCommandExceptionType(
        object -> Component.translatableEscape("commands.stopwatch.does_not_exist", object)
    );
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_STOPWATCHES = (context, suggestionsBuilder) -> SharedSuggestionProvider.suggestResource(
        context.getSource().getServer().getStopwatches().ids(), suggestionsBuilder
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("stopwatch")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("create")
                        .then(
                            Commands.argument("id", IdentifierArgument.id())
                                .executes(context -> createStopwatch(context.getSource(), IdentifierArgument.getId(context, "id")))
                        )
                )
                .then(
                    Commands.literal("query")
                        .then(
                            Commands.argument("id", IdentifierArgument.id())
                                .suggests(SUGGEST_STOPWATCHES)
                                .then(
                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                        .executes(
                                            context -> queryStopwatch(
                                                context.getSource(), IdentifierArgument.getId(context, "id"), DoubleArgumentType.getDouble(context, "scale")
                                            )
                                        )
                                )
                                .executes(context -> queryStopwatch(context.getSource(), IdentifierArgument.getId(context, "id"), 1.0))
                        )
                )
                .then(
                    Commands.literal("restart")
                        .then(
                            Commands.argument("id", IdentifierArgument.id())
                                .suggests(SUGGEST_STOPWATCHES)
                                .executes(context -> restartStopwatch(context.getSource(), IdentifierArgument.getId(context, "id")))
                        )
                )
                .then(
                    Commands.literal("remove")
                        .then(
                            Commands.argument("id", IdentifierArgument.id())
                                .suggests(SUGGEST_STOPWATCHES)
                                .executes(context -> removeStopwatch(context.getSource(), IdentifierArgument.getId(context, "id")))
                        )
                )
        );
    }

    private static int createStopwatch(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        Stopwatches stopwatches = server.getStopwatches();
        Stopwatch stopwatch = new Stopwatch(Stopwatches.currentTime());
        if (!stopwatches.add(id, stopwatch)) {
            throw ERROR_ALREADY_EXISTS.create(id);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.stopwatch.create.success", Component.translationArg(id)), true);
            return 1;
        }
    }

    private static int queryStopwatch(CommandSourceStack source, Identifier id, double scale) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        Stopwatches stopwatches = server.getStopwatches();
        Stopwatch stopwatch = stopwatches.get(id);
        if (stopwatch == null) {
            throw ERROR_DOES_NOT_EXIST.create(id);
        } else {
            long l = Stopwatches.currentTime();
            double d = stopwatch.elapsedSeconds(l);
            source.sendSuccess(() -> Component.translatable("commands.stopwatch.query", Component.translationArg(id), d), true);
            return (int)(d * scale);
        }
    }

    private static int restartStopwatch(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        Stopwatches stopwatches = server.getStopwatches();
        if (!stopwatches.update(id, stopwatch -> new Stopwatch(Stopwatches.currentTime()))) {
            throw ERROR_DOES_NOT_EXIST.create(id);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.stopwatch.restart.success", Component.translationArg(id)), true);
            return 1;
        }
    }

    private static int removeStopwatch(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        Stopwatches stopwatches = server.getStopwatches();
        if (!stopwatches.remove(id)) {
            throw ERROR_DOES_NOT_EXIST.create(id);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.stopwatch.remove.success", Component.translationArg(id)), true);
            return 1;
        }
    }
}
