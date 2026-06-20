package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.util.List;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;

public class ListPlayersCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("list")
                .executes(context -> listPlayers(context.getSource()))
                .then(Commands.literal("uuids").executes(context -> listPlayersWithUuids(context.getSource())))
        );
    }

    private static int listPlayers(CommandSourceStack source) {
        return format(source, Player::getDisplayName);
    }

    private static int listPlayersWithUuids(CommandSourceStack source) {
        return format(
            source, player -> Component.translatable("commands.list.nameAndId", player.getName(), Component.translationArg(player.getGameProfile().id()))
        );
    }

    private static int format(CommandSourceStack source, Function<ServerPlayer, Component> nameExtractor) {
        PlayerList playerList = source.getServer().getPlayerList();
        // CraftBukkit start
        List<ServerPlayer> playersTemp = playerList.getPlayers();
        if (source.getBukkitSender() instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player sender = (org.bukkit.entity.Player) source.getBukkitSender();
            playersTemp = playersTemp.stream().filter((ep) -> sender.canSee(ep.getBukkitEntity())).collect(java.util.stream.Collectors.toList());
        }
        final List<ServerPlayer> players = playersTemp;
        // CraftBukkit end
        Component component = ComponentUtils.formatList(players, nameExtractor);
        source.sendSuccess(() -> Component.translatable("commands.list.players", players.size(), playerList.getMaxPlayers(), component), false);
        return players.size();
    }
}
