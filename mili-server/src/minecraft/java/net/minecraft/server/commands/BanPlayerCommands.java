package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import org.jspecify.annotations.Nullable;

public class BanPlayerCommands {
    private static final SimpleCommandExceptionType ERROR_ALREADY_BANNED = new SimpleCommandExceptionType(Component.translatable("commands.ban.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ban")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(
                    Commands.argument("targets", GameProfileArgument.gameProfile())
                        .executes(
                            commandContext -> banPlayers(commandContext.getSource(), GameProfileArgument.getGameProfiles(commandContext, "targets"), null)
                        )
                        .then(
                            Commands.argument("reason", MessageArgument.message())
                                .executes(
                                    context -> banPlayers(
                                        context.getSource(),
                                        GameProfileArgument.getGameProfiles(context, "targets"),
                                        MessageArgument.getMessage(context, "reason")
                                    )
                                )
                        )
                )
        );
    }

    private static int banPlayers(CommandSourceStack source, Collection<NameAndId> players, @Nullable Component reason) throws CommandSyntaxException {
        UserBanList bans = source.getServer().getPlayerList().getBans();
        int i = 0;

        for (NameAndId nameAndId : players) {
            if (!bans.isBanned(nameAndId)) {
                UserBanListEntry userBanListEntry = new UserBanListEntry(
                    nameAndId, null, source.getTextName(), null, reason == null ? null : reason.getString()
                );
                bans.add(userBanListEntry);
                i++;
                source.sendSuccess(
                    () -> Component.translatable("commands.ban.success", Component.literal(nameAndId.name()), userBanListEntry.getReasonMessage()), true
                );
                ServerPlayer player = source.getServer().getPlayerList().getPlayer(nameAndId.id());
                if (player != null) {
                    player.connection.disconnect(Component.translatable("multiplayer.disconnect.banned"), org.bukkit.event.player.PlayerKickEvent.Cause.BANNED); // Paper - kick event cause
                }
            }
        }

        if (i == 0) {
            throw ERROR_ALREADY_BANNED.create();
        } else {
            return i;
        }
    }
}
