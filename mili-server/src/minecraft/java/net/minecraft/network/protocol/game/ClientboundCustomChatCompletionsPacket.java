package net.minecraft.network.protocol.game;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ClientboundCustomChatCompletionsPacket(ClientboundCustomChatCompletionsPacket.Action action, List<String> entries)
    implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundCustomChatCompletionsPacket> STREAM_CODEC = Packet.codec(
        ClientboundCustomChatCompletionsPacket::write, ClientboundCustomChatCompletionsPacket::new
    );

    private ClientboundCustomChatCompletionsPacket(FriendlyByteBuf buffer) {
        this(buffer.readEnum(ClientboundCustomChatCompletionsPacket.Action.class), buffer.readList(FriendlyByteBuf::readUtf));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeCollection(this.entries, FriendlyByteBuf::writeUtf);
    }

    @Override
    public PacketType<ClientboundCustomChatCompletionsPacket> type() {
        return GamePacketTypes.CLIENTBOUND_CUSTOM_CHAT_COMPLETIONS;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleCustomChatCompletions(this);
    }

    public static enum Action {
        ADD,
        REMOVE,
        SET;
    }
}
