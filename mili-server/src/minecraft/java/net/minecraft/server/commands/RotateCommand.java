package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;

public class RotateCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("rotate")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("target", EntityArgument.entity())
                        .then(
                            Commands.argument("rotation", RotationArgument.rotation())
                                .executes(
                                    commandContext -> rotate(
                                        commandContext.getSource(),
                                        EntityArgument.getEntity(commandContext, "target"),
                                        RotationArgument.getRotation(commandContext, "rotation")
                                    )
                                )
                        )
                        .then(
                            Commands.literal("facing")
                                .then(
                                    Commands.literal("entity")
                                        .then(
                                            Commands.argument("facingEntity", EntityArgument.entity())
                                                .executes(
                                                    context -> rotate(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "target"),
                                                        new LookAt.LookAtEntity(
                                                            EntityArgument.getEntity(context, "facingEntity"), EntityAnchorArgument.Anchor.FEET
                                                        )
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("facingAnchor", EntityAnchorArgument.anchor())
                                                        .executes(
                                                            context -> rotate(
                                                                context.getSource(),
                                                                EntityArgument.getEntity(context, "target"),
                                                                new LookAt.LookAtEntity(
                                                                    EntityArgument.getEntity(context, "facingEntity"),
                                                                    EntityAnchorArgument.getAnchor(context, "facingAnchor")
                                                                )
                                                            )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.argument("facingLocation", Vec3Argument.vec3())
                                        .executes(
                                            context -> rotate(
                                                context.getSource(),
                                                EntityArgument.getEntity(context, "target"),
                                                new LookAt.LookAtPosition(Vec3Argument.getVec3(context, "facingLocation"))
                                            )
                                        )
                                )
                        )
                )
        );
    }

    private static int rotate(CommandSourceStack source, Entity entity, Coordinates coordinates) {
        Vec2 rotation = coordinates.getRotation(source);
        // Folia start - region threading
        entity.getBukkitEntity().taskScheduler.schedule((Entity nmsEntity) -> {
            float f = coordinates.isYRelative() ? rotation.y - nmsEntity.getYRot() : rotation.y;
            float f1 = coordinates.isXRelative() ? rotation.x - nmsEntity.getXRot() : rotation.x;
            nmsEntity.forceSetRotation(f, coordinates.isYRelative(), f1, coordinates.isXRelative());
        }, null, 1L);
        // Folia end - region threading
        source.sendSuccess(() -> Component.translatable("commands.rotate.success", entity.getDisplayName()), true);
        return 1;
    }

    private static int rotate(CommandSourceStack source, Entity entity, LookAt lookAt) {
        // Folia start - region threading
        entity.getBukkitEntity().taskScheduler.schedule((Entity nmsEntity) -> {
            lookAt.perform(source, nmsEntity);
        }, null, 1L);
        // Folia end - region threading
        source.sendSuccess(() -> Component.translatable("commands.rotate.success", entity.getDisplayName()), true);
        return 1;
    }
}
