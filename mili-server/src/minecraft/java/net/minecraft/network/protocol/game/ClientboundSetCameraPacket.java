package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class ClientboundSetCameraPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetCameraPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetCameraPacket::write, ClientboundSetCameraPacket::new
    );
    private final int cameraId;

    public ClientboundSetCameraPacket(Entity cameraEntity) {
        this.cameraId = cameraEntity.getId();
    }

    private ClientboundSetCameraPacket(FriendlyByteBuf buffer) {
        this.cameraId = buffer.readVarInt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.cameraId);
    }

    @Override
    public PacketType<ClientboundSetCameraPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_CAMERA;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleSetCamera(this);
    }

    public @Nullable Entity getEntity(Level level) {
        return level.getEntity(this.cameraId);
    }
}
