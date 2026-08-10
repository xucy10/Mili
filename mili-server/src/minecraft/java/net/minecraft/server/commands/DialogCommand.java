package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;

public class DialogCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(
            Commands.literal("dialog")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("show")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .then(
                                    Commands.argument("dialog", ResourceOrIdArgument.dialog(buildContext))
                                        .executes(
                                            commandContext -> showDialog(
                                                (CommandSourceStack)commandContext.getSource(),
                                                EntityArgument.getPlayers(commandContext, "targets"),
                                                ResourceOrIdArgument.getDialog(commandContext, "dialog")
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("clear")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .executes(commandContext -> clearDialog(commandContext.getSource(), EntityArgument.getPlayers(commandContext, "targets")))
                        )
                )
        );
    }

    private static int showDialog(CommandSourceStack source, Collection<ServerPlayer> targets, Holder<Dialog> dialog) {
        for (ServerPlayer serverPlayer : targets) {
            serverPlayer.openDialog(dialog);
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.dialog.show.single", targets.iterator().next().getDisplayName()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.dialog.show.multiple", targets.size()), true);
        }

        return targets.size();
    }

    private static int clearDialog(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer serverPlayer : targets) {
            serverPlayer.connection.send(ClientboundClearDialogPacket.INSTANCE);
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.dialog.clear.single", targets.iterator().next().getDisplayName()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.dialog.clear.multiple", targets.size()), true);
        }

        return targets.size();
    }
}
