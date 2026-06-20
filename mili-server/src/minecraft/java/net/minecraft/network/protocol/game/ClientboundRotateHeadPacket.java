package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class ClientboundRotateHeadPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundRotateHeadPacket> STREAM_CODEC = Packet.codec(
        ClientboundRotateHeadPacket::write, ClientboundRotateHeadPacket::new
    );
    private final int entityId;
    private final byte yHeadRot;

    public ClientboundRotateHeadPacket(Entity entity, byte yHeadRot) {
        this.entityId = entity.getId();
        this.yHeadRot = yHeadRot;
    }

    private ClientboundRotateHeadPacket(FriendlyByteBuf buffer) {
        this.entityId = buffer.readVarInt();
        this.yHeadRot = buffer.readByte();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeByte(this.yHeadRot);
    }

    @Override
    public PacketType<ClientboundRotateHeadPacket> type() {
        return GamePacketTypes.CLIENTBOUND_ROTATE_HEAD;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleRotateMob(this);
    }

    public @Nullable Entity getEntity(Level level) {
        return level.getEntity(this.entityId);
    }

    public float getYHeadRot() {
        return Mth.unpackDegrees(this.yHeadRot);
    }
}
