package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundSignUpdatePacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundSignUpdatePacket> STREAM_CODEC = Packet.codec(
        ServerboundSignUpdatePacket::write, ServerboundSignUpdatePacket::new
    );
    private static final int MAX_STRING_LENGTH = 384;
    private final BlockPos pos;
    private final String[] lines;
    private final boolean isFrontText;

    public ServerboundSignUpdatePacket(BlockPos pos, boolean isFrontText, String line1, String line2, String line3, String line4) {
        this.pos = pos;
        this.isFrontText = isFrontText;
        this.lines = new String[]{line1, line2, line3, line4};
    }

    private ServerboundSignUpdatePacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.isFrontText = buffer.readBoolean();
        this.lines = new String[4];

        for (int i = 0; i < 4; i++) {
            this.lines[i] = buffer.readUtf(384);
        }
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeBoolean(this.isFrontText);

        for (int i = 0; i < 4; i++) {
            buffer.writeUtf(this.lines[i]);
        }
    }

    @Override
    public PacketType<ServerboundSignUpdatePacket> type() {
        return GamePacketTypes.SERVERBOUND_SIGN_UPDATE;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleSignUpdate(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public boolean isFrontText() {
        return this.isFrontText;
    }

    public String[] getLines() {
        return this.lines;
    }
}
