package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;

public abstract class ChunkSource implements LightChunkGetter, AutoCloseable {
    public @Nullable LevelChunk getChunk(int chunkX, int chunkZ, boolean load) {
        return (LevelChunk)this.getChunk(chunkX, chunkZ, ChunkStatus.FULL, load);
    }

    public @Nullable LevelChunk getChunkNow(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, false);
    }

    @Override
    public @Nullable LightChunk getChunkForLighting(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, false);
    }

    public boolean hasChunk(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
    }

    public abstract @Nullable ChunkAccess getChunk(int x, int z, ChunkStatus chunkStatus, boolean requireChunk);

    public abstract void tick(BooleanSupplier hasTimeLeft, boolean tickChunks);

    public void onSectionEmptinessChanged(int x, int y, int z, boolean isEmpty) {
    }

    public abstract String gatherStats();

    public abstract int getLoadedChunksCount();

    @Override
    public void close() throws IOException {
    }

    public abstract LevelLightEngine getLightEngine();

    public void setSpawnSettings(boolean spawnSettings) {
    }

    public boolean updateChunkForced(ChunkPos chunkPos, boolean add) {
        return false;
    }

    public LongSet getForceLoadedChunks() {
        return LongSet.of();
    }
}
