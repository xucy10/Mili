package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public class ClientboundSetEntityLinkPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetEntityLinkPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetEntityLinkPacket::write, ClientboundSetEntityLinkPacket::new
    );
    private final int sourceId;
    private final int destId;

    public ClientboundSetEntityLinkPacket(Entity source, @Nullable Entity destination) {
        this.sourceId = source.getId();
        this.destId = destination != null ? destination.getId() : 0;
    }

    private ClientboundSetEntityLinkPacket(FriendlyByteBuf buffer) {
        this.sourceId = buffer.readInt();
        this.destId = buffer.readInt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeInt(this.sourceId);
        buffer.writeInt(this.destId);
    }

    @Override
    public PacketType<ClientboundSetEntityLinkPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_ENTITY_LINK;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleEntityLinkPacket(this);
    }

    public int getSourceId() {
        return this.sourceId;
    }

    public int getDestId() {
        return this.destId;
    }
}
