package net.minecraft.server.level;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;

public abstract class SectionTracker extends DynamicGraphMinFixedPoint {
    protected SectionTracker(int firstQueuedLevel, int width, int height) {
        super(firstQueuedLevel, width, height);
    }

    @Override
    protected void checkNeighborsAfterUpdate(long pos, int level, boolean isDecreasing) {
        if (!isDecreasing || level < this.levelCount - 2) {
            for (int i = -1; i <= 1; i++) {
                for (int i1 = -1; i1 <= 1; i1++) {
                    for (int i2 = -1; i2 <= 1; i2++) {
                        long l = SectionPos.offset(pos, i, i1, i2);
                        if (l != pos) {
                            this.checkNeighbor(pos, l, level, isDecreasing);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected int getComputedLevel(long pos, long excludedSourcePos, int level) {
        int i = level;

        for (int i1 = -1; i1 <= 1; i1++) {
            for (int i2 = -1; i2 <= 1; i2++) {
                for (int i3 = -1; i3 <= 1; i3++) {
                    long l = SectionPos.offset(pos, i1, i2, i3);
                    if (l == pos) {
                        l = Long.MAX_VALUE;
                    }

                    if (l != excludedSourcePos) {
                        int i4 = this.computeLevelFromNeighbor(l, pos, this.getLevel(l));
                        if (i > i4) {
                            i = i4;
                        }

                        if (i == 0) {
                            return i;
                        }
                    }
                }
            }
        }

        return i;
    }

    @Override
    protected int computeLevelFromNeighbor(long startPos, long endPos, int startLevel) {
        return this.isSource(startPos) ? this.getLevelFromSource(endPos) : startLevel + 1;
    }

    protected abstract int getLevelFromSource(long pos);

    public void update(long pos, int level, boolean isDecreasing) {
        this.checkEdge(Long.MAX_VALUE, pos, level, isDecreasing);
    }
}
