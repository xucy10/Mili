package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.function.LongPredicate;
import net.minecraft.util.Mth;

public abstract class DynamicGraphMinFixedPoint {
    public static final long SOURCE = Long.MAX_VALUE;
    private static final int NO_COMPUTED_LEVEL = 255;
    protected final int levelCount;
    private final LeveledPriorityQueue priorityQueue;
    private final Long2ByteMap computedLevels;
    private volatile boolean hasWork;

    protected DynamicGraphMinFixedPoint(int firstQueuedLevel, int width, final int height) {
        if (firstQueuedLevel >= 254) {
            throw new IllegalArgumentException("Level count must be < 254.");
        } else {
            this.levelCount = firstQueuedLevel;
            this.priorityQueue = new LeveledPriorityQueue(firstQueuedLevel, width);
            this.computedLevels = new Long2ByteOpenHashMap(height, 0.5F) {
                @Override
                protected void rehash(int newSize) {
                    if (newSize > height) {
                        super.rehash(newSize);
                    }
                }
            };
            this.computedLevels.defaultReturnValue((byte)-1);
        }
    }

    protected void removeFromQueue(long position) {
        int i = this.computedLevels.remove(position) & 255;
        if (i != 255) {
            int level = this.getLevel(position);
            int i1 = this.calculatePriority(level, i);
            this.priorityQueue.dequeue(position, i1, this.levelCount);
            this.hasWork = !this.priorityQueue.isEmpty();
        }
    }

    public void removeIf(LongPredicate predicate) {
        LongList list = new LongArrayList();
        this.computedLevels.keySet().forEach(value -> {
            if (predicate.test(value)) {
                list.add(value);
            }
        });
        list.forEach(this::removeFromQueue);
    }

    private int calculatePriority(int oldLevel, int newLevel) {
        return Math.min(Math.min(oldLevel, newLevel), this.levelCount - 1);
    }

    protected void checkNode(long pos) {
        this.checkEdge(pos, pos, this.levelCount - 1, false);
    }

    protected void checkEdge(long fromPos, long toPos, int newLevel, boolean isDecreasing) {
        this.checkEdge(fromPos, toPos, newLevel, this.getLevel(toPos), this.computedLevels.get(toPos) & 255, isDecreasing);
        this.hasWork = !this.priorityQueue.isEmpty();
    }

    private void checkEdge(long fromPos, long toPos, int newLevel, int previousLevel, int propagationLevel, boolean isDecreasing) {
        if (!this.isSource(toPos)) {
            newLevel = Mth.clamp(newLevel, 0, this.levelCount - 1);
            previousLevel = Mth.clamp(previousLevel, 0, this.levelCount - 1);
            boolean flag = propagationLevel == 255;
            if (flag) {
                propagationLevel = previousLevel;
            }

            int min;
            if (isDecreasing) {
                min = Math.min(propagationLevel, newLevel);
            } else {
                min = Mth.clamp(this.getComputedLevel(toPos, fromPos, newLevel), 0, this.levelCount - 1);
            }

            int i = this.calculatePriority(previousLevel, propagationLevel);
            if (previousLevel != min) {
                int i1 = this.calculatePriority(previousLevel, min);
                if (i != i1 && !flag) {
                    this.priorityQueue.dequeue(toPos, i, i1);
                }

                this.priorityQueue.enqueue(toPos, i1);
                this.computedLevels.put(toPos, (byte)min);
            } else if (!flag) {
                this.priorityQueue.dequeue(toPos, i, this.levelCount);
                this.computedLevels.remove(toPos);
            }
        }
    }

    protected final void checkNeighbor(long fromPos, long toPos, int sourceLevel, boolean isDecreasing) {
        int i = this.computedLevels.get(toPos) & 255;
        int i1 = Mth.clamp(this.computeLevelFromNeighbor(fromPos, toPos, sourceLevel), 0, this.levelCount - 1);
        if (isDecreasing) {
            this.checkEdge(fromPos, toPos, i1, this.getLevel(toPos), i, isDecreasing);
        } else {
            boolean flag = i == 255;
            int i2;
            if (flag) {
                i2 = Mth.clamp(this.getLevel(toPos), 0, this.levelCount - 1);
            } else {
                i2 = i;
            }

            if (i1 == i2) {
                this.checkEdge(fromPos, toPos, this.levelCount - 1, flag ? i2 : this.getLevel(toPos), i, isDecreasing);
            }
        }
    }

    protected final boolean hasWork() {
        return this.hasWork;
    }

    protected final int runUpdates(int toUpdateCount) {
        if (this.priorityQueue.isEmpty()) {
            return toUpdateCount;
        } else {
            while (!this.priorityQueue.isEmpty() && toUpdateCount > 0) {
                toUpdateCount--;
                long l = this.priorityQueue.removeFirstLong();
                int i = Mth.clamp(this.getLevel(l), 0, this.levelCount - 1);
                int i1 = this.computedLevels.remove(l) & 255;
                if (i1 < i) {
                    this.setLevel(l, i1);
                    this.checkNeighborsAfterUpdate(l, i1, true);
                } else if (i1 > i) {
                    this.setLevel(l, this.levelCount - 1);
                    if (i1 != this.levelCount - 1) {
                        this.priorityQueue.enqueue(l, this.calculatePriority(this.levelCount - 1, i1));
                        this.computedLevels.put(l, (byte)i1);
                    }

                    this.checkNeighborsAfterUpdate(l, i, false);
                }
            }

            this.hasWork = !this.priorityQueue.isEmpty();
            return toUpdateCount;
        }
    }

    public int getQueueSize() {
        return this.computedLevels.size();
    }

    protected boolean isSource(long pos) {
        return pos == Long.MAX_VALUE;
    }

    protected abstract int getComputedLevel(long pos, long excludedSourcePos, int level);

    protected abstract void checkNeighborsAfterUpdate(long pos, int level, boolean isDecreasing);

    protected abstract int getLevel(long chunkPos);

    protected abstract void setLevel(long chunkPos, int level);

    protected abstract int computeLevelFromNeighbor(long startPos, long endPos, int startLevel);
}
