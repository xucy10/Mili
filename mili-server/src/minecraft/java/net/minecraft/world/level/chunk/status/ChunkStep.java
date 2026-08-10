package net.minecraft.world.level.chunk.status;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.jspecify.annotations.Nullable;

// Paper start - rewerite chunk system - convert record to class
public final class ChunkStep implements ca.spottedleaf.moonrise.patches.chunk_system.status.ChunkSystemChunkStep { // Paper - rewrite chunk system
    private final ChunkStatus targetStatus;
    private final ChunkDependencies directDependencies;
    private final ChunkDependencies accumulatedDependencies;
    private final int blockStateWriteRadius;
    private final ChunkStatusTask task;

    private final ChunkStatus[] byRadius; // Paper - rewrite chunk system

    public ChunkStep(
        ChunkStatus targetStatus, ChunkDependencies directDependencies, ChunkDependencies accumulatedDependencies, int blockStateWriteRadius, ChunkStatusTask task
    ) {
        this.targetStatus = targetStatus;
        this.directDependencies = directDependencies;
        this.accumulatedDependencies = accumulatedDependencies;
        this.blockStateWriteRadius = blockStateWriteRadius;
        this.task = task;

        // Paper start - rewrite chunk system
        this.byRadius = new ChunkStatus[this.getAccumulatedRadiusOf(ChunkStatus.EMPTY) + 1];
        this.byRadius[0] = targetStatus.getParent();

        for (ChunkStatus status = targetStatus.getParent(); status != ChunkStatus.EMPTY; status = status.getParent()) {
            final int radius = this.getAccumulatedRadiusOf(status);

            for (int j = 0; j <= radius; ++j) {
                if (this.byRadius[j] == null) {
                    this.byRadius[j] = status;
                }
            }
        }
        // Paper end - rewrite chunk system
    }

    // Paper start - rewrite chunk system
    @Override
    public final ChunkStatus moonrise$getRequiredStatusAtRadius(final int radius) {
        return this.byRadius[radius];
    }
    // Paper end - rewrite chunk system

    // Paper start - rewerite chunk system - convert record to class

    public int getAccumulatedRadiusOf(ChunkStatus status) {
        return status == this.targetStatus ? 0 : this.accumulatedDependencies.getRadiusOf(status);
    }

    public CompletableFuture<ChunkAccess> apply(WorldGenContext worldGenContext, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        su.plo.matter.Globals.setupGlobals(worldGenContext.level()); // Leaf - Matter - Secure Seed
        if (chunk.getPersistedStatus().isBefore(this.targetStatus)) {
            ProfiledDuration profiledDuration = JvmProfiler.INSTANCE
                .onChunkGenerate(chunk.getPos(), worldGenContext.level().dimension(), this.targetStatus.getName());
            return this.task.doWork(worldGenContext, this, cache, chunk).thenApply(chunkAccess -> this.completeChunkGeneration(chunkAccess, profiledDuration));
        } else {
            return this.task.doWork(worldGenContext, this, cache, chunk);
        }
    }

    private ChunkAccess completeChunkGeneration(ChunkAccess chunk, @Nullable ProfiledDuration duration) {
        if (chunk instanceof ProtoChunk protoChunk && protoChunk.getPersistedStatus().isBefore(this.targetStatus)) {
            protoChunk.setPersistedStatus(this.targetStatus);
        }

        if (duration != null) {
            duration.finish(true);
        }

        return chunk;
    }

    // Paper start - rewerite chunk system - convert record to class
    public ChunkStatus targetStatus() {
        return targetStatus;
    }

    public ChunkDependencies directDependencies() {
        return directDependencies;
    }

    public ChunkDependencies accumulatedDependencies() {
        return accumulatedDependencies;
    }

    public int blockStateWriteRadius() {
        return blockStateWriteRadius;
    }

    public ChunkStatusTask task() {
        return task;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (net.minecraft.world.level.chunk.status.ChunkStep) obj;
        return java.util.Objects.equals(this.targetStatus, that.targetStatus) &&
            java.util.Objects.equals(this.directDependencies, that.directDependencies) &&
            java.util.Objects.equals(this.accumulatedDependencies, that.accumulatedDependencies) &&
            this.blockStateWriteRadius == that.blockStateWriteRadius &&
            java.util.Objects.equals(this.task, that.task);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(targetStatus, directDependencies, accumulatedDependencies, blockStateWriteRadius, task);
    }

    @Override
    public String toString() {
        return "ChunkStep[" +
            "targetStatus=" + targetStatus + ", " +
            "directDependencies=" + directDependencies + ", " +
            "accumulatedDependencies=" + accumulatedDependencies + ", " +
            "blockStateWriteRadius=" + blockStateWriteRadius + ", " +
            "task=" + task + ']';
    }
    // Paper end - rewerite chunk system - convert record to class


    public static class Builder {
        private final ChunkStatus status;
        private final @Nullable ChunkStep parent;
        private ChunkStatus[] directDependenciesByRadius;
        private int blockStateWriteRadius = -1;
        private ChunkStatusTask task = ChunkStatusTasks::passThrough;

        protected Builder(ChunkStatus status) {
            if (status.getParent() != status) {
                throw new IllegalArgumentException("Not starting with the first status: " + status);
            } else {
                this.status = status;
                this.parent = null;
                this.directDependenciesByRadius = new ChunkStatus[0];
            }
        }

        protected Builder(ChunkStatus status, ChunkStep parent) {
            if (parent.targetStatus.getIndex() != status.getIndex() - 1) {
                throw new IllegalArgumentException("Out of order status: " + status);
            } else {
                this.status = status;
                this.parent = parent;
                this.directDependenciesByRadius = new ChunkStatus[]{parent.targetStatus};
            }
        }

        public ChunkStep.Builder addRequirement(ChunkStatus status, int radius) {
            if (status.isOrAfter(this.status)) {
                throw new IllegalArgumentException("Status " + status + " can not be required by " + this.status);
            } else {
                ChunkStatus[] chunkStatuss = this.directDependenciesByRadius;
                int i = radius + 1;
                if (i > chunkStatuss.length) {
                    this.directDependenciesByRadius = new ChunkStatus[i];
                    Arrays.fill(this.directDependenciesByRadius, status);
                }

                for (int i1 = 0; i1 < Math.min(i, chunkStatuss.length); i1++) {
                    this.directDependenciesByRadius[i1] = ChunkStatus.max(chunkStatuss[i1], status);
                }

                return this;
            }
        }

        public ChunkStep.Builder blockStateWriteRadius(int blockStateWriteRadius) {
            this.blockStateWriteRadius = blockStateWriteRadius;
            return this;
        }

        public ChunkStep.Builder setTask(ChunkStatusTask task) {
            this.task = task;
            return this;
        }

        public ChunkStep build() {
            return new ChunkStep(
                this.status,
                new ChunkDependencies(ImmutableList.copyOf(this.directDependenciesByRadius)),
                new ChunkDependencies(ImmutableList.copyOf(this.buildAccumulatedDependencies())),
                this.blockStateWriteRadius,
                this.task
            );
        }

        private ChunkStatus[] buildAccumulatedDependencies() {
            if (this.parent == null) {
                return this.directDependenciesByRadius;
            } else {
                int radiusOfParent = this.getRadiusOfParent(this.parent.targetStatus);
                ChunkDependencies chunkDependencies = this.parent.accumulatedDependencies;
                ChunkStatus[] chunkStatuss = new ChunkStatus[Math.max(radiusOfParent + chunkDependencies.size(), this.directDependenciesByRadius.length)];

                for (int i = 0; i < chunkStatuss.length; i++) {
                    int i1 = i - radiusOfParent;
                    if (i1 < 0 || i1 >= chunkDependencies.size()) {
                        chunkStatuss[i] = this.directDependenciesByRadius[i];
                    } else if (i >= this.directDependenciesByRadius.length) {
                        chunkStatuss[i] = chunkDependencies.get(i1);
                    } else {
                        chunkStatuss[i] = ChunkStatus.max(this.directDependenciesByRadius[i], chunkDependencies.get(i1));
                    }
                }

                return chunkStatuss;
            }
        }

        private int getRadiusOfParent(ChunkStatus status) {
            for (int i = this.directDependenciesByRadius.length - 1; i >= 0; i--) {
                if (this.directDependenciesByRadius[i].isOrAfter(status)) {
                    return i;
                }
            }

            return 0;
        }
    }
}
