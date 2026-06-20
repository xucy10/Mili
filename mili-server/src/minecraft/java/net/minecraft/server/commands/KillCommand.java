package net.minecraft.server.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public class KillCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("kill")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(commandContext -> kill(commandContext.getSource(), ImmutableList.of(commandContext.getSource().getEntityOrException())))
                .then(
                    Commands.argument("targets", EntityArgument.entities())
                        .executes(context -> kill(context.getSource(), EntityArgument.getEntities(context, "targets")))
                )
        );
    }

    private static int kill(CommandSourceStack source, Collection<? extends Entity> targets) {
        for (Entity entity : targets) {
            entity.getBukkitEntity().taskScheduler.schedule((Entity nmsEntity) -> { // Folia - region threading
                nmsEntity.kill((net.minecraft.server.level.ServerLevel)nmsEntity.level()); // Folia - region threading
            }, null, 1L); // Folia - region threading
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.kill.success.single", targets.iterator().next().getDisplayName()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.kill.success.multiple", targets.size()), true);
        }

        return targets.size();
    }
}
