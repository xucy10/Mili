package net.minecraft.network.protocol.game;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class ClientboundSoundPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSoundPacket> STREAM_CODEC = Packet.codec(
        ClientboundSoundPacket::write, ClientboundSoundPacket::new
    );
    public static final float LOCATION_ACCURACY = 8.0F;
    private final Holder<SoundEvent> sound;
    private final SoundSource source;
    private final int x;
    private final int y;
    private final int z;
    private final float volume;
    private final float pitch;
    private final long seed;

    public ClientboundSoundPacket(Holder<SoundEvent> sound, SoundSource source, double x, double y, double z, float volume, float pitch, long seed) {
        this.sound = sound;
        this.source = source;
        this.x = (int)(x * 8.0);
        this.y = (int)(y * 8.0);
        this.z = (int)(z * 8.0);
        this.volume = volume;
        this.pitch = pitch;
        this.seed = seed;
    }

    private ClientboundSoundPacket(RegistryFriendlyByteBuf buffer) {
        this.sound = SoundEvent.STREAM_CODEC.decode(buffer);
        this.source = buffer.readEnum(SoundSource.class);
        this.x = buffer.readInt();
        this.y = buffer.readInt();
        this.z = buffer.readInt();
        this.volume = buffer.readFloat();
        this.pitch = buffer.readFloat();
        this.seed = buffer.readLong();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        SoundEvent.STREAM_CODEC.encode(buffer, this.sound);
        buffer.writeEnum(this.source);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeFloat(this.volume);
        buffer.writeFloat(this.pitch);
        buffer.writeLong(this.seed);
    }

    @Override
    public PacketType<ClientboundSoundPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SOUND;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleSoundEvent(this);
    }

    public Holder<SoundEvent> getSound() {
        return this.sound;
    }

    public SoundSource getSource() {
        return this.source;
    }

    public double getX() {
        return this.x / 8.0F;
    }

    public double getY() {
        return this.y / 8.0F;
    }

    public double getZ() {
        return this.z / 8.0F;
    }

    public float getVolume() {
        return this.volume;
    }

    public float getPitch() {
        return this.pitch;
    }

    public long getSeed() {
        return this.seed;
    }
}
