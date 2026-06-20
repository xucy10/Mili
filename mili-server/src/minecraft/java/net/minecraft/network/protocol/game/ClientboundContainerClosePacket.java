package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundContainerClosePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundContainerClosePacket> STREAM_CODEC = Packet.codec(
        ClientboundContainerClosePacket::write, ClientboundContainerClosePacket::new
    );
    private final int containerId;

    public ClientboundContainerClosePacket(int containerId) {
        this.containerId = containerId;
    }

    private ClientboundContainerClosePacket(FriendlyByteBuf buffer) {
        this.containerId = buffer.readContainerId();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeContainerId(this.containerId);
    }

    @Override
    public PacketType<ClientboundContainerClosePacket> type() {
        return GamePacketTypes.CLIENTBOUND_CONTAINER_CLOSE;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleContainerClose(this);
    }

    public int getContainerId() {
        return this.containerId;
    }
}
