package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundBlockEntityTagQueryPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundBlockEntityTagQueryPacket> STREAM_CODEC = Packet.codec(
        ServerboundBlockEntityTagQueryPacket::write, ServerboundBlockEntityTagQueryPacket::new
    );
    private final int transactionId;
    private final BlockPos pos;

    public ServerboundBlockEntityTagQueryPacket(int transactionId, BlockPos pos) {
        this.transactionId = transactionId;
        this.pos = pos;
    }

    private ServerboundBlockEntityTagQueryPacket(FriendlyByteBuf buffer) {
        this.transactionId = buffer.readVarInt();
        this.pos = buffer.readBlockPos();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.transactionId);
        buffer.writeBlockPos(this.pos);
    }

    @Override
    public PacketType<ServerboundBlockEntityTagQueryPacket> type() {
        return GamePacketTypes.SERVERBOUND_BLOCK_ENTITY_TAG_QUERY;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleBlockEntityTagQuery(this);
    }

    public int getTransactionId() {
        return this.transactionId;
    }

    public BlockPos getPos() {
        return this.pos;
    }
}
