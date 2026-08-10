package net.minecraft.server.level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.Zone;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

public class ChunkGenerationTask {
    private final GeneratingChunkMap chunkMap;
    private final ChunkPos pos;
    private @Nullable ChunkStatus scheduledStatus = null;
    public final ChunkStatus targetStatus;
    private volatile boolean markedForCancellation;
    private final List<CompletableFuture<ChunkResult<ChunkAccess>>> scheduledLayer = new ArrayList<>();
    private final StaticCache2D<GenerationChunkHolder> cache;
    private boolean needsGeneration;

    private ChunkGenerationTask(GeneratingChunkMap chunkMap, ChunkStatus targetStatus, ChunkPos pos, StaticCache2D<GenerationChunkHolder> cache) {
        this.chunkMap = chunkMap;
        this.targetStatus = targetStatus;
        this.pos = pos;
        this.cache = cache;
    }

    public static ChunkGenerationTask create(GeneratingChunkMap chunkMap, ChunkStatus targetStatus, ChunkPos pos) {
        int accumulatedRadiusOf = ChunkPyramid.GENERATION_PYRAMID.getStepTo(targetStatus).getAccumulatedRadiusOf(ChunkStatus.EMPTY);
        StaticCache2D<GenerationChunkHolder> staticCache2D = StaticCache2D.create(
            pos.x, pos.z, accumulatedRadiusOf, (x, z) -> chunkMap.acquireGeneration(ChunkPos.asLong(x, z))
        );
        return new ChunkGenerationTask(chunkMap, targetStatus, pos, staticCache2D);
    }

    public @Nullable CompletableFuture<?> runUntilWait() {
        while (true) {
            CompletableFuture<?> completableFuture = this.waitForScheduledLayer();
            if (completableFuture != null) {
                return completableFuture;
            }

            if (this.markedForCancellation || this.scheduledStatus == this.targetStatus) {
                this.releaseClaim();
                return null;
            }

            this.scheduleNextLayer();
        }
    }

    private void scheduleNextLayer() {
        ChunkStatus chunkStatus;
        if (this.scheduledStatus == null) {
            chunkStatus = ChunkStatus.EMPTY;
        } else if (!this.needsGeneration && this.scheduledStatus == ChunkStatus.EMPTY && !this.canLoadWithoutGeneration()) {
            this.needsGeneration = true;
            chunkStatus = ChunkStatus.EMPTY;
        } else {
            chunkStatus = ChunkStatus.getStatusList().get(this.scheduledStatus.getIndex() + 1);
        }

        this.scheduleLayer(chunkStatus, this.needsGeneration);
        this.scheduledStatus = chunkStatus;
    }

    public void markForCancellation() {
        this.markedForCancellation = true;
    }

    private void releaseClaim() {
        GenerationChunkHolder generationChunkHolder = this.cache.get(this.pos.x, this.pos.z);
        generationChunkHolder.removeTask(this);
        this.cache.forEach(this.chunkMap::releaseGeneration);
    }

    private boolean canLoadWithoutGeneration() {
        if (this.targetStatus == ChunkStatus.EMPTY) {
            return true;
        } else {
            ChunkStatus persistedStatus = this.cache.get(this.pos.x, this.pos.z).getPersistedStatus();
            if (persistedStatus != null && !persistedStatus.isBefore(this.targetStatus)) {
                ChunkDependencies chunkDependencies = ChunkPyramid.LOADING_PYRAMID.getStepTo(this.targetStatus).accumulatedDependencies();
                int radius = chunkDependencies.getRadius();

                for (int i = this.pos.x - radius; i <= this.pos.x + radius; i++) {
                    for (int i1 = this.pos.z - radius; i1 <= this.pos.z + radius; i1++) {
                        int chessboardDistance = this.pos.getChessboardDistance(i, i1);
                        ChunkStatus chunkStatus = chunkDependencies.get(chessboardDistance);
                        ChunkStatus persistedStatus1 = this.cache.get(i, i1).getPersistedStatus();
                        if (persistedStatus1 == null || persistedStatus1.isBefore(chunkStatus)) {
                            return false;
                        }
                    }
                }

                return true;
            } else {
                return false;
            }
        }
    }

    public GenerationChunkHolder getCenter() {
        return this.cache.get(this.pos.x, this.pos.z);
    }

    private void scheduleLayer(ChunkStatus status, boolean needsGeneration) {
        try (Zone zone = Profiler.get().zone("scheduleLayer")) {
            zone.addText(status::getName);
            int radiusForLayer = this.getRadiusForLayer(status, needsGeneration);

            for (int i = this.pos.x - radiusForLayer; i <= this.pos.x + radiusForLayer; i++) {
                for (int i1 = this.pos.z - radiusForLayer; i1 <= this.pos.z + radiusForLayer; i1++) {
                    GenerationChunkHolder generationChunkHolder = this.cache.get(i, i1);
                    if (this.markedForCancellation || !this.scheduleChunkInLayer(status, needsGeneration, generationChunkHolder)) {
                        return;
                    }
                }
            }
        }
    }

    private int getRadiusForLayer(ChunkStatus status, boolean needsGeneration) {
        ChunkPyramid chunkPyramid = needsGeneration ? ChunkPyramid.GENERATION_PYRAMID : ChunkPyramid.LOADING_PYRAMID;
        return chunkPyramid.getStepTo(this.targetStatus).getAccumulatedRadiusOf(status);
    }

    private boolean scheduleChunkInLayer(ChunkStatus status, boolean needsGeneration, GenerationChunkHolder chunk) {
        ChunkStatus persistedStatus = chunk.getPersistedStatus();
        boolean flag = persistedStatus != null && status.isAfter(persistedStatus);
        ChunkPyramid chunkPyramid = flag ? ChunkPyramid.GENERATION_PYRAMID : ChunkPyramid.LOADING_PYRAMID;
        if (flag && !needsGeneration) {
            throw new IllegalStateException("Can't load chunk, but didn't expect to need to generate");
        } else {
            CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = chunk.applyStep(chunkPyramid.getStepTo(status), this.chunkMap, this.cache);
            ChunkResult<ChunkAccess> chunkResult = completableFuture.getNow(null);
            if (chunkResult == null) {
                this.scheduledLayer.add(completableFuture);
                return true;
            } else if (chunkResult.isSuccess()) {
                return true;
            } else {
                this.markForCancellation();
                return false;
            }
        }
    }

    private @Nullable CompletableFuture<?> waitForScheduledLayer() {
        while (!this.scheduledLayer.isEmpty()) {
            CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.scheduledLayer.getLast();
            ChunkResult<ChunkAccess> chunkResult = completableFuture.getNow(null);
            if (chunkResult == null) {
                return completableFuture;
            }

            this.scheduledLayer.removeLast();
            if (!chunkResult.isSuccess()) {
                this.markForCancellation();
            }
        }

        return null;
    }
}
