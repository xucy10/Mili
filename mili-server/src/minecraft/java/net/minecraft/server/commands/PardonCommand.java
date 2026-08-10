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
import net.minecraft.server.players.UserBanList;

public class PardonCommand {
    private static final SimpleCommandExceptionType ERROR_NOT_BANNED = new SimpleCommandExceptionType(Component.translatable("commands.pardon.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("pardon")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(
                    Commands.argument("targets", GameProfileArgument.gameProfile())
                        .suggests(
                            (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(
                                commandContext.getSource().getServer().getPlayerList().getBans().getUserList(), suggestionsBuilder
                            )
                        )
                        .executes(commandContext -> pardonPlayers(commandContext.getSource(), GameProfileArgument.getGameProfiles(commandContext, "targets")))
                )
        );
    }

    private static int pardonPlayers(CommandSourceStack source, Collection<NameAndId> gameProfiles) throws CommandSyntaxException {
        UserBanList bans = source.getServer().getPlayerList().getBans();
        int i = 0;

        for (NameAndId nameAndId : gameProfiles) {
            if (bans.isBanned(nameAndId)) {
                bans.remove(nameAndId);
                i++;
                source.sendSuccess(() -> Component.translatable("commands.pardon.success", Component.literal(nameAndId.name())), true);
            }
        }

        if (i == 0) {
            throw ERROR_NOT_BANNED.create();
        } else {
            return i;
        }
    }
}
