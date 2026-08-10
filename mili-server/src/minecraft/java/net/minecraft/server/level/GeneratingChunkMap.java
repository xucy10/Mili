package net.minecraft.server.level;

import java.util.concurrent.CompletableFuture;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

public interface GeneratingChunkMap {
    GenerationChunkHolder acquireGeneration(long chunkPos);

    void releaseGeneration(GenerationChunkHolder chunk);

    CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder chunk, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache);

    ChunkGenerationTask scheduleGenerationTask(ChunkStatus targetStatus, ChunkPos pos);

    void runGenerationTasks();
}
