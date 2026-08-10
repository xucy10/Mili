package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public class TellRawCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("tellraw")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("message", ComponentArgument.textComponent(context)).executes(commandContext -> {
                            int i = 0;

                            for (ServerPlayer serverPlayer : EntityArgument.getPlayers(commandContext, "targets")) {
                                serverPlayer.sendSystemMessage(ComponentArgument.getResolvedComponent(commandContext, "message", serverPlayer), false);
                                i++;
                            }

                            return i;
                        }))
                )
        );
    }
}
