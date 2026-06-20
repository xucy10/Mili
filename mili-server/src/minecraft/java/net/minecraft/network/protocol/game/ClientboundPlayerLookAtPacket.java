package net.minecraft.network.protocol.game;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ClientboundPlayerLookAtPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundPlayerLookAtPacket> STREAM_CODEC = Packet.codec(
        ClientboundPlayerLookAtPacket::write, ClientboundPlayerLookAtPacket::new
    );
    private final double x;
    private final double y;
    private final double z;
    private final int entity;
    private final EntityAnchorArgument.Anchor fromAnchor;
    private final EntityAnchorArgument.Anchor toAnchor;
    private final boolean atEntity;

    public ClientboundPlayerLookAtPacket(EntityAnchorArgument.Anchor fromAnchor, double x, double y, double z) {
        this.fromAnchor = fromAnchor;
        this.x = x;
        this.y = y;
        this.z = z;
        this.entity = 0;
        this.atEntity = false;
        this.toAnchor = null;
    }

    public ClientboundPlayerLookAtPacket(EntityAnchorArgument.Anchor fromAnchor, Entity entity, EntityAnchorArgument.Anchor toAnchor) {
        this.fromAnchor = fromAnchor;
        this.entity = entity.getId();
        this.toAnchor = toAnchor;
        Vec3 vec3 = toAnchor.apply(entity);
        this.x = vec3.x;
        this.y = vec3.y;
        this.z = vec3.z;
        this.atEntity = true;
    }

    private ClientboundPlayerLookAtPacket(FriendlyByteBuf buffer) {
        this.fromAnchor = buffer.readEnum(EntityAnchorArgument.Anchor.class);
        this.x = buffer.readDouble();
        this.y = buffer.readDouble();
        this.z = buffer.readDouble();
        this.atEntity = buffer.readBoolean();
        if (this.atEntity) {
            this.entity = buffer.readVarInt();
            this.toAnchor = buffer.readEnum(EntityAnchorArgument.Anchor.class);
        } else {
            this.entity = 0;
            this.toAnchor = null;
        }
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.fromAnchor);
        buffer.writeDouble(this.x);
        buffer.writeDouble(this.y);
        buffer.writeDouble(this.z);
        buffer.writeBoolean(this.atEntity);
        if (this.atEntity) {
            buffer.writeVarInt(this.entity);
            buffer.writeEnum(this.toAnchor);
        }
    }

    @Override
    public PacketType<ClientboundPlayerLookAtPacket> type() {
        return GamePacketTypes.CLIENTBOUND_PLAYER_LOOK_AT;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleLookAt(this);
    }

    public EntityAnchorArgument.Anchor getFromAnchor() {
        return this.fromAnchor;
    }

    public @Nullable Vec3 getPosition(Level level) {
        if (this.atEntity) {
            Entity entity = level.getEntity(this.entity);
            return entity == null ? new Vec3(this.x, this.y, this.z) : this.toAnchor.apply(entity);
        } else {
            return new Vec3(this.x, this.y, this.z);
        }
    }
}
