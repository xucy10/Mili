package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;

public class DifficultyCommand {
    private static final DynamicCommandExceptionType ERROR_ALREADY_DIFFICULT = new DynamicCommandExceptionType(
        difficulty -> Component.translatableEscape("commands.difficulty.failure", difficulty)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("difficulty");

        for (Difficulty difficulty : Difficulty.values()) {
            literalArgumentBuilder.then(Commands.literal(difficulty.getKey()).executes(context -> setDifficulty(context.getSource(), difficulty)));
        }

        dispatcher.register(literalArgumentBuilder.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(commandContext -> {
            Difficulty difficulty1 = commandContext.getSource().getLevel().getDifficulty();
            commandContext.getSource().sendSuccess(() -> Component.translatable("commands.difficulty.query", difficulty1.getDisplayName()), false);
            return difficulty1.getId();
        }));
    }

    public static int setDifficulty(CommandSourceStack source, Difficulty difficulty) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        if (source.getLevel().getDifficulty() == difficulty) { // CraftBukkit
            throw ERROR_ALREADY_DIFFICULT.create(difficulty.getKey());
        } else {
            server.setDifficulty(source.getLevel(), difficulty, source, true); // Paper - per level difficulty; don't skip other difficulty-changing logic (fix upstream's fix); WorldDifficultyChangeEvent
            source.sendSuccess(() -> Component.translatable("commands.difficulty.success", difficulty.getDisplayName()), true);
            return 0;
        }
    }
}
