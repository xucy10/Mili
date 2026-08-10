package net.minecraft.network.protocol.game;

import com.google.common.collect.Sets;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record ClientboundLoginPacket(
    int playerId,
    boolean hardcore,
    Set<ResourceKey<Level>> levels,
    int maxPlayers,
    int chunkRadius,
    int simulationDistance,
    boolean reducedDebugInfo,
    boolean showDeathScreen,
    boolean doLimitedCrafting,
    CommonPlayerSpawnInfo commonPlayerSpawnInfo,
    boolean enforcesSecureChat
) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundLoginPacket> STREAM_CODEC = Packet.codec(
        ClientboundLoginPacket::write, ClientboundLoginPacket::new
    );

    private ClientboundLoginPacket(RegistryFriendlyByteBuf buffer) {
        this(
            buffer.readInt(),
            buffer.readBoolean(),
            buffer.readCollection(Sets::newHashSetWithExpectedSize, buffer1 -> buffer1.readResourceKey(Registries.DIMENSION)),
            buffer.readVarInt(),
            buffer.readVarInt(),
            buffer.readVarInt(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            new CommonPlayerSpawnInfo(buffer),
            buffer.readBoolean()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(this.playerId);
        buffer.writeBoolean(this.hardcore);
        buffer.writeCollection(this.levels, FriendlyByteBuf::writeResourceKey);
        buffer.writeVarInt(this.maxPlayers);
        buffer.writeVarInt(this.chunkRadius);
        buffer.writeVarInt(this.simulationDistance);
        buffer.writeBoolean(this.reducedDebugInfo);
        buffer.writeBoolean(this.showDeathScreen);
        buffer.writeBoolean(this.doLimitedCrafting);
        this.commonPlayerSpawnInfo.write(buffer);
        buffer.writeBoolean(this.enforcesSecureChat);
    }

    @Override
    public PacketType<ClientboundLoginPacket> type() {
        return GamePacketTypes.CLIENTBOUND_LOGIN;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleLogin(this);
    }
}
