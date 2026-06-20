package net.minecraft.network.protocol.game;

import java.util.BitSet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;

public class ClientboundLightUpdatePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundLightUpdatePacket> STREAM_CODEC = Packet.codec(
        ClientboundLightUpdatePacket::write, ClientboundLightUpdatePacket::new
    );
    private final int x;
    private final int z;
    private final ClientboundLightUpdatePacketData lightData;

    public ClientboundLightUpdatePacket(ChunkPos chunkPos, LevelLightEngine lightEngine, @Nullable BitSet skyLight, @Nullable BitSet blockLight) {
        this.x = chunkPos.x;
        this.z = chunkPos.z;
        this.lightData = new ClientboundLightUpdatePacketData(chunkPos, lightEngine, skyLight, blockLight);
    }

    private ClientboundLightUpdatePacket(FriendlyByteBuf buffer) {
        this.x = buffer.readVarInt();
        this.z = buffer.readVarInt();
        this.lightData = new ClientboundLightUpdatePacketData(buffer, this.x, this.z);
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.x);
        buffer.writeVarInt(this.z);
        this.lightData.write(buffer);
    }

    @Override
    public PacketType<ClientboundLightUpdatePacket> type() {
        return GamePacketTypes.CLIENTBOUND_LIGHT_UPDATE;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleLightUpdatePacket(this);
    }

    public int getX() {
        return this.x;
    }

    public int getZ() {
        return this.z;
    }

    public ClientboundLightUpdatePacketData getLightData() {
        return this.lightData;
    }
}
