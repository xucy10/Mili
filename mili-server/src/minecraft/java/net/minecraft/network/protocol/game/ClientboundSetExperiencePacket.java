package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundSetExperiencePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetExperiencePacket> STREAM_CODEC = Packet.codec(
        ClientboundSetExperiencePacket::write, ClientboundSetExperiencePacket::new
    );
    private final float experienceProgress;
    private final int totalExperience;
    private final int experienceLevel;

    public ClientboundSetExperiencePacket(float experienceProgress, int totalExperience, int experienceLevel) {
        this.experienceProgress = experienceProgress;
        this.totalExperience = totalExperience;
        this.experienceLevel = experienceLevel;
    }

    private ClientboundSetExperiencePacket(FriendlyByteBuf buffer) {
        this.experienceProgress = buffer.readFloat();
        this.experienceLevel = buffer.readVarInt();
        this.totalExperience = buffer.readVarInt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeFloat(this.experienceProgress);
        buffer.writeVarInt(this.experienceLevel);
        buffer.writeVarInt(this.totalExperience);
    }

    @Override
    public PacketType<ClientboundSetExperiencePacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_EXPERIENCE;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleSetExperience(this);
    }

    public float getExperienceProgress() {
        return this.experienceProgress;
    }

    public int getTotalExperience() {
        return this.totalExperience;
    }

    public int getExperienceLevel() {
        return this.experienceLevel;
    }
}
