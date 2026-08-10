package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.border.WorldBorder;

public class ClientboundSetBorderWarningDistancePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetBorderWarningDistancePacket> STREAM_CODEC = Packet.codec(
        ClientboundSetBorderWarningDistancePacket::write, ClientboundSetBorderWarningDistancePacket::new
    );
    private final int warningBlocks;

    public ClientboundSetBorderWarningDistancePacket(WorldBorder worldBorder) {
        this.warningBlocks = worldBorder.getWarningBlocks();
    }

    private ClientboundSetBorderWarningDistancePacket(FriendlyByteBuf buffer) {
        this.warningBlocks = buffer.readVarInt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.warningBlocks);
    }

    @Override
    public PacketType<ClientboundSetBorderWarningDistancePacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_BORDER_WARNING_DISTANCE;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleSetBorderWarningDistance(this);
    }

    public int getWarningBlocks() {
        return this.warningBlocks;
    }
}
