package net.minecraft.network.protocol.game;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

public class ClientboundSoundEntityPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSoundEntityPacket> STREAM_CODEC = Packet.codec(
        ClientboundSoundEntityPacket::write, ClientboundSoundEntityPacket::new
    );
    private final Holder<SoundEvent> sound;
    private final SoundSource source;
    private final int id;
    private final float volume;
    private final float pitch;
    private final long seed;

    public ClientboundSoundEntityPacket(Holder<SoundEvent> sound, SoundSource source, Entity entity, float volume, float pitch, long seed) {
        this.sound = sound;
        this.source = source;
        this.id = entity.getId();
        this.volume = volume;
        this.pitch = pitch;
        this.seed = seed;
    }

    private ClientboundSoundEntityPacket(RegistryFriendlyByteBuf buffer) {
        this.sound = SoundEvent.STREAM_CODEC.decode(buffer);
        this.source = buffer.readEnum(SoundSource.class);
        this.id = buffer.readVarInt();
        this.volume = buffer.readFloat();
        this.pitch = buffer.readFloat();
        this.seed = buffer.readLong();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        SoundEvent.STREAM_CODEC.encode(buffer, this.sound);
        buffer.writeEnum(this.source);
        buffer.writeVarInt(this.id);
        buffer.writeFloat(this.volume);
        buffer.writeFloat(this.pitch);
        buffer.writeLong(this.seed);
    }

    @Override
    public PacketType<ClientboundSoundEntityPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SOUND_ENTITY;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleSoundEntityEvent(this);
    }

    public Holder<SoundEvent> getSound() {
        return this.sound;
    }

    public SoundSource getSource() {
        return this.source;
    }

    public int getId() {
        return this.id;
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
