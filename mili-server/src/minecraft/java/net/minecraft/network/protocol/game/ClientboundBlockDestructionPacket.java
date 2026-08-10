package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundBlockDestructionPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundBlockDestructionPacket> STREAM_CODEC = Packet.codec(
        ClientboundBlockDestructionPacket::write, ClientboundBlockDestructionPacket::new
    );
    private final int id;
    private final BlockPos pos;
    private final int progress;

    public ClientboundBlockDestructionPacket(int id, BlockPos pos, int progress) {
        this.id = id;
        this.pos = pos;
        this.progress = progress;
    }

    private ClientboundBlockDestructionPacket(FriendlyByteBuf buffer) {
        this.id = buffer.readVarInt();
        this.pos = buffer.readBlockPos();
        this.progress = buffer.readUnsignedByte();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.id);
        buffer.writeBlockPos(this.pos);
        buffer.writeByte(this.progress);
    }

    @Override
    public PacketType<ClientboundBlockDestructionPacket> type() {
        return GamePacketTypes.CLIENTBOUND_BLOCK_DESTRUCTION;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleBlockDestruction(this);
    }

    public int getId() {
        return this.id;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getProgress() {
        return this.progress;
    }
}
