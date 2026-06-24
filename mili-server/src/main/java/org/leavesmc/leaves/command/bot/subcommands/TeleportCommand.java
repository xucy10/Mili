/*
 * This file is part of Mili
 */

package org.leavesmc.leaves.command.bot.subcommands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.adventure.PaperAdventure;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.bot.BotList;
import org.leavesmc.leaves.bot.ServerBot;
import org.leavesmc.leaves.command.ArgumentNode;
import org.leavesmc.leaves.command.CommandContext;
import org.leavesmc.leaves.command.arguments.BotArgumentType;
import org.leavesmc.leaves.command.bot.BotSubcommand;

import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.spaces;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public class TeleportCommand extends BotSubcommand {

    public TeleportCommand() {
        super("tp");
        children(BotArgument::new);
    }

    private static class BotArgument extends ArgumentNode<ServerBot> {
        private BotArgument() {
            super("bot", BotArgumentType.bot());
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
            ServerBot bot = context.getArgument(BotArgument.class);
            CommandSender sender = context.getSender();

            if (!(sender instanceof Player player)) {
                sender.sendMessage(text("This command can only be executed by a player", RED));
                return true;
            }

            Location loc = bot.getBukkitEntity().getLocation();
            player.teleportAsync(loc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);

            sender.sendMessage(join(spaces(),
                    text("You have been teleported to bot", GRAY),
                    PaperAdventure.asAdventure(bot.getDisplayName()),
                    text("at", GRAY),
                    text(loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ(), AQUA)
            ));
            return true;
        }
    }
}
