package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundSetHealthPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetHealthPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetHealthPacket::write, ClientboundSetHealthPacket::new
    );
    private final float health;
    private final int food;
    private final float saturation;

    public ClientboundSetHealthPacket(float health, int food, float saturation) {
        this.health = health;
        this.food = food;
        this.saturation = saturation;
    }

    private ClientboundSetHealthPacket(FriendlyByteBuf buffer) {
        this.health = buffer.readFloat();
        this.food = buffer.readVarInt();
        this.saturation = buffer.readFloat();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeFloat(this.health);
        buffer.writeVarInt(this.food);
        buffer.writeFloat(this.saturation);
    }

    @Override
    public PacketType<ClientboundSetHealthPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_HEALTH;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleSetHealth(this);
    }

    public float getHealth() {
        return this.health;
    }

    public int getFood() {
        return this.food;
    }

    public float getSaturation() {
        return this.saturation;
    }
}
