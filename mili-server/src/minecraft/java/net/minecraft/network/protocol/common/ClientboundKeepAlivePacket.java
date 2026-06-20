package net.minecraft.network.protocol.common;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundKeepAlivePacket implements Packet<ClientCommonPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundKeepAlivePacket> STREAM_CODEC = Packet.codec(
        ClientboundKeepAlivePacket::write, ClientboundKeepAlivePacket::new
    );
    private final long id;

    public ClientboundKeepAlivePacket(long id) {
        this.id = id;
    }

    private ClientboundKeepAlivePacket(FriendlyByteBuf buffer) {
        this.id = buffer.readLong();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeLong(this.id);
    }

    @Override
    public PacketType<ClientboundKeepAlivePacket> type() {
        return CommonPacketTypes.CLIENTBOUND_KEEP_ALIVE;
    }

    @Override
    public void handle(ClientCommonPacketListener handler) {
        handler.handleKeepAlive(this);
    }

    public long getId() {
        return this.id;
    }
}
