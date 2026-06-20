package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.DataLayer;
import org.jspecify.annotations.Nullable;

public interface LayerLightEventListener extends LightEventListener {
    @Nullable DataLayer getDataLayerData(SectionPos sectionPos);

    int getLightValue(BlockPos pos);

    public static enum DummyLightLayerEventListener implements LayerLightEventListener {
        INSTANCE;

        @Override
        public @Nullable DataLayer getDataLayerData(SectionPos sectionPos) {
            return null;
        }

        @Override
        public int getLightValue(BlockPos pos) {
            return 0;
        }

        @Override
        public void checkBlock(BlockPos pos) {
        }

        @Override
        public boolean hasLightWork() {
            return false;
        }

        @Override
        public int runLightUpdates() {
            return 0;
        }

        @Override
        public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        }

        @Override
        public void setLightEnabled(ChunkPos chunkPos, boolean lightEnabled) {
        }

        @Override
        public void propagateLightSources(ChunkPos chunkPos) {
        }
    }
}
