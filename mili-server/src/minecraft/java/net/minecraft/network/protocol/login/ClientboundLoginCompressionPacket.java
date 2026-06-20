package net.minecraft.network.protocol.login;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundLoginCompressionPacket implements Packet<ClientLoginPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundLoginCompressionPacket> STREAM_CODEC = Packet.codec(
        ClientboundLoginCompressionPacket::write, ClientboundLoginCompressionPacket::new
    );
    private final int compressionThreshold;

    public ClientboundLoginCompressionPacket(int compressionThreshold) {
        this.compressionThreshold = compressionThreshold;
    }

    private ClientboundLoginCompressionPacket(FriendlyByteBuf buffer) {
        this.compressionThreshold = buffer.readVarInt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.compressionThreshold);
    }

    @Override
    public PacketType<ClientboundLoginCompressionPacket> type() {
        return LoginPacketTypes.CLIENTBOUND_LOGIN_COMPRESSION;
    }

    @Override
    public void handle(ClientLoginPacketListener handler) {
        handler.handleCompression(this);
    }

    public int getCompressionThreshold() {
        return this.compressionThreshold;
    }
}
