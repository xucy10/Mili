package net.minecraft.server.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.player.Player;

public class WardenSpawnTrackerCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("warden_spawn_tracker")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("clear")
                        .executes(
                            commandContext -> resetTracker(commandContext.getSource(), ImmutableList.of(commandContext.getSource().getPlayerOrException()))
                        )
                )
                .then(
                    Commands.literal("set")
                        .then(
                            Commands.argument("warning_level", IntegerArgumentType.integer(0, 4))
                                .executes(
                                    context -> setWarningLevel(
                                        context.getSource(),
                                        ImmutableList.of(context.getSource().getPlayerOrException()),
                                        IntegerArgumentType.getInteger(context, "warning_level")
                                    )
                                )
                        )
                )
        );
    }

    private static int setWarningLevel(CommandSourceStack source, Collection<? extends Player> targets, int warningLevel) {
        for (Player player : targets) {
            player.getWardenSpawnTracker().ifPresent(wardenSpawnTracker -> wardenSpawnTracker.setWarningLevel(warningLevel));
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable("commands.warden_spawn_tracker.set.success.single", targets.iterator().next().getDisplayName()), true
            );
        } else {
            source.sendSuccess(() -> Component.translatable("commands.warden_spawn_tracker.set.success.multiple", targets.size()), true);
        }

        return targets.size();
    }

    private static int resetTracker(CommandSourceStack source, Collection<? extends Player> targets) {
        for (Player player : targets) {
            player.getWardenSpawnTracker().ifPresent(WardenSpawnTracker::reset);
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable("commands.warden_spawn_tracker.clear.success.single", targets.iterator().next().getDisplayName()), true
            );
        } else {
            source.sendSuccess(() -> Component.translatable("commands.warden_spawn_tracker.clear.success.multiple", targets.size()), true);
        }

        return targets.size();
    }
}
