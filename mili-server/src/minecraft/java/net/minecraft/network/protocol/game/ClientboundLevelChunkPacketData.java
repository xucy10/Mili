package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

public class ClientboundLevelChunkPacketData {
    private static final StreamCodec<ByteBuf, Map<Heightmap.Types, long[]>> HEIGHTMAPS_STREAM_CODEC = ByteBufCodecs.map(
        i -> new EnumMap<>(Heightmap.Types.class), Heightmap.Types.STREAM_CODEC, ByteBufCodecs.LONG_ARRAY
    );
    private static final int TWO_MEGABYTES = 2097152;
    private final Map<Heightmap.Types, long[]> heightmaps;
    private final byte[] buffer;
    private final List<ClientboundLevelChunkPacketData.BlockEntityInfo> blockEntitiesData;
    // Paper start - Handle oversized block entities in chunks
    private final java.util.List<net.minecraft.network.protocol.Packet<?>> extraPackets = new java.util.ArrayList<>();
    private static final int BLOCK_ENTITY_LIMIT = Integer.getInteger("Paper.excessiveTELimit", 750);

    public List<net.minecraft.network.protocol.Packet<?>> getExtraPackets() {
        return this.extraPackets;
    }
    // Paper end - Handle oversized block entities in chunks

    // Paper start - Anti-Xray - Add chunk packet info
    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public ClientboundLevelChunkPacketData(LevelChunk levelChunk) {
        this(levelChunk, null);
    }
    public ClientboundLevelChunkPacketData(LevelChunk levelChunk, io.papermc.paper.antixray.ChunkPacketInfo<net.minecraft.world.level.block.state.BlockState> chunkPacketInfo) {
        // Paper end - Anti-Xray - Add chunk packet info
        this.heightmaps = levelChunk.getHeightmaps()
            .stream()
            .filter(entry1 -> entry1.getKey().sendToClient())
            .collect(Collectors.toMap(Entry::getKey, entry1 -> (long[])entry1.getValue().getRawData().clone()));
        this.buffer = new byte[calculateChunkSize(levelChunk)];
        // Paper start - Anti-Xray - Add chunk packet info
        if (chunkPacketInfo != null) {
            chunkPacketInfo.setBuffer(this.buffer);
        }
        extractChunkData(new FriendlyByteBuf(this.getWriteBuffer()), levelChunk, chunkPacketInfo);
        this.blockEntitiesData = Lists.newArrayList();
        int totalTileEntities = 0; // Paper - Handle oversized block entities in chunks

        for (Entry<BlockPos, BlockEntity> entry : levelChunk.getBlockEntities().entrySet()) {
            // Paper start - Handle oversized block entities in chunks
            if (++totalTileEntities > BLOCK_ENTITY_LIMIT) {
                net.minecraft.network.protocol.Packet<ClientGamePacketListener> packet = entry.getValue().getUpdatePacket();
                if (packet != null) {
                    this.extraPackets.add(packet);
                    continue;
                }
            }
            // Paper end - Handle oversized block entities in chunks
            this.blockEntitiesData.add(ClientboundLevelChunkPacketData.BlockEntityInfo.create(entry.getValue()));
        }
    }

    public ClientboundLevelChunkPacketData(RegistryFriendlyByteBuf buffer, int x, int z) {
        this.heightmaps = HEIGHTMAPS_STREAM_CODEC.decode(buffer);
        int varInt = buffer.readVarInt();
        if (varInt > 2097152) { // Paper - diff on change - if this changes, update PacketEncoder
            throw new RuntimeException("Chunk Packet trying to allocate too much memory on read.");
        } else {
            this.buffer = new byte[varInt];
            buffer.readBytes(this.buffer);
            this.blockEntitiesData = ClientboundLevelChunkPacketData.BlockEntityInfo.LIST_STREAM_CODEC.decode(buffer);
        }
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        HEIGHTMAPS_STREAM_CODEC.encode(buffer, this.heightmaps);
        buffer.writeVarInt(this.buffer.length);
        buffer.writeBytes(this.buffer);
        ClientboundLevelChunkPacketData.BlockEntityInfo.LIST_STREAM_CODEC.encode(buffer, this.blockEntitiesData);
    }

    private static int calculateChunkSize(LevelChunk chunk) {
        int i = 0;

        for (LevelChunkSection levelChunkSection : chunk.getSections()) {
            i += levelChunkSection.getSerializedSize();
        }

        return i;
    }

    private ByteBuf getWriteBuffer() {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(this.buffer);
        byteBuf.writerIndex(0);
        return byteBuf;
    }

    // Paper start - Anti-Xray - Add chunk packet info
    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public static void extractChunkData(FriendlyByteBuf buffer, LevelChunk chunk) {
        ClientboundLevelChunkPacketData.extractChunkData(buffer, chunk, null);
    }
    public static void extractChunkData(FriendlyByteBuf buffer, LevelChunk chunk, io.papermc.paper.antixray.ChunkPacketInfo<net.minecraft.world.level.block.state.BlockState> chunkPacketInfo) {
        int chunkSectionIndex = 0;
        for (LevelChunkSection levelChunkSection : chunk.getSections()) {
            levelChunkSection.write(buffer, chunkPacketInfo, chunkSectionIndex);
            chunkSectionIndex++;
            // Paper end  - Anti-Xray - Add chunk packet info
        }

        if (buffer.writerIndex() != buffer.capacity()) {
            throw new IllegalStateException("Didn't fill chunk buffer: expected " + buffer.capacity() + " bytes, got " + buffer.writerIndex());
        }
    }

    public Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> getBlockEntitiesTagsConsumer(int chunkX, int chunkZ) {
        return blockEntityTagOutput -> this.getBlockEntitiesTags(blockEntityTagOutput, chunkX, chunkZ);
    }

    private void getBlockEntitiesTags(ClientboundLevelChunkPacketData.BlockEntityTagOutput output, int chunkX, int chunkZ) {
        int i = 16 * chunkX;
        int i1 = 16 * chunkZ;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (ClientboundLevelChunkPacketData.BlockEntityInfo blockEntityInfo : this.blockEntitiesData) {
            int i2 = i + SectionPos.sectionRelative(blockEntityInfo.packedXZ >> 4);
            int i3 = i1 + SectionPos.sectionRelative(blockEntityInfo.packedXZ);
            mutableBlockPos.set(i2, blockEntityInfo.y, i3);
            output.accept(mutableBlockPos, blockEntityInfo.type, blockEntityInfo.tag);
        }
    }

    public FriendlyByteBuf getReadBuffer() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(this.buffer));
    }

    public Map<Heightmap.Types, long[]> getHeightmaps() {
        return this.heightmaps;
    }

    static class BlockEntityInfo {
        public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundLevelChunkPacketData.BlockEntityInfo> STREAM_CODEC = StreamCodec.ofMember(
            ClientboundLevelChunkPacketData.BlockEntityInfo::write, ClientboundLevelChunkPacketData.BlockEntityInfo::new
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, List<ClientboundLevelChunkPacketData.BlockEntityInfo>> LIST_STREAM_CODEC = STREAM_CODEC.apply(
            ByteBufCodecs.list()
        );
        final int packedXZ;
        final int y;
        final BlockEntityType<?> type;
        final @Nullable CompoundTag tag;

        private BlockEntityInfo(int packedXZ, int y, BlockEntityType<?> type, @Nullable CompoundTag tag) {
            this.packedXZ = packedXZ;
            this.y = y;
            this.type = type;
            this.tag = tag;
        }

        private BlockEntityInfo(RegistryFriendlyByteBuf buffer) {
            this.packedXZ = buffer.readByte();
            this.y = buffer.readShort();
            this.type = ByteBufCodecs.registry(Registries.BLOCK_ENTITY_TYPE).decode(buffer);
            this.tag = buffer.readNbt();
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeByte(this.packedXZ);
            buffer.writeShort(this.y);
            ByteBufCodecs.registry(Registries.BLOCK_ENTITY_TYPE).encode(buffer, this.type);
            buffer.writeNbt(this.tag);
        }

        static ClientboundLevelChunkPacketData.BlockEntityInfo create(BlockEntity blockEntity) {
            CompoundTag updateTag = blockEntity.getUpdateTag(blockEntity.getLevel().registryAccess());
            BlockPos blockPos = blockEntity.getBlockPos();
            int i = SectionPos.sectionRelative(blockPos.getX()) << 4 | SectionPos.sectionRelative(blockPos.getZ());
            blockEntity.sanitizeSentNbt(updateTag); // Paper - Sanitize sent data
            return new ClientboundLevelChunkPacketData.BlockEntityInfo(i, blockPos.getY(), blockEntity.getType(), updateTag.isEmpty() ? null : updateTag);
        }
    }

    @FunctionalInterface
    public interface BlockEntityTagOutput {
        void accept(BlockPos pos, BlockEntityType<?> type, @Nullable CompoundTag tag);
    }
}
