package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public class SpectateCommand {
    private static final SimpleCommandExceptionType ERROR_SELF = new SimpleCommandExceptionType(Component.translatable("commands.spectate.self"));
    private static final DynamicCommandExceptionType ERROR_NOT_SPECTATOR = new DynamicCommandExceptionType(
        player -> Component.translatableEscape("commands.spectate.not_spectator", player)
    );
    private static final DynamicCommandExceptionType ERROR_CANNOT_SPECTATE = new DynamicCommandExceptionType(
        player -> Component.translatableEscape("commands.spectate.cannot_spectate", player)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("spectate")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> spectate(context.getSource(), null, context.getSource().getPlayerOrException()))
                .then(
                    Commands.argument("target", EntityArgument.entity())
                        .executes(
                            context -> spectate(context.getSource(), EntityArgument.getEntity(context, "target"), context.getSource().getPlayerOrException())
                        )
                        .then(
                            Commands.argument("player", EntityArgument.player())
                                .executes(
                                    context -> spectate(
                                        context.getSource(), EntityArgument.getEntity(context, "target"), EntityArgument.getPlayer(context, "player")
                                    )
                                )
                        )
                )
        );
    }

    private static int spectate(CommandSourceStack source, @Nullable Entity target, ServerPlayer player) throws CommandSyntaxException {
        if (player == target) {
            throw ERROR_SELF.create();
        } else if (!player.isSpectator()) {
            throw ERROR_NOT_SPECTATOR.create(player.getDisplayName());
        } else if (target != null && target.getType().clientTrackingRange() == 0) {
            throw ERROR_CANNOT_SPECTATE.create(target.getDisplayName());
        } else {
            player.setCamera(target);
            if (target != null) {
                source.sendSuccess(() -> Component.translatable("commands.spectate.success.started", target.getDisplayName()), false);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.spectate.success.stopped"), false);
            }

            return 1;
        }
    }
}
