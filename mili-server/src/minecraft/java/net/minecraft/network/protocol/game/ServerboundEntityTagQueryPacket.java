package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundEntityTagQueryPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundEntityTagQueryPacket> STREAM_CODEC = Packet.codec(
        ServerboundEntityTagQueryPacket::write, ServerboundEntityTagQueryPacket::new
    );
    private final int transactionId;
    private final int entityId;

    public ServerboundEntityTagQueryPacket(int transactionId, int entityId) {
        this.transactionId = transactionId;
        this.entityId = entityId;
    }

    private ServerboundEntityTagQueryPacket(FriendlyByteBuf buffer) {
        this.transactionId = buffer.readVarInt();
        this.entityId = buffer.readVarInt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.transactionId);
        buffer.writeVarInt(this.entityId);
    }

    @Override
    public PacketType<ServerboundEntityTagQueryPacket> type() {
        return GamePacketTypes.SERVERBOUND_ENTITY_TAG_QUERY;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleEntityTagQuery(this);
    }

    public int getTransactionId() {
        return this.transactionId;
    }

    public int getEntityId() {
        return this.entityId;
    }
}
