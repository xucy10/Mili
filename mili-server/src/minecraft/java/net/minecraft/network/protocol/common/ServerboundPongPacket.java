package net.minecraft.network.protocol.common;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundPongPacket implements Packet<ServerCommonPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundPongPacket> STREAM_CODEC = Packet.codec(
        ServerboundPongPacket::write, ServerboundPongPacket::new
    );
    private final int id;

    public ServerboundPongPacket(int id) {
        this.id = id;
    }

    private ServerboundPongPacket(FriendlyByteBuf buffer) {
        this.id = buffer.readInt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeInt(this.id);
    }

    @Override
    public PacketType<ServerboundPongPacket> type() {
        return CommonPacketTypes.SERVERBOUND_PONG;
    }

    @Override
    public void handle(ServerCommonPacketListener handler) {
        handler.handlePong(this);
    }

    public int getId() {
        return this.id;
    }
}
