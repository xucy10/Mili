package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;

public class ClientboundLightUpdatePacketData {
    private static final StreamCodec<ByteBuf, byte[]> DATA_LAYER_STREAM_CODEC = ByteBufCodecs.byteArray(2048);
    private final BitSet skyYMask;
    private final BitSet blockYMask;
    private final BitSet emptySkyYMask;
    private final BitSet emptyBlockYMask;
    private final List<byte[]> skyUpdates;
    private final List<byte[]> blockUpdates;

    public ClientboundLightUpdatePacketData(ChunkPos chunkPos, LevelLightEngine lightEngine, @Nullable BitSet skyLight, @Nullable BitSet blockLight) {
        this.skyYMask = new BitSet();
        this.blockYMask = new BitSet();
        this.emptySkyYMask = new BitSet();
        this.emptyBlockYMask = new BitSet();
        this.skyUpdates = Lists.newArrayList();
        this.blockUpdates = Lists.newArrayList();

        for (int i = 0; i < lightEngine.getLightSectionCount(); i++) {
            if (skyLight == null || skyLight.get(i)) {
                this.prepareSectionData(chunkPos, lightEngine, LightLayer.SKY, i, this.skyYMask, this.emptySkyYMask, this.skyUpdates);
            }

            if (blockLight == null || blockLight.get(i)) {
                this.prepareSectionData(chunkPos, lightEngine, LightLayer.BLOCK, i, this.blockYMask, this.emptyBlockYMask, this.blockUpdates);
            }
        }
    }

    public ClientboundLightUpdatePacketData(FriendlyByteBuf buffer, int x, int z) {
        this.skyYMask = buffer.readBitSet();
        this.blockYMask = buffer.readBitSet();
        this.emptySkyYMask = buffer.readBitSet();
        this.emptyBlockYMask = buffer.readBitSet();
        this.skyUpdates = buffer.readList(DATA_LAYER_STREAM_CODEC);
        this.blockUpdates = buffer.readList(DATA_LAYER_STREAM_CODEC);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBitSet(this.skyYMask);
        buffer.writeBitSet(this.blockYMask);
        buffer.writeBitSet(this.emptySkyYMask);
        buffer.writeBitSet(this.emptyBlockYMask);
        buffer.writeCollection(this.skyUpdates, DATA_LAYER_STREAM_CODEC);
        buffer.writeCollection(this.blockUpdates, DATA_LAYER_STREAM_CODEC);
    }

    private void prepareSectionData(
        ChunkPos chunkPos, LevelLightEngine levelLightEngine, LightLayer lightLayer, int index, BitSet skyLight, BitSet blockLight, List<byte[]> updates
    ) {
        DataLayer dataLayerData = levelLightEngine.getLayerListener(lightLayer)
            .getDataLayerData(SectionPos.of(chunkPos, levelLightEngine.getMinLightSection() + index));
        if (dataLayerData != null) {
            if (dataLayerData.isEmpty()) {
                blockLight.set(index);
            } else {
                skyLight.set(index);
                updates.add(dataLayerData.copy().getData());
            }
        }
    }

    public BitSet getSkyYMask() {
        return this.skyYMask;
    }

    public BitSet getEmptySkyYMask() {
        return this.emptySkyYMask;
    }

    public List<byte[]> getSkyUpdates() {
        return this.skyUpdates;
    }

    public BitSet getBlockYMask() {
        return this.blockYMask;
    }

    public BitSet getEmptyBlockYMask() {
        return this.emptyBlockYMask;
    }

    public List<byte[]> getBlockUpdates() {
        return this.blockUpdates;
    }
}
