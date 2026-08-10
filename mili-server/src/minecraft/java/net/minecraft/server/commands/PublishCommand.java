package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

public class PublishCommand {
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.publish.failed"));
    private static final DynamicCommandExceptionType ERROR_ALREADY_PUBLISHED = new DynamicCommandExceptionType(
        port -> Component.translatableEscape("commands.publish.alreadyPublished", port)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("publish")
                .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                .executes(commandContext -> publish(commandContext.getSource(), HttpUtil.getAvailablePort(), false, null))
                .then(
                    Commands.argument("allowCommands", BoolArgumentType.bool())
                        .executes(
                            context -> publish(context.getSource(), HttpUtil.getAvailablePort(), BoolArgumentType.getBool(context, "allowCommands"), null)
                        )
                        .then(
                            Commands.argument("gamemode", GameModeArgument.gameMode())
                                .executes(
                                    context -> publish(
                                        context.getSource(),
                                        HttpUtil.getAvailablePort(),
                                        BoolArgumentType.getBool(context, "allowCommands"),
                                        GameModeArgument.getGameMode(context, "gamemode")
                                    )
                                )
                                .then(
                                    Commands.argument("port", IntegerArgumentType.integer(0, 65535))
                                        .executes(
                                            context -> publish(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "port"),
                                                BoolArgumentType.getBool(context, "allowCommands"),
                                                GameModeArgument.getGameMode(context, "gamemode")
                                            )
                                        )
                                )
                        )
                )
        );
    }

    private static int publish(CommandSourceStack source, int port, boolean cheats, @Nullable GameType gameMode) throws CommandSyntaxException {
        if (source.getServer().isPublished()) {
            throw ERROR_ALREADY_PUBLISHED.create(source.getServer().getPort());
        } else if (!source.getServer().publishServer(gameMode, cheats, port)) {
            throw ERROR_FAILED.create();
        } else {
            source.sendSuccess(() -> getSuccessMessage(port), true);
            return port;
        }
    }

    public static MutableComponent getSuccessMessage(int port) {
        Component component = ComponentUtils.copyOnClickText(String.valueOf(port));
        return Component.translatable("commands.publish.started", component);
    }
}
