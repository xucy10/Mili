package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundAcceptTeleportationPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundAcceptTeleportationPacket> STREAM_CODEC = Packet.codec(
        ServerboundAcceptTeleportationPacket::write, ServerboundAcceptTeleportationPacket::new
    );
    private final int id;

    public ServerboundAcceptTeleportationPacket(int id) {
        this.id = id;
    }

    private ServerboundAcceptTeleportationPacket(FriendlyByteBuf buffer) {
        this.id = buffer.readVarInt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.id);
    }

    @Override
    public PacketType<ServerboundAcceptTeleportationPacket> type() {
        return GamePacketTypes.SERVERBOUND_ACCEPT_TELEPORTATION;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleAcceptTeleportPacket(this);
    }

    public int getId() {
        return this.id;
    }
}
