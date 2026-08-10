package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import org.jspecify.annotations.Nullable;

public record ClientboundResetScorePacket(String owner, @Nullable String objectiveName) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundResetScorePacket> STREAM_CODEC = Packet.codec(
        ClientboundResetScorePacket::write, ClientboundResetScorePacket::new
    );

    private ClientboundResetScorePacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readNullable(FriendlyByteBuf::readUtf));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.owner);
        buffer.writeNullable(this.objectiveName, FriendlyByteBuf::writeUtf);
    }

    @Override
    public PacketType<ClientboundResetScorePacket> type() {
        return GamePacketTypes.CLIENTBOUND_RESET_SCORE;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleResetScore(this);
    }
}
