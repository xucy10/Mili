package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundSelectBundleItemPacket(int slotId, int selectedItemIndex) implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundSelectBundleItemPacket> STREAM_CODEC = Packet.codec(
        ServerboundSelectBundleItemPacket::write, ServerboundSelectBundleItemPacket::new
    );

    private ServerboundSelectBundleItemPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt());
        if (this.selectedItemIndex < 0 && this.selectedItemIndex != -1) {
            throw new IllegalArgumentException("Invalid selectedItemIndex: " + this.selectedItemIndex);
        }
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.slotId);
        buffer.writeVarInt(this.selectedItemIndex);
    }

    @Override
    public PacketType<ServerboundSelectBundleItemPacket> type() {
        return GamePacketTypes.SERVERBOUND_BUNDLE_ITEM_SELECTED;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleBundleItemSelectedPacket(this);
    }
}
