package net.minecraft.server.level;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;

public abstract class ChunkTracker extends DynamicGraphMinFixedPoint {
    protected ChunkTracker(int firstQueuedLevel, int width, int height) {
        super(firstQueuedLevel, width, height);
    }

    @Override
    protected boolean isSource(long pos) {
        return pos == ChunkPos.INVALID_CHUNK_POS;
    }

    @Override
    protected void checkNeighborsAfterUpdate(long pos, int level, boolean isDecreasing) {
        if (!isDecreasing || level < this.levelCount - 2) {
            ChunkPos chunkPos = new ChunkPos(pos);
            int i = chunkPos.x;
            int i1 = chunkPos.z;

            for (int i2 = -1; i2 <= 1; i2++) {
                for (int i3 = -1; i3 <= 1; i3++) {
                    long packedChunkPos = ChunkPos.asLong(i + i2, i1 + i3);
                    if (packedChunkPos != pos) {
                        this.checkNeighbor(pos, packedChunkPos, level, isDecreasing);
                    }
                }
            }
        }
    }

    @Override
    protected int getComputedLevel(long pos, long excludedSourcePos, int level) {
        int i = level;
        ChunkPos chunkPos = new ChunkPos(pos);
        int i1 = chunkPos.x;
        int i2 = chunkPos.z;

        for (int i3 = -1; i3 <= 1; i3++) {
            for (int i4 = -1; i4 <= 1; i4++) {
                long packedChunkPos = ChunkPos.asLong(i1 + i3, i2 + i4);
                if (packedChunkPos == pos) {
                    packedChunkPos = ChunkPos.INVALID_CHUNK_POS;
                }

                if (packedChunkPos != excludedSourcePos) {
                    int i5 = this.computeLevelFromNeighbor(packedChunkPos, pos, this.getLevel(packedChunkPos));
                    if (i > i5) {
                        i = i5;
                    }

                    if (i == 0) {
                        return i;
                    }
                }
            }
        }

        return i;
    }

    @Override
    protected int computeLevelFromNeighbor(long startPos, long endPos, int startLevel) {
        return startPos == ChunkPos.INVALID_CHUNK_POS ? this.getLevelFromSource(endPos) : startLevel + 1;
    }

    protected abstract int getLevelFromSource(long pos);

    public void update(long pos, int level, boolean isDecreasing) {
        this.checkEdge(ChunkPos.INVALID_CHUNK_POS, pos, level, isDecreasing);
    }
}
