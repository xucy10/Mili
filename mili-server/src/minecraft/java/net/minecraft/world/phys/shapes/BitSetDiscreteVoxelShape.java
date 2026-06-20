package net.minecraft.world.phys.shapes;

import java.util.BitSet;
import net.minecraft.core.Direction;

public final class BitSetDiscreteVoxelShape extends DiscreteVoxelShape {
    public final BitSet storage; // Paper - optimise collisions - public
    public int xMin; // Paper - optimise collisions - public
    public int yMin; // Paper - optimise collisions - public
    public int zMin; // Paper - optimise collisions - public
    public int xMax; // Paper - optimise collisions - public
    public int yMax; // Paper - optimise collisions - public
    public int zMax; // Paper - optimise collisions - public

    public BitSetDiscreteVoxelShape(int xSize, int ySize, int zSize) {
        super(xSize, ySize, zSize);
        this.storage = new BitSet(xSize * ySize * zSize);
        this.xMin = xSize;
        this.yMin = ySize;
        this.zMin = zSize;
    }

    public static BitSetDiscreteVoxelShape withFilledBounds(int x, int y, int z, int xMin, int yMin, int zMin, int xMax, int yMax, int zMax) {
        BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = new BitSetDiscreteVoxelShape(x, y, z);
        bitSetDiscreteVoxelShape.xMin = xMin;
        bitSetDiscreteVoxelShape.yMin = yMin;
        bitSetDiscreteVoxelShape.zMin = zMin;
        bitSetDiscreteVoxelShape.xMax = xMax;
        bitSetDiscreteVoxelShape.yMax = yMax;
        bitSetDiscreteVoxelShape.zMax = zMax;

        for (int i = xMin; i < xMax; i++) {
            for (int i1 = yMin; i1 < yMax; i1++) {
                for (int i2 = zMin; i2 < zMax; i2++) {
                    bitSetDiscreteVoxelShape.fillUpdateBounds(i, i1, i2, false);
                }
            }
        }

        return bitSetDiscreteVoxelShape;
    }

    public BitSetDiscreteVoxelShape(DiscreteVoxelShape shape) {
        super(shape.xSize, shape.ySize, shape.zSize);
        if (shape instanceof BitSetDiscreteVoxelShape) {
            this.storage = (BitSet)((BitSetDiscreteVoxelShape)shape).storage.clone();
        } else {
            this.storage = new BitSet(this.xSize * this.ySize * this.zSize);

            for (int i = 0; i < this.xSize; i++) {
                for (int i1 = 0; i1 < this.ySize; i1++) {
                    for (int i2 = 0; i2 < this.zSize; i2++) {
                        if (shape.isFull(i, i1, i2)) {
                            this.storage.set(this.getIndex(i, i1, i2));
                        }
                    }
                }
            }
        }

        this.xMin = shape.firstFull(Direction.Axis.X);
        this.yMin = shape.firstFull(Direction.Axis.Y);
        this.zMin = shape.firstFull(Direction.Axis.Z);
        this.xMax = shape.lastFull(Direction.Axis.X);
        this.yMax = shape.lastFull(Direction.Axis.Y);
        this.zMax = shape.lastFull(Direction.Axis.Z);
    }

    protected int getIndex(int x, int y, int z) {
        return (x * this.ySize + y) * this.zSize + z;
    }

    @Override
    public boolean isFull(int x, int y, int z) {
        return this.storage.get(this.getIndex(x, y, z));
    }

    private void fillUpdateBounds(int x, int y, int z, boolean update) {
        this.storage.set(this.getIndex(x, y, z));
        if (update) {
            this.xMin = Math.min(this.xMin, x);
            this.yMin = Math.min(this.yMin, y);
            this.zMin = Math.min(this.zMin, z);
            this.xMax = Math.max(this.xMax, x + 1);
            this.yMax = Math.max(this.yMax, y + 1);
            this.zMax = Math.max(this.zMax, z + 1);
        }
    }

    @Override
    public void fill(int x, int y, int z) {
        this.fillUpdateBounds(x, y, z, true);
    }

    @Override
    public boolean isEmpty() {
        return this.storage.isEmpty();
    }

    @Override
    public int firstFull(Direction.Axis axis) {
        return axis.choose(this.xMin, this.yMin, this.zMin);
    }

    @Override
    public int lastFull(Direction.Axis axis) {
        return axis.choose(this.xMax, this.yMax, this.zMax);
    }

    static BitSetDiscreteVoxelShape join(
        DiscreteVoxelShape mainShape, DiscreteVoxelShape secondaryShape, IndexMerger mergerX, IndexMerger mergerY, IndexMerger mergerZ, BooleanOp operator
    ) {
        BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = new BitSetDiscreteVoxelShape(mergerX.size() - 1, mergerY.size() - 1, mergerZ.size() - 1);
        int[] ints = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        mergerX.forMergedIndexes((x1, x2, x3) -> {
            boolean[] flags = new boolean[]{false};
            mergerY.forMergedIndexes((y1, y2, y3) -> {
                boolean[] flags1 = new boolean[]{false};
                mergerZ.forMergedIndexes((z1, z2, z3) -> {
                    if (operator.apply(mainShape.isFullWide(x1, y1, z1), secondaryShape.isFullWide(x2, y2, z2))) {
                        bitSetDiscreteVoxelShape.storage.set(bitSetDiscreteVoxelShape.getIndex(x3, y3, z3));
                        ints[2] = Math.min(ints[2], z3);
                        ints[5] = Math.max(ints[5], z3);
                        flags1[0] = true;
                    }

                    return true;
                });
                if (flags1[0]) {
                    ints[1] = Math.min(ints[1], y3);
                    ints[4] = Math.max(ints[4], y3);
                    flags[0] = true;
                }

                return true;
            });
            if (flags[0]) {
                ints[0] = Math.min(ints[0], x3);
                ints[3] = Math.max(ints[3], x3);
            }

            return true;
        });
        bitSetDiscreteVoxelShape.xMin = ints[0];
        bitSetDiscreteVoxelShape.yMin = ints[1];
        bitSetDiscreteVoxelShape.zMin = ints[2];
        bitSetDiscreteVoxelShape.xMax = ints[3] + 1;
        bitSetDiscreteVoxelShape.yMax = ints[4] + 1;
        bitSetDiscreteVoxelShape.zMax = ints[5] + 1;
        return bitSetDiscreteVoxelShape;
    }

    // Paper start - optimise collisions
    public static void forAllBoxes(final DiscreteVoxelShape shape, final DiscreteVoxelShape.IntLineConsumer consumer, final boolean mergeAdjacent) {
        // Paper - remove debug
        // called with the shape of a VoxelShape, so we can expect the cache to exist
        final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData cache = ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionDiscreteVoxelShape) shape).moonrise$getOrCreateCachedShapeData();

        final int sizeX = cache.sizeX();
        final int sizeY = cache.sizeY();
        final int sizeZ = cache.sizeZ();

        int indexX;
        int indexY = 0;
        int indexZ;

        int incY = sizeZ;
        int incX = sizeZ * sizeY;

        long[] bitset = cache.voxelSet();

        // index = z + y*size_z + x*(size_z*size_y)

        if (!mergeAdjacent) {
            // due to the odd selection of loop order (which does affect behavior, unfortunately) we can't simply
            // increment an index in the Z loop, and have to perform this trash (keeping track of 3 counters) to avoid
            // the multiplication
            for (int y = 0; y < sizeY; ++y, indexY += incY) {
                indexX = indexY;
                for (int x = 0; x < sizeX; ++x, indexX += incX) {
                    indexZ = indexX;
                    for (int z = 0; z < sizeZ; ++z, ++indexZ) {
                        if ((bitset[indexZ >>> 6] & (1L << indexZ)) != 0L) {
                            consumer.consume(x, y, z, x + 1, y + 1, z + 1);
                        }
                    }
                }
            }
        } else {
            // same notes about loop order as the above
            // this branch is actually important to optimise, as it affects uncached toAabbs() (which affects optimize())

            // only clone when we may write to it
            bitset = ca.spottedleaf.moonrise.common.util.MixinWorkarounds.clone(bitset);

            for (int y = 0; y < sizeY; ++y, indexY += incY) {
                indexX = indexY;
                for (int x = 0; x < sizeX; ++x, indexX += incX) {
                    for (int zIdx = indexX, endIndex = indexX + sizeZ; zIdx < endIndex; ) {
                        final int firstSetZ = ca.spottedleaf.moonrise.common.util.FlatBitsetUtil.firstSet(bitset, zIdx, endIndex);

                        if (firstSetZ == -1) {
                            break;
                        }

                        int lastSetZ = ca.spottedleaf.moonrise.common.util.FlatBitsetUtil.firstClear(bitset, firstSetZ, endIndex);
                        if (lastSetZ == -1) {
                            lastSetZ = endIndex;
                        }

                        ca.spottedleaf.moonrise.common.util.FlatBitsetUtil.clearRange(bitset, firstSetZ, lastSetZ);

                        // try to merge neighbouring on the X axis
                        int endX = x + 1; // exclusive
                        for (int neighbourIdxStart = firstSetZ + incX, neighbourIdxEnd = lastSetZ + incX;
                             endX < sizeX && ca.spottedleaf.moonrise.common.util.FlatBitsetUtil.isRangeSet(bitset, neighbourIdxStart, neighbourIdxEnd);
                             neighbourIdxStart += incX, neighbourIdxEnd += incX) {

                            ++endX;
                            ca.spottedleaf.moonrise.common.util.FlatBitsetUtil.clearRange(bitset, neighbourIdxStart, neighbourIdxEnd);
                        }

                        // try to merge neighbouring on the Y axis

                        int endY; // exclusive
                        int firstSetZY, lastSetZY;
                        y_merge:
                        for (endY = y + 1, firstSetZY = firstSetZ + incY, lastSetZY = lastSetZ + incY; endY < sizeY;
                             firstSetZY += incY, lastSetZY += incY) {

                            // test the whole XZ range
                            for (int testX = x, start = firstSetZY, end = lastSetZY; testX < endX;
                                 ++testX, start += incX, end += incX) {
                                if (!ca.spottedleaf.moonrise.common.util.FlatBitsetUtil.isRangeSet(bitset, start, end)) {
                                    break y_merge;
                                }
                            }

                            ++endY;

                            // passed, so we can clear it
                            for (int testX = x, start = firstSetZY, end = lastSetZY; testX < endX;
                                 ++testX, start += incX, end += incX) {
                                ca.spottedleaf.moonrise.common.util.FlatBitsetUtil.clearRange(bitset, start, end);
                            }
                        }

                        consumer.consume(x, y, firstSetZ - indexX, endX, endY, lastSetZ - indexX);
                        zIdx = lastSetZ;
                    }
                }
            }
        }
    }
    // Paper end - optimise collisions

    private boolean isZStripFull(int zMin, int zMax, int x, int y) {
        return x < this.xSize && y < this.ySize && this.storage.nextClearBit(this.getIndex(x, y, zMin)) >= this.getIndex(x, y, zMax);
    }

    private boolean isXZRectangleFull(int xMin, int xMax, int zMin, int zMax, int y) {
        for (int i = xMin; i < xMax; i++) {
            if (!this.isZStripFull(zMin, zMax, i, y)) {
                return false;
            }
        }

        return true;
    }

    private void clearZStrip(int zMin, int zMax, int x, int y) {
        this.storage.clear(this.getIndex(x, y, zMin), this.getIndex(x, y, zMax));
    }

    public boolean isInterior(int x, int y, int z) {
        boolean flag = x > 0 && x < this.xSize - 1 && y > 0 && y < this.ySize - 1 && z > 0 && z < this.zSize - 1;
        return flag
            && this.isFull(x, y, z)
            && this.isFull(x - 1, y, z)
            && this.isFull(x + 1, y, z)
            && this.isFull(x, y - 1, z)
            && this.isFull(x, y + 1, z)
            && this.isFull(x, y, z - 1)
            && this.isFull(x, y, z + 1);
    }
}
