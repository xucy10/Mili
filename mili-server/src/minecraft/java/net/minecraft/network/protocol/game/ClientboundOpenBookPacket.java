package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.InteractionHand;

public class ClientboundOpenBookPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundOpenBookPacket> STREAM_CODEC = Packet.codec(
        ClientboundOpenBookPacket::write, ClientboundOpenBookPacket::new
    );
    private final InteractionHand hand;

    public ClientboundOpenBookPacket(InteractionHand hand) {
        this.hand = hand;
    }

    private ClientboundOpenBookPacket(FriendlyByteBuf buffer) {
        this.hand = buffer.readEnum(InteractionHand.class);
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.hand);
    }

    @Override
    public PacketType<ClientboundOpenBookPacket> type() {
        return GamePacketTypes.CLIENTBOUND_OPEN_BOOK;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleOpenBook(this);
    }

    public InteractionHand getHand() {
        return this.hand;
    }
}
