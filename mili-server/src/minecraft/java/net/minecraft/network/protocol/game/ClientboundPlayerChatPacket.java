package net.minecraft.network.protocol.game;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import org.jspecify.annotations.Nullable;

public record ClientboundPlayerChatPacket(
    int globalIndex,
    UUID sender,
    int index,
    @Nullable MessageSignature signature,
    SignedMessageBody.Packed body,
    @Nullable Component unsignedContent,
    FilterMask filterMask,
    ChatType.Bound chatType
) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPlayerChatPacket> STREAM_CODEC = Packet.codec(
        ClientboundPlayerChatPacket::write, ClientboundPlayerChatPacket::new
    );

    private ClientboundPlayerChatPacket(RegistryFriendlyByteBuf buffer) {
        this(
            buffer.readVarInt(),
            buffer.readUUID(),
            buffer.readVarInt(),
            buffer.readNullable(MessageSignature::read),
            new SignedMessageBody.Packed(buffer),
            FriendlyByteBuf.readNullable(buffer, ComponentSerialization.TRUSTED_STREAM_CODEC),
            FilterMask.read(buffer),
            ChatType.Bound.STREAM_CODEC.decode(buffer)
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.globalIndex);
        buffer.writeUUID(this.sender);
        buffer.writeVarInt(this.index);
        buffer.writeNullable(this.signature, MessageSignature::write);
        this.body.write(buffer);
        FriendlyByteBuf.writeNullable(buffer, this.unsignedContent, ComponentSerialization.TRUSTED_STREAM_CODEC);
        FilterMask.write(buffer, this.filterMask);
        ChatType.Bound.STREAM_CODEC.encode(buffer, this.chatType);
    }

    @Override
    public PacketType<ClientboundPlayerChatPacket> type() {
        return GamePacketTypes.CLIENTBOUND_PLAYER_CHAT;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handlePlayerChat(this);
    }

    @Override
    public boolean isSkippable() {
        return true;
    }
}
