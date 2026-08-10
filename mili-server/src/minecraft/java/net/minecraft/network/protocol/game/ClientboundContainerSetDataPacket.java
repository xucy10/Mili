package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundContainerSetDataPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundContainerSetDataPacket> STREAM_CODEC = Packet.codec(
        ClientboundContainerSetDataPacket::write, ClientboundContainerSetDataPacket::new
    );
    private final int containerId;
    private final int id;
    private final int value;

    public ClientboundContainerSetDataPacket(int containerId, int id, int value) {
        this.containerId = containerId;
        this.id = id;
        this.value = value;
    }

    private ClientboundContainerSetDataPacket(FriendlyByteBuf buffer) {
        this.containerId = buffer.readContainerId();
        this.id = buffer.readShort();
        this.value = buffer.readShort();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeContainerId(this.containerId);
        buffer.writeShort(this.id);
        buffer.writeShort(this.value);
    }

    @Override
    public PacketType<ClientboundContainerSetDataPacket> type() {
        return GamePacketTypes.CLIENTBOUND_CONTAINER_SET_DATA;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleContainerSetData(this);
    }

    public int getContainerId() {
        return this.containerId;
    }

    public int getId() {
        return this.id;
    }

    public int getValue() {
        return this.value;
    }
}
