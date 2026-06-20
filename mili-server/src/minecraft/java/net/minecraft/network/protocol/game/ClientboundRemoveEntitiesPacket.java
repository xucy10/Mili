package net.minecraft.network.protocol.game;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundRemoveEntitiesPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundRemoveEntitiesPacket> STREAM_CODEC = Packet.codec(
        ClientboundRemoveEntitiesPacket::write, ClientboundRemoveEntitiesPacket::new
    );
    private final IntList entityIds;

    public ClientboundRemoveEntitiesPacket(IntList entityIds) {
        this.entityIds = new IntArrayList(entityIds);
    }

    public ClientboundRemoveEntitiesPacket(int... entityIds) {
        this.entityIds = new IntArrayList(entityIds);
    }

    private ClientboundRemoveEntitiesPacket(FriendlyByteBuf buffer) {
        this.entityIds = buffer.readIntIdList();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeIntIdList(this.entityIds);
    }

    @Override
    public PacketType<ClientboundRemoveEntitiesPacket> type() {
        return GamePacketTypes.CLIENTBOUND_REMOVE_ENTITIES;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleRemoveEntities(this);
    }

    public IntList getEntityIds() {
        return this.entityIds;
    }
}
