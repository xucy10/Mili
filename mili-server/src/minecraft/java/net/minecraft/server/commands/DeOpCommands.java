package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;

public class DeOpCommands {
    private static final SimpleCommandExceptionType ERROR_NOT_OP = new SimpleCommandExceptionType(Component.translatable("commands.deop.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("deop")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(
                    Commands.argument("targets", GameProfileArgument.gameProfile())
                        .suggests(
                            (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(
                                commandContext.getSource().getServer().getPlayerList().getOpNames(), suggestionsBuilder
                            )
                        )
                        .executes(commandContext -> deopPlayers(commandContext.getSource(), GameProfileArgument.getGameProfiles(commandContext, "targets")))
                )
        );
    }

    private static int deopPlayers(CommandSourceStack source, Collection<NameAndId> players) throws CommandSyntaxException {
        PlayerList playerList = source.getServer().getPlayerList();
        int i = 0;

        for (NameAndId nameAndId : players) {
            if (playerList.isOp(nameAndId)) {
                playerList.deop(nameAndId);
                i++;
                source.sendSuccess(() -> Component.translatable("commands.deop.success", nameAndId.name()), true); // Paper - fixes MC-253721
            }
        }

        if (i == 0) {
            throw ERROR_NOT_OP.create();
        } else {
            source.getServer().kickUnlistedPlayers();
            return i;
        }
    }
}
