package net.minecraft.server.commands;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.BanListEntry;
import net.minecraft.server.players.PlayerList;

public class BanListCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("banlist")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .executes(
                    context -> {
                        PlayerList playerList = context.getSource().getServer().getPlayerList();
                        return showList(
                            context.getSource(), Lists.newArrayList(Iterables.concat(playerList.getBans().getEntries(), playerList.getIpBans().getEntries()))
                        );
                    }
                )
                .then(
                    Commands.literal("ips")
                        .executes(context -> showList(context.getSource(), context.getSource().getServer().getPlayerList().getIpBans().getEntries()))
                )
                .then(
                    Commands.literal("players")
                        .executes(context -> showList(context.getSource(), context.getSource().getServer().getPlayerList().getBans().getEntries()))
                )
        );
    }

    private static int showList(CommandSourceStack source, Collection<? extends BanListEntry<?>> bannedPlayerList) {
        if (bannedPlayerList.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.banlist.none"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.banlist.list", bannedPlayerList.size()), false);

            for (BanListEntry<?> banListEntry : bannedPlayerList) {
                source.sendSuccess(
                    () -> Component.translatable(
                        "commands.banlist.entry", banListEntry.getDisplayName(), banListEntry.getSource(), banListEntry.getReasonMessage()
                    ),
                    false
                );
            }
        }

        return bannedPlayerList.size();
    }
}
