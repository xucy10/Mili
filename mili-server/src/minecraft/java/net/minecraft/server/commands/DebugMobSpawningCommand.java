package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;

public class DebugMobSpawningCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("debugmobspawning")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        for (MobCategory mobCategory : MobCategory.values()) {
            literalArgumentBuilder.then(
                Commands.literal(mobCategory.getName())
                    .then(
                        Commands.argument("at", BlockPosArgument.blockPos())
                            .executes(
                                commandContext -> spawnMobs(commandContext.getSource(), mobCategory, BlockPosArgument.getLoadedBlockPos(commandContext, "at"))
                            )
                    )
            );
        }

        dispatcher.register(literalArgumentBuilder);
    }

    private static int spawnMobs(CommandSourceStack source, MobCategory mobCategory, BlockPos pos) {
        NaturalSpawner.spawnCategoryForPosition(mobCategory, source.getLevel(), pos);
        return 1;
    }
}
