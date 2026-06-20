package net.minecraft.network.protocol.game;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public class ClientboundAddEntityPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAddEntityPacket> STREAM_CODEC = Packet.codec(
        ClientboundAddEntityPacket::write, ClientboundAddEntityPacket::new
    );
    private final int id;
    private final UUID uuid;
    private final EntityType<?> type;
    private final double x;
    private final double y;
    private final double z;
    private final Vec3 movement;
    private final byte xRot;
    private final byte yRot;
    private final byte yHeadRot;
    private final int data;

    public ClientboundAddEntityPacket(Entity entity, ServerEntity serverEntity) {
        this(entity, serverEntity, 0);
    }

    public ClientboundAddEntityPacket(Entity entity, ServerEntity serverEntity, int data) {
        this(
            entity.getId(),
            entity.getUUID(),
            // Paper start - fix entity tracker desync
            entity.trackingPosition().x(),
            entity.trackingPosition().y(),
            entity.trackingPosition().z(),
            // Paper end - fix entity tracker desync
            serverEntity.getLastSentXRot(),
            serverEntity.getLastSentYRot(),
            entity.getType(),
            data,
            serverEntity.getLastSentMovement(),
            serverEntity.getLastSentYHeadRot()
        );
    }

    public ClientboundAddEntityPacket(Entity entity, int data, BlockPos pos) {
        this(
            entity.getId(),
            entity.getUUID(),
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            entity.getXRot(),
            entity.getYRot(),
            entity.getType(),
            data,
            entity.getDeltaMovement(),
            entity.getYHeadRot()
        );
    }

    public ClientboundAddEntityPacket(
        int id, UUID uuid, double x, double y, double z, float xRot, float yRot, EntityType<?> type, int data, Vec3 deltaMovement, double yHeadRot
    ) {
        this.id = id;
        this.uuid = uuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.movement = deltaMovement;
        this.xRot = Mth.packDegrees(xRot);
        this.yRot = Mth.packDegrees(yRot);
        this.yHeadRot = Mth.packDegrees((float)yHeadRot);
        this.type = type;
        this.data = data;
    }

    private ClientboundAddEntityPacket(RegistryFriendlyByteBuf buffer) {
        this.id = buffer.readVarInt();
        this.uuid = buffer.readUUID();
        this.type = ByteBufCodecs.registry(Registries.ENTITY_TYPE).decode(buffer);
        this.x = buffer.readDouble();
        this.y = buffer.readDouble();
        this.z = buffer.readDouble();
        this.movement = buffer.readLpVec3();
        this.xRot = buffer.readByte();
        this.yRot = buffer.readByte();
        this.yHeadRot = buffer.readByte();
        this.data = buffer.readVarInt();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.id);
        buffer.writeUUID(this.uuid);
        ByteBufCodecs.registry(Registries.ENTITY_TYPE).encode(buffer, this.type);
        buffer.writeDouble(this.x);
        buffer.writeDouble(this.y);
        buffer.writeDouble(this.z);
        buffer.writeLpVec3(this.movement);
        buffer.writeByte(this.xRot);
        buffer.writeByte(this.yRot);
        buffer.writeByte(this.yHeadRot);
        buffer.writeVarInt(this.data);
    }

    @Override
    public PacketType<ClientboundAddEntityPacket> type() {
        return GamePacketTypes.CLIENTBOUND_ADD_ENTITY;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleAddEntity(this);
    }

    public int getId() {
        return this.id;
    }

    public UUID getUUID() {
        return this.uuid;
    }

    public EntityType<?> getType() {
        return this.type;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public Vec3 getMovement() {
        return this.movement;
    }

    public float getXRot() {
        return Mth.unpackDegrees(this.xRot);
    }

    public float getYRot() {
        return Mth.unpackDegrees(this.yRot);
    }

    public float getYHeadRot() {
        return Mth.unpackDegrees(this.yHeadRot);
    }

    public int getData() {
        return this.data;
    }
}
