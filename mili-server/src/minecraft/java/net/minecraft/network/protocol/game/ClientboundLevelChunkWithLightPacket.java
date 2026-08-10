package net.minecraft.network.protocol.game;

import java.util.BitSet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;

public class ClientboundLevelChunkWithLightPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundLevelChunkWithLightPacket> STREAM_CODEC = Packet.codec(
        ClientboundLevelChunkWithLightPacket::write, ClientboundLevelChunkWithLightPacket::new
    );
    private final int x;
    private final int z;
    private final ClientboundLevelChunkPacketData chunkData;
    private final ClientboundLightUpdatePacketData lightData;
    // Paper start - Async-Anti-Xray - Ready flag for the connection, add chunk packet info
    private volatile boolean ready;

    @Override
    public boolean isReady() {
        return this.ready;
    }

    public void setReady(final boolean ready) {
        this.ready = ready;
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public ClientboundLevelChunkWithLightPacket(LevelChunk chunk, LevelLightEngine lightEngine, @Nullable BitSet skyLight, @Nullable BitSet blockLight) {
        this(chunk, lightEngine, skyLight, blockLight, true);
    }
    public ClientboundLevelChunkWithLightPacket(LevelChunk chunk, LevelLightEngine lightEngine, @Nullable BitSet skyLight, @Nullable BitSet blockLight, boolean modifyBlocks) {
        // Paper end - Anti-Xray
        ChunkPos pos = chunk.getPos();
        this.x = pos.x;
        this.z = pos.z;
        io.papermc.paper.antixray.ChunkPacketInfo<net.minecraft.world.level.block.state.BlockState> chunkPacketInfo = modifyBlocks ? chunk.getLevel().chunkPacketBlockController.getChunkPacketInfo(this, chunk) : null; // Paper - Ant-Xray
        this.chunkData = new ClientboundLevelChunkPacketData(chunk, chunkPacketInfo); // Paper - Anti-Xray
        this.lightData = new ClientboundLightUpdatePacketData(pos, lightEngine, skyLight, blockLight);
        chunk.getLevel().chunkPacketBlockController.modifyBlocks(this, chunkPacketInfo); // Paper - Anti-Xray - Modify blocks
    }

    private ClientboundLevelChunkWithLightPacket(RegistryFriendlyByteBuf buffer) {
        this.x = buffer.readInt();
        this.z = buffer.readInt();
        this.chunkData = new ClientboundLevelChunkPacketData(buffer, this.x, this.z);
        this.lightData = new ClientboundLightUpdatePacketData(buffer, this.x, this.z);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(this.x);
        buffer.writeInt(this.z);
        this.chunkData.write(buffer);
        this.lightData.write(buffer);
    }

    @Override
    public PacketType<ClientboundLevelChunkWithLightPacket> type() {
        return GamePacketTypes.CLIENTBOUND_LEVEL_CHUNK_WITH_LIGHT;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        handler.handleLevelChunkWithLight(this);
    }

    public int getX() {
        return this.x;
    }

    public int getZ() {
        return this.z;
    }

    public ClientboundLevelChunkPacketData getChunkData() {
        return this.chunkData;
    }

    public ClientboundLightUpdatePacketData getLightData() {
        return this.lightData;
    }

    // Paper start - Handle oversized block entities in chunks
    @Override
    public java.util.List<Packet<?>> getExtraPackets() {
        return this.chunkData.getExtraPackets();
    }
    // Paper end - Handle oversized block entities in chunks
}
