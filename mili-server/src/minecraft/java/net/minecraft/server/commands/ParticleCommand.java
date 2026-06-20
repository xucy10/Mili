package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class ParticleCommand {
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.particle.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("particle")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("name", ParticleArgument.particle(context))
                        .executes(
                            commandContext -> sendParticles(
                                commandContext.getSource(),
                                ParticleArgument.getParticle(commandContext, "name"),
                                commandContext.getSource().getPosition(),
                                Vec3.ZERO,
                                0.0F,
                                0,
                                false,
                                commandContext.getSource().getServer().getPlayerList().getPlayers()
                            )
                        )
                        .then(
                            Commands.argument("pos", Vec3Argument.vec3())
                                .executes(
                                    context1 -> sendParticles(
                                        context1.getSource(),
                                        ParticleArgument.getParticle(context1, "name"),
                                        Vec3Argument.getVec3(context1, "pos"),
                                        Vec3.ZERO,
                                        0.0F,
                                        0,
                                        false,
                                        context1.getSource().getServer().getPlayerList().getPlayers()
                                    )
                                )
                                .then(
                                    Commands.argument("delta", Vec3Argument.vec3(false))
                                        .then(
                                            Commands.argument("speed", FloatArgumentType.floatArg(0.0F))
                                                .then(
                                                    Commands.argument("count", IntegerArgumentType.integer(0))
                                                        .executes(
                                                            context1 -> sendParticles(
                                                                context1.getSource(),
                                                                ParticleArgument.getParticle(context1, "name"),
                                                                Vec3Argument.getVec3(context1, "pos"),
                                                                Vec3Argument.getVec3(context1, "delta"),
                                                                FloatArgumentType.getFloat(context1, "speed"),
                                                                IntegerArgumentType.getInteger(context1, "count"),
                                                                false,
                                                                context1.getSource().getServer().getPlayerList().getPlayers()
                                                            )
                                                        )
                                                        .then(
                                                            Commands.literal("force")
                                                                .executes(
                                                                    context1 -> sendParticles(
                                                                        context1.getSource(),
                                                                        ParticleArgument.getParticle(context1, "name"),
                                                                        Vec3Argument.getVec3(context1, "pos"),
                                                                        Vec3Argument.getVec3(context1, "delta"),
                                                                        FloatArgumentType.getFloat(context1, "speed"),
                                                                        IntegerArgumentType.getInteger(context1, "count"),
                                                                        true,
                                                                        context1.getSource().getServer().getPlayerList().getPlayers()
                                                                    )
                                                                )
                                                                .then(
                                                                    Commands.argument("viewers", EntityArgument.players())
                                                                        .executes(
                                                                            context1 -> sendParticles(
                                                                                context1.getSource(),
                                                                                ParticleArgument.getParticle(context1, "name"),
                                                                                Vec3Argument.getVec3(context1, "pos"),
                                                                                Vec3Argument.getVec3(context1, "delta"),
                                                                                FloatArgumentType.getFloat(context1, "speed"),
                                                                                IntegerArgumentType.getInteger(context1, "count"),
                                                                                true,
                                                                                EntityArgument.getPlayers(context1, "viewers")
                                                                            )
                                                                        )
                                                                )
                                                        )
                                                        .then(
                                                            Commands.literal("normal")
                                                                .executes(
                                                                    context1 -> sendParticles(
                                                                        context1.getSource(),
                                                                        ParticleArgument.getParticle(context1, "name"),
                                                                        Vec3Argument.getVec3(context1, "pos"),
                                                                        Vec3Argument.getVec3(context1, "delta"),
                                                                        FloatArgumentType.getFloat(context1, "speed"),
                                                                        IntegerArgumentType.getInteger(context1, "count"),
                                                                        false,
                                                                        context1.getSource().getServer().getPlayerList().getPlayers()
                                                                    )
                                                                )
                                                                .then(
                                                                    Commands.argument("viewers", EntityArgument.players())
                                                                        .executes(
                                                                            context1 -> sendParticles(
                                                                                context1.getSource(),
                                                                                ParticleArgument.getParticle(context1, "name"),
                                                                                Vec3Argument.getVec3(context1, "pos"),
                                                                                Vec3Argument.getVec3(context1, "delta"),
                                                                                FloatArgumentType.getFloat(context1, "speed"),
                                                                                IntegerArgumentType.getInteger(context1, "count"),
                                                                                false,
                                                                                EntityArgument.getPlayers(context1, "viewers")
                                                                            )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int sendParticles(
        CommandSourceStack source, ParticleOptions options, Vec3 pos, Vec3 delta, float speed, int count, boolean force, Collection<ServerPlayer> viewers
    ) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverPlayer : viewers) {
            if (source.getLevel().sendParticles(serverPlayer, options, force, false, pos.x, pos.y, pos.z, count, delta.x, delta.y, delta.z, speed)) {
                i++;
            }
        }

        if (i == 0) {
            throw ERROR_FAILED.create();
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.particle.success", BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()).toString()), true
            );
            return i;
        }
    }
}
