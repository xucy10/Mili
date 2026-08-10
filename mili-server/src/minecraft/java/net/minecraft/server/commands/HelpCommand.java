package net.minecraft.server.commands;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class HelpCommand {
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.help.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("help")
                .executes(context -> {
                    Map<CommandNode<CommandSourceStack>, String> smartUsage = dispatcher.getSmartUsage(dispatcher.getRoot(), context.getSource());

                    for (String string : smartUsage.values()) {
                        context.getSource().sendSuccess(() -> Component.literal("/" + string), false);
                    }

                    return smartUsage.size();
                })
                .then(
                    Commands.argument("command", StringArgumentType.greedyString())
                        .executes(
                            context -> {
                                ParseResults<CommandSourceStack> parseResults = dispatcher.parse(
                                    StringArgumentType.getString(context, "command"), context.getSource()
                                );
                                if (parseResults.getContext().getNodes().isEmpty()) {
                                    throw ERROR_FAILED.create();
                                } else {
                                    Map<CommandNode<CommandSourceStack>, String> smartUsage = dispatcher.getSmartUsage(
                                        Iterables.getLast(parseResults.getContext().getNodes()).getNode(), context.getSource()
                                    );

                                    for (String string : smartUsage.values()) {
                                        context.getSource()
                                            .sendSuccess(() -> Component.literal("/" + parseResults.getReader().getString() + " " + string), false);
                                    }

                                    return smartUsage.size();
                                }
                            }
                        )
                )
        );
    }
}
