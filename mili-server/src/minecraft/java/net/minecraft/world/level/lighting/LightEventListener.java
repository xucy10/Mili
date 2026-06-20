package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

public interface LightEventListener {
    void checkBlock(BlockPos pos);

    boolean hasLightWork();

    int runLightUpdates();

    default void updateSectionStatus(BlockPos pos, boolean isQueueEmpty) {
        this.updateSectionStatus(SectionPos.of(pos), isQueueEmpty);
    }

    void updateSectionStatus(SectionPos pos, boolean isQueueEmpty);

    void setLightEnabled(ChunkPos chunkPos, boolean lightEnabled);

    void propagateLightSources(ChunkPos chunkPos);
}
