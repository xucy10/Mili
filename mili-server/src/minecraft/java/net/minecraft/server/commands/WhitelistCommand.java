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
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.world.entity.player.Player;

public class WhitelistCommand {
    private static final SimpleCommandExceptionType ERROR_ALREADY_ENABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.whitelist.alreadyOn")
    );
    private static final SimpleCommandExceptionType ERROR_ALREADY_DISABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.whitelist.alreadyOff")
    );
    private static final SimpleCommandExceptionType ERROR_ALREADY_WHITELISTED = new SimpleCommandExceptionType(
        Component.translatable("commands.whitelist.add.failed")
    );
    private static final SimpleCommandExceptionType ERROR_NOT_WHITELISTED = new SimpleCommandExceptionType(
        Component.translatable("commands.whitelist.remove.failed")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("whitelist")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("on").executes(commandContext -> enableWhitelist(commandContext.getSource())))
                .then(Commands.literal("off").executes(context -> disableWhitelist(context.getSource())))
                .then(Commands.literal("list").executes(context -> showList(context.getSource())))
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("targets", GameProfileArgument.gameProfile())
                                .suggests(
                                    (commandContext, suggestionsBuilder) -> {
                                        PlayerList playerList = commandContext.getSource().getServer().getPlayerList();
                                        return SharedSuggestionProvider.suggest(
                                            playerList.getPlayers()
                                                .stream()
                                                .map(Player::nameAndId)
                                                .filter(nameAndId -> !playerList.getWhiteList().isWhiteListed(nameAndId))
                                                .map(NameAndId::name),
                                            suggestionsBuilder
                                        );
                                    }
                                )
                                .executes(
                                    commandContext -> addPlayers(commandContext.getSource(), GameProfileArgument.getGameProfiles(commandContext, "targets"))
                                )
                        )
                )
                .then(
                    Commands.literal("remove")
                        .then(
                            Commands.argument("targets", GameProfileArgument.gameProfile())
                                .suggests(
                                    (commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggest(
                                        commandContext.getSource().getServer().getPlayerList().getWhiteListNames(), suggestionsBuilder
                                    )
                                )
                                .executes(
                                    commandContext -> removePlayers(commandContext.getSource(), GameProfileArgument.getGameProfiles(commandContext, "targets"))
                                )
                        )
                )
                .then(Commands.literal("reload").executes(context -> reload(context.getSource())))
        );
    }

    private static int reload(CommandSourceStack source) {
        source.getServer().getPlayerList().reloadWhiteList();
        source.sendSuccess(() -> Component.translatable("commands.whitelist.reloaded"), true);
        source.getServer().kickUnlistedPlayers();
        return 1;
    }

    private static int addPlayers(CommandSourceStack source, Collection<NameAndId> players) throws CommandSyntaxException {
        UserWhiteList whiteList = source.getServer().getPlayerList().getWhiteList();
        int i = 0;

        for (NameAndId nameAndId : players) {
            if (!whiteList.isWhiteListed(nameAndId)) {
                UserWhiteListEntry userWhiteListEntry = new UserWhiteListEntry(nameAndId);
                whiteList.add(userWhiteListEntry);
                source.sendSuccess(() -> Component.translatable("commands.whitelist.add.success", Component.literal(nameAndId.name())), true);
                i++;
            }
        }

        if (i == 0) {
            throw ERROR_ALREADY_WHITELISTED.create();
        } else {
            return i;
        }
    }

    private static int removePlayers(CommandSourceStack source, Collection<NameAndId> players) throws CommandSyntaxException {
        UserWhiteList whiteList = source.getServer().getPlayerList().getWhiteList();
        int i = 0;

        for (NameAndId nameAndId : players) {
            if (whiteList.isWhiteListed(nameAndId)) {
                UserWhiteListEntry userWhiteListEntry = new UserWhiteListEntry(nameAndId);
                whiteList.remove(userWhiteListEntry);
                source.sendSuccess(() -> Component.translatable("commands.whitelist.remove.success", Component.literal(nameAndId.name())), true);
                i++;
            }
        }

        if (i == 0) {
            throw ERROR_NOT_WHITELISTED.create();
        } else {
            source.getServer().kickUnlistedPlayers();
            return i;
        }
    }

    private static int enableWhitelist(CommandSourceStack source) throws CommandSyntaxException {
        if (source.getServer().isUsingWhitelist()) {
            throw ERROR_ALREADY_ENABLED.create();
        } else {
            source.getServer().setUsingWhitelist(true);
            source.sendSuccess(() -> Component.translatable("commands.whitelist.enabled"), true);
            source.getServer().kickUnlistedPlayers();
            return 1;
        }
    }

    private static int disableWhitelist(CommandSourceStack source) throws CommandSyntaxException {
        if (!source.getServer().isUsingWhitelist()) {
            throw ERROR_ALREADY_DISABLED.create();
        } else {
            source.getServer().setUsingWhitelist(false);
            source.sendSuccess(() -> Component.translatable("commands.whitelist.disabled"), true);
            return 1;
        }
    }

    private static int showList(CommandSourceStack source) {
        String[] whiteListNames = source.getServer().getPlayerList().getWhiteListNames();
        if (whiteListNames.length == 0) {
            source.sendSuccess(() -> Component.translatable("commands.whitelist.none"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.whitelist.list", whiteListNames.length, String.join(", ", whiteListNames)), false);
        }

        return whiteListNames.length;
    }
}
