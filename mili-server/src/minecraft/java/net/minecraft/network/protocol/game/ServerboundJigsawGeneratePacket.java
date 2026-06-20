package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundJigsawGeneratePacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundJigsawGeneratePacket> STREAM_CODEC = Packet.codec(
        ServerboundJigsawGeneratePacket::write, ServerboundJigsawGeneratePacket::new
    );
    private final BlockPos pos;
    private final int levels;
    private final boolean keepJigsaws;

    public ServerboundJigsawGeneratePacket(BlockPos pos, int levels, boolean keepJigsaws) {
        this.pos = pos;
        this.levels = levels;
        this.keepJigsaws = keepJigsaws;
    }

    private ServerboundJigsawGeneratePacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.levels = buffer.readVarInt();
        this.keepJigsaws = buffer.readBoolean();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeVarInt(this.levels);
        buffer.writeBoolean(this.keepJigsaws);
    }

    @Override
    public PacketType<ServerboundJigsawGeneratePacket> type() {
        return GamePacketTypes.SERVERBOUND_JIGSAW_GENERATE;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleJigsawGenerate(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int levels() {
        return this.levels;
    }

    public boolean keepJigsaws() {
        return this.keepJigsaws;
    }
}
