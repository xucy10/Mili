package net.minecraft.network.protocol.login;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.network.protocol.login.custom.DiscardedQueryPayload;
import net.minecraft.resources.Identifier;

public record ClientboundCustomQueryPacket(int transactionId, CustomQueryPayload payload) implements Packet<ClientLoginPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundCustomQueryPacket> STREAM_CODEC = Packet.codec(
        ClientboundCustomQueryPacket::write, ClientboundCustomQueryPacket::new
    );
    private static final int MAX_PAYLOAD_SIZE = 1048576;

    private ClientboundCustomQueryPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), readPayload(buffer.readIdentifier(), buffer));
    }

    private static CustomQueryPayload readPayload(Identifier id, FriendlyByteBuf buffer) {
        return readUnknownPayload(id, buffer);
    }

    private static DiscardedQueryPayload readUnknownPayload(Identifier id, FriendlyByteBuf buffer) {
        int i = buffer.readableBytes();
        if (i >= 0 && i <= 1048576) {
            buffer.skipBytes(i);
            return new DiscardedQueryPayload(id);
        } else {
            throw new IllegalArgumentException("Payload may not be larger than 1048576 bytes");
        }
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.transactionId);
        buffer.writeIdentifier(this.payload.id());
        this.payload.write(buffer);
    }

    @Override
    public PacketType<ClientboundCustomQueryPacket> type() {
        return LoginPacketTypes.CLIENTBOUND_CUSTOM_QUERY;
    }

    @Override
    public void handle(ClientLoginPacketListener handler) {
        handler.handleCustomQuery(this);
    }

    // Paper start - MC Utils - default query payloads
    public static record PlayerInfoChannelPayload(Identifier id, FriendlyByteBuf buffer) implements CustomQueryPayload {
        @Override
        public void write(final FriendlyByteBuf buf) {
            buf.writeBytes(this.buffer.copy());
        }
    }
    // Paper end - MC Utils - default query payloads
}
