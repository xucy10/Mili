package net.minecraft.network.chat;

import net.minecraft.server.level.ServerPlayer;

public interface OutgoingChatMessage {
    Component content();

    void sendToPlayer(ServerPlayer player, boolean filtered, ChatType.Bound boundChatType);

    // Paper start
    default void sendToPlayer(ServerPlayer player, boolean filtered, ChatType.Bound boundChatType, @javax.annotation.Nullable Component unsigned) {
        this.sendToPlayer(player, filtered, boundChatType);
    }
    // Paper end

    static OutgoingChatMessage create(PlayerChatMessage message) {
        return (OutgoingChatMessage)(message.isSystem()
            ? new OutgoingChatMessage.Disguised(message.decoratedContent())
            : new OutgoingChatMessage.Player(message));
    }

    public record Disguised(@Override Component content) implements OutgoingChatMessage {
        @Override
        public void sendToPlayer(ServerPlayer player, boolean filtered, ChatType.Bound boundChatType) {
            // Paper start
            this.sendToPlayer(player, filtered, boundChatType, null);
        }
        @Override
        public void sendToPlayer(ServerPlayer player, boolean filtered, ChatType.Bound boundChatType, @javax.annotation.Nullable Component unsigned) {
            player.connection.sendDisguisedChatMessage(unsigned != null ? unsigned : this.content, boundChatType);
            // Paper end
        }
    }

    public record Player(PlayerChatMessage message) implements OutgoingChatMessage {
        @Override
        public Component content() {
            return this.message.decoratedContent();
        }

        @Override
        public void sendToPlayer(ServerPlayer player, boolean filtered, ChatType.Bound boundChatType) {
            // Paper start
            this.sendToPlayer(player, filtered, boundChatType, null);
        }
        @Override
        public void sendToPlayer(ServerPlayer player, boolean filtered, ChatType.Bound boundChatType, @javax.annotation.Nullable Component unsigned) {
            // Paper end
            PlayerChatMessage playerChatMessage = this.message.filter(filtered);
            playerChatMessage = unsigned != null ? playerChatMessage.withUnsignedContent(unsigned) : playerChatMessage; // Paper
            if (!playerChatMessage.isFullyFiltered()) {
                player.connection.sendPlayerChatMessage(playerChatMessage, boundChatType);
            }
        }
    }
}
