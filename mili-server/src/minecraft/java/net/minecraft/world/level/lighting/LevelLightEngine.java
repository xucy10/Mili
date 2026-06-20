package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.jspecify.annotations.Nullable;

public class LevelLightEngine implements LightEventListener, ca.spottedleaf.moonrise.patches.starlight.light.StarLightLightingProvider { // Paper - rewrite chunk system
    public static final int LIGHT_SECTION_PADDING = 1;
    public static final LevelLightEngine EMPTY = new LevelLightEngine();
    protected final LevelHeightAccessor levelHeightAccessor;
    // Paper start - rewrite chunk system
    protected final ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface lightEngine;

    @Override
    public final ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface starlight$getLightEngine() {
        return this.lightEngine;
    }

    @Override
    public void starlight$clientUpdateLight(final LightLayer lightType, final SectionPos pos,
                                            final DataLayer nibble, final boolean trustEdges) {
        throw new IllegalStateException("This hook is for the CLIENT ONLY"); // Paper - not implemented on server
    }

    @Override
    public void starlight$clientRemoveLightData(final ChunkPos chunkPos) {
        throw new IllegalStateException("This hook is for the CLIENT ONLY"); // Paper - not implemented on server
    }

    @Override
    public void starlight$clientChunkLoad(final ChunkPos pos, final net.minecraft.world.level.chunk.LevelChunk chunk) {
        throw new IllegalStateException("This hook is for the CLIENT ONLY"); // Paper - not implemented on server
    }
    // Paper end - rewrite chunk system

    public LevelLightEngine(LightChunkGetter lightChunkGetter, boolean blockLight, boolean skyLight) {
        this.levelHeightAccessor = lightChunkGetter.getLevel();
        // Paper start - rewrite chunk system
        if (lightChunkGetter.getLevel() instanceof net.minecraft.world.level.Level) {
            this.lightEngine = new ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface(lightChunkGetter, skyLight, blockLight, (LevelLightEngine)(Object)this);
        } else {
            this.lightEngine = new ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface(null, skyLight, blockLight, (LevelLightEngine)(Object)this);
        }
        // Paper end - rewrite chunk system
    }

    private LevelLightEngine() {
        this.levelHeightAccessor = LevelHeightAccessor.create(0, 0);
        // Paper start - rewrite chunk system
        this.lightEngine = new ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface(null, false, false, (LevelLightEngine)(Object)this);
        // Paper end - rewrite chunk system
    }

    @Override
    public void checkBlock(BlockPos pos) {
        this.lightEngine.blockChange(pos.immutable()); // Paper - rewrite chunk system
    }

    @Override
    public boolean hasLightWork() {
        return this.lightEngine.hasUpdates(); // Paper - rewrite chunk system
    }

    @Override
    public int runLightUpdates() {
        final boolean hadUpdates = this.hasLightWork();
        this.lightEngine.propagateChanges();
        return hadUpdates ? 1 : 0; // Paper - rewrite chunk system
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        this.lightEngine.sectionChange(pos, isEmpty); // Paper - rewrite chunk system
    }

    @Override
    public void setLightEnabled(ChunkPos chunkPos, boolean lightEnabled) {
        // Paper - rewrite chunk system
    }

    @Override
    public void propagateLightSources(ChunkPos chunkPos) {
        // Paper - rewrite chunk system
    }

    public LayerLightEventListener getLayerListener(LightLayer lightLayer) {
        return lightLayer == LightLayer.BLOCK ? this.lightEngine.getBlockReader() : this.lightEngine.getSkyReader(); // Paper - rewrite chunk system
    }

    public String getDebugData(LightLayer lightLayer, SectionPos sectionPos) {
        return "n/a"; // Paper - rewrite chunk system
    }

    public LayerLightSectionStorage.SectionType getDebugSectionType(LightLayer lightLayer, SectionPos sectionPos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void queueSectionData(LightLayer lightLayer, SectionPos sectionPos, @Nullable DataLayer dataLayer) {
        // Paper - rewrite chunk system
    }

    public void retainData(ChunkPos pos, boolean retain) {
        // Paper - rewrite chunk system
    }

    public int getRawBrightness(BlockPos pos, int amount) {
        return this.lightEngine.getRawBrightness(pos, amount); // Paper - rewrite chunk system
    }

    public boolean lightOnInColumn(long columnPos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system // Paper - not implemented on server
    }

    public int getLightSectionCount() {
        return this.levelHeightAccessor.getSectionsCount() + 2;
    }

    public int getMinLightSection() {
        return this.levelHeightAccessor.getMinSectionY() - 1;
    }

    public int getMaxLightSection() {
        return this.getMinLightSection() + this.getLightSectionCount();
    }
}
