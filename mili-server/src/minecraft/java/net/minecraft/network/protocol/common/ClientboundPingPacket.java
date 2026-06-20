package net.minecraft.network.protocol.common;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundPingPacket implements Packet<ClientCommonPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundPingPacket> STREAM_CODEC = Packet.codec(
        ClientboundPingPacket::write, ClientboundPingPacket::new
    );
    private final int id;

    public ClientboundPingPacket(int id) {
        this.id = id;
    }

    private ClientboundPingPacket(FriendlyByteBuf buffer) {
        this.id = buffer.readInt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeInt(this.id);
    }

    @Override
    public PacketType<ClientboundPingPacket> type() {
        return CommonPacketTypes.CLIENTBOUND_PING;
    }

    @Override
    public void handle(ClientCommonPacketListener handler) {
        handler.handlePing(this);
    }

    public int getId() {
        return this.id;
    }
}
