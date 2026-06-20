package net.minecraft.network.protocol.handshake;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ClientIntentionPacket(int protocolVersion, String hostName, int port, ClientIntent intention) implements Packet<ServerHandshakePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientIntentionPacket> STREAM_CODEC = Packet.codec(
        ClientIntentionPacket::write, ClientIntentionPacket::new
    );
    private static final int MAX_HOST_LENGTH = 255;

    @Deprecated
    public ClientIntentionPacket(int protocolVersion, String hostName, int port, ClientIntent intention) {
        this.protocolVersion = protocolVersion;
        this.hostName = hostName;
        this.port = port;
        this.intention = intention;
    }

    private ClientIntentionPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUtf(Short.MAX_VALUE), buffer.readUnsignedShort(), ClientIntent.byId(buffer.readVarInt())); // Spigot - increase max hostName length
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.protocolVersion);
        buffer.writeUtf(this.hostName);
        buffer.writeShort(this.port);
        buffer.writeVarInt(this.intention.id());
    }

    @Override
    public PacketType<ClientIntentionPacket> type() {
        return HandshakePacketTypes.CLIENT_INTENTION;
    }

    @Override
    public void handle(ServerHandshakePacketListener handler) {
        handler.handleIntention(this);
    }

    @Override
    public boolean isTerminal() {
        return true;
    }
}
