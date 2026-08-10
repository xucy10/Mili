package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundClearTitlesPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundClearTitlesPacket> STREAM_CODEC = Packet.codec(
        ClientboundClearTitlesPacket::write, ClientboundClearTitlesPacket::new
    );
    private final boolean resetTimes;

    public ClientboundClearTitlesPacket(boolean resetTimes) {
        this.resetTimes = resetTimes;
    }

    private ClientboundClearTitlesPacket(FriendlyByteBuf buffer) {
        this.resetTimes = buffer.readBoolean();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.resetTimes);
    }

    @Override
    public PacketType<ClientboundClearTitlesPacket> type() {
        return GamePacketTypes.CLIENTBOUND_CLEAR_TITLES;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleTitlesClear(this);
    }

    public boolean shouldResetTimes() {
        return this.resetTimes;
    }
}
