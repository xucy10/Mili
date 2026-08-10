package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundOpenSignEditorPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundOpenSignEditorPacket> STREAM_CODEC = Packet.codec(
        ClientboundOpenSignEditorPacket::write, ClientboundOpenSignEditorPacket::new
    );
    private final BlockPos pos;
    private final boolean isFrontText;

    public ClientboundOpenSignEditorPacket(BlockPos pos, boolean isFrontText) {
        this.pos = pos;
        this.isFrontText = isFrontText;
    }

    private ClientboundOpenSignEditorPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.isFrontText = buffer.readBoolean();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeBoolean(this.isFrontText);
    }

    @Override
    public PacketType<ClientboundOpenSignEditorPacket> type() {
        return GamePacketTypes.CLIENTBOUND_OPEN_SIGN_EDITOR;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleOpenSignEditor(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public boolean isFrontText() {
        return this.isFrontText;
    }
}
