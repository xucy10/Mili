package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class SetPlayerIdleTimeoutCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("setidletimeout")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(
                    Commands.argument("minutes", IntegerArgumentType.integer(0))
                        .executes(commandContext -> setIdleTimeout(commandContext.getSource(), IntegerArgumentType.getInteger(commandContext, "minutes")))
                )
        );
    }

    private static int setIdleTimeout(CommandSourceStack source, int idleTimeout) {
        source.getServer().setPlayerIdleTimeout(idleTimeout);
        if (idleTimeout > 0) {
            source.sendSuccess(() -> Component.translatable("commands.setidletimeout.success", idleTimeout), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.setidletimeout.success.disabled"), true);
        }

        return idleTimeout;
    }
}
