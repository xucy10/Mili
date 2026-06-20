package net.minecraft.network.protocol.game;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ClientboundRespawnPacket(CommonPlayerSpawnInfo commonPlayerSpawnInfo, byte dataToKeep) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRespawnPacket> STREAM_CODEC = Packet.codec(
        ClientboundRespawnPacket::write, ClientboundRespawnPacket::new
    );
    public static final byte KEEP_ATTRIBUTE_MODIFIERS = 1;
    public static final byte KEEP_ENTITY_DATA = 2;
    public static final byte KEEP_ALL_DATA = 3;

    private ClientboundRespawnPacket(RegistryFriendlyByteBuf buffer) {
        this(new CommonPlayerSpawnInfo(buffer), buffer.readByte());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        this.commonPlayerSpawnInfo.write(buffer);
        buffer.writeByte(this.dataToKeep);
    }

    @Override
    public PacketType<ClientboundRespawnPacket> type() {
        return GamePacketTypes.CLIENTBOUND_RESPAWN;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleRespawn(this);
    }

    public boolean shouldKeep(byte data) {
        return (this.dataToKeep & data) != 0;
    }
}
