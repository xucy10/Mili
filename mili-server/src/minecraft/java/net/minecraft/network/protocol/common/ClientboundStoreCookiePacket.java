package net.minecraft.network.protocol.common;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;

public record ClientboundStoreCookiePacket(Identifier key, byte[] payload) implements Packet<ClientCommonPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundStoreCookiePacket> STREAM_CODEC = Packet.codec(
        ClientboundStoreCookiePacket::write, ClientboundStoreCookiePacket::new
    );
    private static final int MAX_PAYLOAD_SIZE = 5120;
    public static final StreamCodec<ByteBuf, byte[]> PAYLOAD_STREAM_CODEC = ByteBufCodecs.byteArray(5120);

    private ClientboundStoreCookiePacket(FriendlyByteBuf buffer) {
        this(buffer.readIdentifier(), PAYLOAD_STREAM_CODEC.decode(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeIdentifier(this.key);
        PAYLOAD_STREAM_CODEC.encode(buffer, this.payload);
    }

    @Override
    public PacketType<ClientboundStoreCookiePacket> type() {
        return CommonPacketTypes.CLIENTBOUND_STORE_COOKIE;
    }

    @Override
    public void handle(ClientCommonPacketListener handler) {
        handler.handleStoreCookie(this);
    }
}
