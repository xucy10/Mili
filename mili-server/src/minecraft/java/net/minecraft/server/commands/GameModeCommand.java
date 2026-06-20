package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;

public class GameModeCommand {
    public static final PermissionCheck PERMISSION_CHECK = new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("gamemode")
                .requires(Commands.hasPermission(PERMISSION_CHECK))
                .then(
                    Commands.argument("gamemode", GameModeArgument.gameMode())
                        .executes(
                            context -> setMode(
                                context, Collections.singleton(context.getSource().getPlayerOrException()), GameModeArgument.getGameMode(context, "gamemode")
                            )
                        )
                        .then(
                            Commands.argument("target", EntityArgument.players())
                                .executes(
                                    context -> setMode(context, EntityArgument.getPlayers(context, "target"), GameModeArgument.getGameMode(context, "gamemode"))
                                )
                        )
                )
        );
    }

    private static void logGamemodeChange(CommandSourceStack source, ServerPlayer player, GameType gameType) {
        Component component = Component.translatable("gameMode." + gameType.getName());
        if (source.getEntity() == player) {
            source.sendSuccess(() -> Component.translatable("commands.gamemode.success.self", component), true);
        } else {
            if (source.getLevel().getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK)) {
                player.sendSystemMessage(Component.translatable("gameMode.changed", component));
            }

            source.sendSuccess(() -> Component.translatable("commands.gamemode.success.other", player.getDisplayName(), component), true);
        }
    }

    private static int setMode(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players, GameType gameType) {
        int i = 0;

        for (ServerPlayer serverPlayer : players) {
            serverPlayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer nmsEntity) -> { // Folia - region threading
            if (setGameMode(context.getSource(), nmsEntity, gameType)) { // Folia - region threading
                //i++; // Folia - region threading
            }
            }, null, 1L); // Folia - region threading
        }

        return i;
    }

    public static void setGameMode(ServerPlayer player, GameType gameMode) {
        setGameMode(player.createCommandSourceStack(), player, gameMode);
    }

    private static boolean setGameMode(CommandSourceStack source, ServerPlayer player, GameType gameMode) {
        // Paper start - Expand PlayerGameModeChangeEvent
        return setGameMode(source, player, gameMode, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.COMMAND);
    }

    public static boolean setGameMode(CommandSourceStack source, ServerPlayer player, GameType gameMode, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause cause) {
        org.bukkit.event.player.PlayerGameModeChangeEvent event = player.setGameMode(gameMode, cause, null);
        if (event != null && !event.isCancelled()) {
            // Paper end - Expand PlayerGameModeChangeEvent
            logGamemodeChange(source, player, gameMode);
            return true;
            // Paper start - Expand PlayerGameModeChangeEvent
        } else if (event != null && event.cancelMessage() != null) {
            source.sendSuccess(() -> io.papermc.paper.adventure.PaperAdventure.asVanilla(event.cancelMessage()), true);
            return false;
            // Paper end - Expand PlayerGameModeChangeEvent
        } else {
            return false;
        }
    }
}
