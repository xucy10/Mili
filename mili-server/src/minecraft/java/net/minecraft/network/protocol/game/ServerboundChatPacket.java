package net.minecraft.network.protocol.game;

import java.time.Instant;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import org.jspecify.annotations.Nullable;

public record ServerboundChatPacket(String message, Instant timeStamp, long salt, @Nullable MessageSignature signature, LastSeenMessages.Update lastSeenMessages)
    implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundChatPacket> STREAM_CODEC = Packet.codec(
        ServerboundChatPacket::write, ServerboundChatPacket::new
    );

    private ServerboundChatPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(256), buffer.readInstant(), buffer.readLong(), buffer.readNullable(MessageSignature::read), new LastSeenMessages.Update(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.message, 256);
        buffer.writeInstant(this.timeStamp);
        buffer.writeLong(this.salt);
        buffer.writeNullable(this.signature, MessageSignature::write);
        this.lastSeenMessages.write(buffer);
    }

    @Override
    public PacketType<ServerboundChatPacket> type() {
        return GamePacketTypes.SERVERBOUND_CHAT;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleChat(this);
    }
}
