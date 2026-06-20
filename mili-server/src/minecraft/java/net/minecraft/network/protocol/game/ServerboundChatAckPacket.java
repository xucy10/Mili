package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundChatAckPacket(int offset) implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundChatAckPacket> STREAM_CODEC = Packet.codec(
        ServerboundChatAckPacket::write, ServerboundChatAckPacket::new
    );

    private ServerboundChatAckPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.offset);
    }

    @Override
    public PacketType<ServerboundChatAckPacket> type() {
        return GamePacketTypes.SERVERBOUND_CHAT_ACK;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleChatAck(this);
    }
}
