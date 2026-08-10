package net.minecraft.network.protocol.game;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundLevelParticlesPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundLevelParticlesPacket> STREAM_CODEC = Packet.codec(
        ClientboundLevelParticlesPacket::write, ClientboundLevelParticlesPacket::new
    );
    private final double x;
    private final double y;
    private final double z;
    private final float xDist;
    private final float yDist;
    private final float zDist;
    private final float maxSpeed;
    private final int count;
    private final boolean overrideLimiter;
    private final boolean alwaysShow;
    private final ParticleOptions particle;

    public <T extends ParticleOptions> ClientboundLevelParticlesPacket(
        T particle, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, float xDist, float yDist, float zDist, float maxSpeed, int count
    ) {
        this.particle = particle;
        this.overrideLimiter = overrideLimiter;
        this.alwaysShow = alwaysShow;
        this.x = x;
        this.y = y;
        this.z = z;
        this.xDist = xDist;
        this.yDist = yDist;
        this.zDist = zDist;
        this.maxSpeed = maxSpeed;
        this.count = count;
    }

    private ClientboundLevelParticlesPacket(RegistryFriendlyByteBuf buffer) {
        this.overrideLimiter = buffer.readBoolean();
        this.alwaysShow = buffer.readBoolean();
        this.x = buffer.readDouble();
        this.y = buffer.readDouble();
        this.z = buffer.readDouble();
        this.xDist = buffer.readFloat();
        this.yDist = buffer.readFloat();
        this.zDist = buffer.readFloat();
        this.maxSpeed = buffer.readFloat();
        this.count = buffer.readInt();
        this.particle = ParticleTypes.STREAM_CODEC.decode(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(this.overrideLimiter);
        buffer.writeBoolean(this.alwaysShow);
        buffer.writeDouble(this.x);
        buffer.writeDouble(this.y);
        buffer.writeDouble(this.z);
        buffer.writeFloat(this.xDist);
        buffer.writeFloat(this.yDist);
        buffer.writeFloat(this.zDist);
        buffer.writeFloat(this.maxSpeed);
        buffer.writeInt(this.count);
        ParticleTypes.STREAM_CODEC.encode(buffer, this.particle);
    }

    @Override
    public PacketType<ClientboundLevelParticlesPacket> type() {
        return GamePacketTypes.CLIENTBOUND_LEVEL_PARTICLES;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleParticleEvent(this);
    }

    public boolean isOverrideLimiter() {
        return this.overrideLimiter;
    }

    public boolean alwaysShow() {
        return this.alwaysShow;
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

    public float getXDist() {
        return this.xDist;
    }

    public float getYDist() {
        return this.yDist;
    }

    public float getZDist() {
        return this.zDist;
    }

    public float getMaxSpeed() {
        return this.maxSpeed;
    }

    public int getCount() {
        return this.count;
    }

    public ParticleOptions getParticle() {
        return this.particle;
    }
}
