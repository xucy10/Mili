package net.minecraft.network.protocol.game;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import org.jspecify.annotations.Nullable;

public class ClientboundTagQueryPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundTagQueryPacket> STREAM_CODEC = Packet.codec(
        ClientboundTagQueryPacket::write, ClientboundTagQueryPacket::new
    );
    private final int transactionId;
    private final @Nullable CompoundTag tag;

    public ClientboundTagQueryPacket(int transactionId, @Nullable CompoundTag tag) {
        this.transactionId = transactionId;
        this.tag = tag;
    }

    private ClientboundTagQueryPacket(FriendlyByteBuf buffer) {
        this.transactionId = buffer.readVarInt();
        this.tag = buffer.readNbt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.transactionId);
        buffer.writeNbt(this.tag);
    }

    @Override
    public PacketType<ClientboundTagQueryPacket> type() {
        return GamePacketTypes.CLIENTBOUND_TAG_QUERY;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleTagQueryPacket(this);
    }

    public int getTransactionId() {
        return this.transactionId;
    }

    public @Nullable CompoundTag getTag() {
        return this.tag;
    }

    @Override
    public boolean isSkippable() {
        return true;
    }
}
