package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;

public class TeamMsgCommand {
    private static final Style SUGGEST_STYLE = Style.EMPTY
        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.type.team.hover")))
        .withClickEvent(new ClickEvent.SuggestCommand("/teammsg "));
    private static final SimpleCommandExceptionType ERROR_NOT_ON_TEAM = new SimpleCommandExceptionType(Component.translatable("commands.teammsg.failed.noteam"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> literalCommandNode = dispatcher.register(
            Commands.literal("teammsg")
                .then(
                    Commands.argument("message", MessageArgument.message())
                        .executes(
                            context -> {
                                CommandSourceStack commandSourceStack = context.getSource();
                                Entity entityOrException = commandSourceStack.getEntityOrException();
                                PlayerTeam team = entityOrException.getTeam();
                                if (team == null) {
                                    throw ERROR_NOT_ON_TEAM.create();
                                } else {
                                    List<ServerPlayer> list = commandSourceStack.getServer()
                                        .getPlayerList()
                                        .getPlayers()
                                        .stream()
                                        .filter(player -> player == entityOrException || player.getTeam() == team)
                                        .toList();
                                    if (!list.isEmpty()) {
                                        MessageArgument.resolveChatMessage(
                                            context, "message", message -> sendMessage(commandSourceStack, entityOrException, team, list, message)
                                        );
                                    }

                                    return list.size();
                                }
                            }
                        )
                )
        );
        dispatcher.register(Commands.literal("tm").redirect(literalCommandNode));
    }

    private static void sendMessage(CommandSourceStack source, Entity sender, PlayerTeam team, List<ServerPlayer> teamMembers, PlayerChatMessage message) {
        Component component = team.getFormattedDisplayName().withStyle(SUGGEST_STYLE);
        ChatType.Bound bound = ChatType.bind(ChatType.TEAM_MSG_COMMAND_INCOMING, source).withTargetName(component);
        ChatType.Bound bound1 = ChatType.bind(ChatType.TEAM_MSG_COMMAND_OUTGOING, source).withTargetName(component);
        OutgoingChatMessage outgoingChatMessage = OutgoingChatMessage.create(message);
        boolean flag = false;

        for (ServerPlayer serverPlayer : teamMembers) {
            ChatType.Bound bound2 = serverPlayer == sender ? bound1 : bound;
            boolean shouldFilterMessageTo = source.shouldFilterMessageTo(serverPlayer);
            serverPlayer.sendChatMessage(outgoingChatMessage, shouldFilterMessageTo, bound2);
            flag |= shouldFilterMessageTo && message.isFullyFiltered();
        }

        if (flag) {
            source.sendSystemMessage(PlayerList.CHAT_FILTERED_FULL);
        }
    }
}
