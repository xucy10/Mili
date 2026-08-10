package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.ChatType;
import net.minecraft.server.players.PlayerList;

public class SayCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("say")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("message", MessageArgument.message()).executes(commandContext -> {
                    MessageArgument.resolveChatMessage(commandContext, "message", playerChatMessage -> {
                        CommandSourceStack commandSourceStack = commandContext.getSource();
                        PlayerList playerList = commandSourceStack.getServer().getPlayerList();
                        playerList.broadcastChatMessage(playerChatMessage, commandSourceStack, ChatType.bind(ChatType.SAY_COMMAND, commandSourceStack));
                    });
                    return 1;
                }))
        );
    }
}
