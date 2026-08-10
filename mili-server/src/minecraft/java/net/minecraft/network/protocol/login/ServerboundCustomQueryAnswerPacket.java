package net.minecraft.network.protocol.login;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import net.minecraft.network.protocol.login.custom.DiscardedQueryAnswerPayload;
import org.jspecify.annotations.Nullable;

public record ServerboundCustomQueryAnswerPacket(int transactionId, @Nullable CustomQueryAnswerPayload payload) implements Packet<ServerLoginPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundCustomQueryAnswerPacket> STREAM_CODEC = Packet.codec(
        ServerboundCustomQueryAnswerPacket::write, ServerboundCustomQueryAnswerPacket::read
    );
    private static final int MAX_PAYLOAD_SIZE = 1048576;

    private static ServerboundCustomQueryAnswerPacket read(FriendlyByteBuf buffer) {
        int varInt = buffer.readVarInt();
        return new ServerboundCustomQueryAnswerPacket(varInt, readPayload(varInt, buffer));
    }

    private static CustomQueryAnswerPayload readPayload(int transactionId, FriendlyByteBuf buffer) {
        // Paper start - MC Utils - default query payloads
        FriendlyByteBuf buf = buffer.readNullable((buf2) -> {
            int readableBytes = buf2.readableBytes();
            if (readableBytes >= 0 && readableBytes <= MAX_PAYLOAD_SIZE) {
                return new FriendlyByteBuf(buf2.readBytes(readableBytes));
            } else {
                throw new IllegalArgumentException("Payload may not be larger than " + MAX_PAYLOAD_SIZE + " bytes");
            }
        });
        return buf == null ? null : new net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket.QueryAnswerPayload(buf);
        // Paper end - MC Utils - default query payloads
    }

    private static CustomQueryAnswerPayload readUnknownPayload(FriendlyByteBuf buffer) {
        int i = buffer.readableBytes();
        if (i >= 0 && i <= 1048576) {
            buffer.skipBytes(i);
            return DiscardedQueryAnswerPayload.INSTANCE;
        } else {
            throw new IllegalArgumentException("Payload may not be larger than 1048576 bytes");
        }
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.transactionId);
        buffer.writeNullable(this.payload, (buffer1, value) -> value.write(buffer1));
    }

    @Override
    public PacketType<ServerboundCustomQueryAnswerPacket> type() {
        return LoginPacketTypes.SERVERBOUND_CUSTOM_QUERY_ANSWER;
    }

    @Override
    public void handle(ServerLoginPacketListener handler) {
        handler.handleCustomQueryPacket(this);
    }

    // Paper start - MC Utils - default query payloads
    public static final class QueryAnswerPayload implements CustomQueryAnswerPayload {
        public final FriendlyByteBuf buffer;

        public QueryAnswerPayload(final net.minecraft.network.FriendlyByteBuf buffer) {
            this.buffer = buffer;
        }

        @Override
        public void write(final net.minecraft.network.FriendlyByteBuf buf) {
            buf.writeBytes(this.buffer.copy());
        }
    }
    // Paper end - MC Utils - default query payloads
}
