package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;

public class SeedCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean isDedicatedServer) {
        dispatcher.register(
            Commands.literal("seed")
                .requires(Commands.hasPermission(isDedicatedServer ? Commands.LEVEL_GAMEMASTERS : Commands.LEVEL_ALL))
                .executes(commandContext -> {
                    long seed = commandContext.getSource().getLevel().getSeed();
                    Component component = ComponentUtils.copyOnClickText(String.valueOf(seed));
                    commandContext.getSource().sendSuccess(() -> Component.translatable("commands.seed.success", component), false);
                    // Leaf start - Matter - SecureSeed Command
                    if (me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled) {
                        su.plo.matter.Globals.setupGlobals(commandContext.getSource().getLevel());
                        String seedStr = su.plo.matter.Globals.seedToString(su.plo.matter.Globals.worldSeed);
                        Component featureSeedComponent = ComponentUtils.copyOnClickText(seedStr);

                        commandContext.getSource().sendSuccess(() -> Component.translatable(("Feature seed: %s"), featureSeedComponent), false);
                    }
                    // Leaf end - Matter - SecureSeed Command
                    return (int)seed;
                })
        );
    }
}
