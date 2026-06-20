package net.minecraft.server.level;

import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

public class ChunkLevel {
    public static final int FULL_CHUNK_LEVEL = 33; // Paper - public
    public static final int BLOCK_TICKING_LEVEL = 32;
    public static final int ENTITY_TICKING_LEVEL = 31;
    private static final ChunkStep FULL_CHUNK_STEP = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FULL);
    public static final int RADIUS_AROUND_FULL_CHUNK = FULL_CHUNK_STEP.accumulatedDependencies().getRadius();
    public static final int MAX_LEVEL = 33 + RADIUS_AROUND_FULL_CHUNK;

    public static @Nullable ChunkStatus generationStatus(int level) {
        return getStatusAroundFullChunk(level - 33, null);
    }

    @Contract("_,!null->!null;_,_->_")
    public static @Nullable ChunkStatus getStatusAroundFullChunk(int distance, @Nullable ChunkStatus chunkStatus) {
        if (distance > RADIUS_AROUND_FULL_CHUNK) {
            return chunkStatus;
        } else {
            return distance <= 0 ? ChunkStatus.FULL : FULL_CHUNK_STEP.accumulatedDependencies().get(distance);
        }
    }

    public static ChunkStatus getStatusAroundFullChunk(int distance) {
        return getStatusAroundFullChunk(distance, ChunkStatus.EMPTY);
    }

    public static int byStatus(ChunkStatus status) {
        return 33 + FULL_CHUNK_STEP.getAccumulatedRadiusOf(status);
    }

    public static FullChunkStatus fullStatus(int level) {
        if (level <= 31) {
            return FullChunkStatus.ENTITY_TICKING;
        } else if (level <= 32) {
            return FullChunkStatus.BLOCK_TICKING;
        } else {
            return level <= 33 ? FullChunkStatus.FULL : FullChunkStatus.INACCESSIBLE;
        }
    }

    public static int byStatus(FullChunkStatus status) {
        return switch (status) {
            case INACCESSIBLE -> MAX_LEVEL;
            case FULL -> 33;
            case BLOCK_TICKING -> 32;
            case ENTITY_TICKING -> 31;
        };
    }

    public static boolean isEntityTicking(int level) {
        return level <= 31;
    }

    public static boolean isBlockTicking(int level) {
        return level <= 32;
    }

    public static boolean isLoaded(int level) {
        return level <= MAX_LEVEL;
    }
}
