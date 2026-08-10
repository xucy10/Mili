package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.LivingEntity;

public record ClientboundHurtAnimationPacket(int id, float yaw) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundHurtAnimationPacket> STREAM_CODEC = Packet.codec(
        ClientboundHurtAnimationPacket::write, ClientboundHurtAnimationPacket::new
    );

    public ClientboundHurtAnimationPacket(LivingEntity entity) {
        this(entity.getId(), entity.getHurtDir());
    }

    private ClientboundHurtAnimationPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readFloat());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.id);
        buffer.writeFloat(this.yaw);
    }

    @Override
    public PacketType<ClientboundHurtAnimationPacket> type() {
        return GamePacketTypes.CLIENTBOUND_HURT_ANIMATION;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleHurtAnimation(this);
    }
}
