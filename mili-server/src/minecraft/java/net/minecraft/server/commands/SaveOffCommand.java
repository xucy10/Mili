package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class SaveOffCommand {
    private static final SimpleCommandExceptionType ERROR_ALREADY_OFF = new SimpleCommandExceptionType(Component.translatable("commands.save.alreadyOff"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("save-off").requires(Commands.hasPermission(Commands.LEVEL_OWNERS)).executes(commandContext -> {
            CommandSourceStack commandSourceStack = commandContext.getSource();
            boolean flag = commandSourceStack.getServer().setAutoSave(false);
            if (!flag) {
                throw ERROR_ALREADY_OFF.create();
            } else {
                commandSourceStack.sendSuccess(() -> Component.translatable("commands.save.disabled"), true);
                return 1;
            }
        }));
    }
}
