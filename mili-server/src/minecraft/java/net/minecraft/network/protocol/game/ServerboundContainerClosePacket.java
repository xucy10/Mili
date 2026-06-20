package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundContainerClosePacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundContainerClosePacket> STREAM_CODEC = Packet.codec(
        ServerboundContainerClosePacket::write, ServerboundContainerClosePacket::new
    );
    private final int containerId;

    public ServerboundContainerClosePacket(int containerId) {
        this.containerId = containerId;
    }

    private ServerboundContainerClosePacket(FriendlyByteBuf buffer) {
        this.containerId = buffer.readContainerId();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeContainerId(this.containerId);
    }

    @Override
    public PacketType<ServerboundContainerClosePacket> type() {
        return GamePacketTypes.SERVERBOUND_CONTAINER_CLOSE;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleContainerClose(this);
    }

    public int getContainerId() {
        return this.containerId;
    }
}
