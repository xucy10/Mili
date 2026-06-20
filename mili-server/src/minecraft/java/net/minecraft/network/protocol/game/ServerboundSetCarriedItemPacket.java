package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundSetCarriedItemPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundSetCarriedItemPacket> STREAM_CODEC = Packet.codec(
        ServerboundSetCarriedItemPacket::write, ServerboundSetCarriedItemPacket::new
    );
    private final int slot;

    public ServerboundSetCarriedItemPacket(int slot) {
        this.slot = slot;
    }

    private ServerboundSetCarriedItemPacket(FriendlyByteBuf buffer) {
        this.slot = buffer.readShort();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeShort(this.slot);
    }

    @Override
    public PacketType<ServerboundSetCarriedItemPacket> type() {
        return GamePacketTypes.SERVERBOUND_SET_CARRIED_ITEM;
    }

    @Override
    public void handle(ServerGamePacketListener handler) {
        handler.handleSetCarriedItem(this);
    }

    public int getSlot() {
        return this.slot;
    }
}
