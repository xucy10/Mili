package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.MacroFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.timers.FunctionCallback;
import net.minecraft.world.level.timers.FunctionTagCallback;
import net.minecraft.world.level.timers.TimerQueue;

public class ScheduleCommand {
    private static final SimpleCommandExceptionType ERROR_SAME_TICK = new SimpleCommandExceptionType(Component.translatable("commands.schedule.same_tick"));
    private static final DynamicCommandExceptionType ERROR_CANT_REMOVE = new DynamicCommandExceptionType(
        functionName -> Component.translatableEscape("commands.schedule.cleared.failure", functionName)
    );
    private static final SimpleCommandExceptionType ERROR_MACRO = new SimpleCommandExceptionType(Component.translatableEscape("commands.schedule.macro"));
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_SCHEDULE = (context, builder) -> SharedSuggestionProvider.suggest(
        context.getSource().getLevel().serverLevelData.getScheduledEvents().getEventsIds(), builder // Paper - Make schedule command per-world
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("schedule")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("function")
                        .then(
                            Commands.argument("function", FunctionArgument.functions())
                                .suggests(FunctionCommand.SUGGEST_FUNCTION)
                                .then(
                                    Commands.argument("time", TimeArgument.time())
                                        .executes(
                                            context -> schedule(
                                                context.getSource(),
                                                FunctionArgument.getFunctionOrTag(context, "function"),
                                                IntegerArgumentType.getInteger(context, "time"),
                                                true
                                            )
                                        )
                                        .then(
                                            Commands.literal("append")
                                                .executes(
                                                    context -> schedule(
                                                        context.getSource(),
                                                        FunctionArgument.getFunctionOrTag(context, "function"),
                                                        IntegerArgumentType.getInteger(context, "time"),
                                                        false
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("replace")
                                                .executes(
                                                    context -> schedule(
                                                        context.getSource(),
                                                        FunctionArgument.getFunctionOrTag(context, "function"),
                                                        IntegerArgumentType.getInteger(context, "time"),
                                                        true
                                                    )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("clear")
                        .then(
                            Commands.argument("function", StringArgumentType.greedyString())
                                .suggests(SUGGEST_SCHEDULE)
                                .executes(context -> remove(context.getSource(), StringArgumentType.getString(context, "function")))
                        )
                )
        );
    }

    private static int schedule(
        CommandSourceStack source,
        Pair<Identifier, Either<CommandFunction<CommandSourceStack>, Collection<CommandFunction<CommandSourceStack>>>> function,
        int time,
        boolean append
    ) throws CommandSyntaxException {
        if (time == 0) {
            throw ERROR_SAME_TICK.create();
        } else {
            long l = source.getLevel().getGameTime() + time;
            Identifier identifier = function.getFirst();
            TimerQueue<MinecraftServer> scheduledEvents = source.getLevel().serverLevelData.overworldData().getScheduledEvents(); // CraftBukkit - SPIGOT-6667: Use world specific function timer
            Optional<CommandFunction<CommandSourceStack>> optional = function.getSecond().left();
            if (optional.isPresent()) {
                if (optional.get() instanceof MacroFunction) {
                    throw ERROR_MACRO.create();
                }

                String string = identifier.toString();
                if (append) {
                    scheduledEvents.remove(string);
                }

                scheduledEvents.schedule(string, l, new FunctionCallback(identifier));
                source.sendSuccess(() -> Component.translatable("commands.schedule.created.function", Component.translationArg(identifier), time, l), true);
            } else {
                String string = "#" + identifier;
                if (append) {
                    scheduledEvents.remove(string);
                }

                scheduledEvents.schedule(string, l, new FunctionTagCallback(identifier));
                source.sendSuccess(() -> Component.translatable("commands.schedule.created.tag", Component.translationArg(identifier), time, l), true);
            }

            return Math.floorMod(l, Integer.MAX_VALUE);
        }
    }

    private static int remove(CommandSourceStack source, String function) throws CommandSyntaxException {
        int i = source.getLevel().serverLevelData.overworldData().getScheduledEvents().remove(function); // Paper - Make schedule command per-world
        if (i == 0) {
            throw ERROR_CANT_REMOVE.create(function);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.schedule.cleared.success", i, function), true);
            return i;
        }
    }
}
