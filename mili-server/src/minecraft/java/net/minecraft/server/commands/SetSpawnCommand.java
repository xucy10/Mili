package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec2;

public class SetSpawnCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("spawnpoint")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(
                    context -> setSpawn(
                        context.getSource(),
                        Collections.singleton(context.getSource().getPlayerOrException()),
                        BlockPos.containing(context.getSource().getPosition()),
                        WorldCoordinates.ZERO_ROTATION
                    )
                )
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .executes(
                            context -> setSpawn(
                                context.getSource(),
                                EntityArgument.getPlayers(context, "targets"),
                                BlockPos.containing(context.getSource().getPosition()),
                                WorldCoordinates.ZERO_ROTATION
                            )
                        )
                        .then(
                            Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(
                                    context -> setSpawn(
                                        context.getSource(),
                                        EntityArgument.getPlayers(context, "targets"),
                                        BlockPosArgument.getSpawnablePos(context, "pos"),
                                        WorldCoordinates.ZERO_ROTATION
                                    )
                                )
                                .then(
                                    Commands.argument("rotation", RotationArgument.rotation())
                                        .executes(
                                            context -> setSpawn(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "targets"),
                                                BlockPosArgument.getSpawnablePos(context, "pos"),
                                                RotationArgument.getRotation(context, "rotation")
                                            )
                                        )
                                )
                        )
                )
        );
    }

    private static int setSpawn(CommandSourceStack source, Collection<ServerPlayer> targets, BlockPos pos, Coordinates rotationSupplier) {
        ResourceKey<Level> resourceKey = source.getLevel().dimension();
        Vec2 rotation = rotationSupplier.getRotation(source);
        float f = Mth.wrapDegrees(rotation.y);
        float f1 = Mth.clamp(rotation.x, -90.0F, 90.0F);

        final Collection<ServerPlayer> actualTargets = new java.util.ArrayList<>(); // Paper - Add PlayerSetSpawnEvent
        for (ServerPlayer serverPlayer : targets) {
            // Paper start - Add PlayerSetSpawnEvent
            // Folia start - region threading
            serverPlayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> {
                player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(resourceKey, pos, f, f1), true), false, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.COMMAND);
            }, null, 1L);
            if (true) { // Folia end - region threading
                actualTargets.add(serverPlayer);
            }
            // Paper end - Add PlayerSetSpawnEvent
        }
        // Paper start - Add PlayerSetSpawnEvent
        if (actualTargets.isEmpty()) {
            return 0;
        }
        // Paper end - Add PlayerSetSpawnEvent

        String string = resourceKey.identifier().toString();
        if (actualTargets.size() == 1) { // Paper - Add PlayerSetSpawnEvent
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.spawnpoint.success.single", pos.getX(), pos.getY(), pos.getZ(), f, f1, string, actualTargets.iterator().next().getDisplayName() // Paper - Add PlayerSetSpawnEvent
                ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.spawnpoint.success.multiple", pos.getX(), pos.getY(), pos.getZ(), f, f1, string, actualTargets.size()), true // Paper - Add PlayerSetSpawnEvent
            );
        }

        return actualTargets.size(); // Paper - Add PlayerSetSpawnEvent
    }
}
