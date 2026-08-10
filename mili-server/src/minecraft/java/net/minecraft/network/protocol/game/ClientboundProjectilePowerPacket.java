package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundProjectilePowerPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundProjectilePowerPacket> STREAM_CODEC = Packet.codec(
        ClientboundProjectilePowerPacket::write, ClientboundProjectilePowerPacket::new
    );
    private final int id;
    private final double accelerationPower;

    public ClientboundProjectilePowerPacket(int id, double accelerationPower) {
        this.id = id;
        this.accelerationPower = accelerationPower;
    }

    private ClientboundProjectilePowerPacket(FriendlyByteBuf buffer) {
        this.id = buffer.readVarInt();
        this.accelerationPower = buffer.readDouble();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.id);
        buffer.writeDouble(this.accelerationPower);
    }

    @Override
    public PacketType<ClientboundProjectilePowerPacket> type() {
        return GamePacketTypes.CLIENTBOUND_PROJECTILE_POWER;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleProjectilePowerPacket(this);
    }

    public int getId() {
        return this.id;
    }

    public double getAccelerationPower() {
        return this.accelerationPower;
    }
}
