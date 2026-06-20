package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

public class StopSoundCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        RequiredArgumentBuilder<CommandSourceStack, EntitySelector> requiredArgumentBuilder = Commands.argument("targets", EntityArgument.players())
            .executes(context -> stopSound(context.getSource(), EntityArgument.getPlayers(context, "targets"), null, null))
            .then(
                Commands.literal("*")
                    .then(
                        Commands.argument("sound", IdentifierArgument.id())
                            .suggests(SuggestionProviders.cast(SuggestionProviders.AVAILABLE_SOUNDS))
                            .executes(
                                context -> stopSound(
                                    context.getSource(), EntityArgument.getPlayers(context, "targets"), null, IdentifierArgument.getId(context, "sound")
                                )
                            )
                    )
            );

        for (SoundSource soundSource : SoundSource.values()) {
            requiredArgumentBuilder.then(
                Commands.literal(soundSource.getName())
                    .executes(context -> stopSound(context.getSource(), EntityArgument.getPlayers(context, "targets"), soundSource, null))
                    .then(
                        Commands.argument("sound", IdentifierArgument.id())
                            .suggests(SuggestionProviders.cast(SuggestionProviders.AVAILABLE_SOUNDS))
                            .executes(
                                context -> stopSound(
                                    context.getSource(), EntityArgument.getPlayers(context, "targets"), soundSource, IdentifierArgument.getId(context, "sound")
                                )
                            )
                    )
            );
        }

        dispatcher.register(Commands.literal("stopsound").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(requiredArgumentBuilder));
    }

    private static int stopSound(CommandSourceStack source, Collection<ServerPlayer> targets, @Nullable SoundSource soundSource, @Nullable Identifier sound) {
        ClientboundStopSoundPacket clientboundStopSoundPacket = new ClientboundStopSoundPacket(sound, soundSource);

        for (ServerPlayer serverPlayer : targets) {
            serverPlayer.connection.send(clientboundStopSoundPacket);
        }

        if (soundSource != null) {
            if (sound != null) {
                source.sendSuccess(
                    () -> Component.translatable("commands.stopsound.success.source.sound", Component.translationArg(sound), soundSource.getName()), true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.stopsound.success.source.any", soundSource.getName()), true);
            }
        } else if (sound != null) {
            source.sendSuccess(() -> Component.translatable("commands.stopsound.success.sourceless.sound", Component.translationArg(sound)), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.stopsound.success.sourceless.any"), true);
        }

        return targets.size();
    }
}
