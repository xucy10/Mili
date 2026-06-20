package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec2;

public class SetWorldSpawnCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("setworldspawn")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> setSpawn(context.getSource(), BlockPos.containing(context.getSource().getPosition()), WorldCoordinates.ZERO_ROTATION))
                .then(
                    Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> setSpawn(context.getSource(), BlockPosArgument.getSpawnablePos(context, "pos"), WorldCoordinates.ZERO_ROTATION))
                        .then(
                            Commands.argument("rotation", RotationArgument.rotation())
                                .executes(
                                    context -> setSpawn(
                                        context.getSource(),
                                        BlockPosArgument.getSpawnablePos(context, "pos"),
                                        RotationArgument.getRotation(context, "rotation")
                                    )
                                )
                        )
                )
        );
    }

    private static int setSpawn(CommandSourceStack source, BlockPos pos, Coordinates rotationSupplier) {
        ServerLevel level = source.getLevel();
        Vec2 rotation = rotationSupplier.getRotation(source);
        float f = rotation.y;
        float f1 = rotation.x;
        LevelData.RespawnData respawnData = LevelData.RespawnData.of(level.dimension(), pos, f, f1);
        level.setRespawnData(respawnData);
        source.sendSuccess(
            () -> Component.translatable(
                "commands.setworldspawn.success",
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                respawnData.yaw(),
                respawnData.pitch(),
                level.dimension().identifier().toString()
            ),
            true
        );
        return 1;
    }
}
