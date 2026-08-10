package net.minecraft.world.phys.shapes;

import com.google.common.collect.Lists;
import com.google.common.math.DoubleMath;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

public abstract class VoxelShape implements ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape { // Paper - optimise collisions
    public final DiscreteVoxelShape shape; // Paper - optimise collisions - public
    private @Nullable VoxelShape @Nullable [] faces;

    // Paper start - optimise collisions
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private AABB singleAABBRepresentation;
    private double[] rootCoordinatesX;
    private double[] rootCoordinatesY;
    private double[] rootCoordinatesZ;
    private ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData cachedShapeData;
    private boolean isEmpty;
    private ca.spottedleaf.moonrise.patches.collisions.shape.CachedToAABBs cachedToAABBs;
    private AABB cachedBounds;
    private Boolean isFullBlock;
    private Boolean occludesFullBlock;

    // must be power of two
    private static final int MERGED_CACHE_SIZE = 16;
    private ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache[] mergedORCache;

    @Override
    public final double moonrise$offsetX() {
        return this.offsetX;
    }

    @Override
    public final double moonrise$offsetY() {
        return this.offsetY;
    }

    @Override
    public final double moonrise$offsetZ() {
        return this.offsetZ;
    }

    @Override
    public final AABB moonrise$getSingleAABBRepresentation() {
        return this.singleAABBRepresentation;
    }

    @Override
    public final double[] moonrise$rootCoordinatesX() {
        return this.rootCoordinatesX;
    }

    @Override
    public final double[] moonrise$rootCoordinatesY() {
        return this.rootCoordinatesY;
    }

    @Override
    public final double[] moonrise$rootCoordinatesZ() {
        return this.rootCoordinatesZ;
    }

    private static double[] extractRawArray(final DoubleList list) {
        if (list instanceof it.unimi.dsi.fastutil.doubles.DoubleArrayList rawList) {
            final double[] raw = rawList.elements();
            final int expected = rawList.size();
            if (raw.length == expected) {
                return raw;
            } else {
                return java.util.Arrays.copyOf(raw, expected);
            }
        } else {
            return list.toDoubleArray();
        }
    }

    @Override
    public final void moonrise$initCache() {
        this.cachedShapeData = ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionDiscreteVoxelShape)this.shape).moonrise$getOrCreateCachedShapeData();
        this.isEmpty = this.cachedShapeData.isEmpty();

        final DoubleList xList = this.getCoords(Direction.Axis.X);
        final DoubleList yList = this.getCoords(Direction.Axis.Y);
        final DoubleList zList = this.getCoords(Direction.Axis.Z);

        if (xList instanceof OffsetDoubleList offsetDoubleList) {
            this.offsetX = offsetDoubleList.offset;
            this.rootCoordinatesX = extractRawArray(offsetDoubleList.delegate);
        } else {
            this.rootCoordinatesX = extractRawArray(xList);
        }

        if (yList instanceof OffsetDoubleList offsetDoubleList) {
            this.offsetY = offsetDoubleList.offset;
            this.rootCoordinatesY = extractRawArray(offsetDoubleList.delegate);
        } else {
            this.rootCoordinatesY = extractRawArray(yList);
        }

        if (zList instanceof OffsetDoubleList offsetDoubleList) {
            this.offsetZ = offsetDoubleList.offset;
            this.rootCoordinatesZ = extractRawArray(offsetDoubleList.delegate);
        } else {
            this.rootCoordinatesZ = extractRawArray(zList);
        }

        if (this.cachedShapeData.hasSingleAABB()) {
            this.singleAABBRepresentation = new AABB(
                this.rootCoordinatesX[0] + this.offsetX, this.rootCoordinatesY[0] + this.offsetY, this.rootCoordinatesZ[0] + this.offsetZ,
                this.rootCoordinatesX[1] + this.offsetX, this.rootCoordinatesY[1] + this.offsetY, this.rootCoordinatesZ[1] + this.offsetZ
            );
            this.cachedBounds = this.singleAABBRepresentation;
        }
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData moonrise$getCachedVoxelData() {
        return this.cachedShapeData;
    }

    private VoxelShape[] faceShapeClampedCache;

    @Override
    public final VoxelShape moonrise$getFaceShapeClamped(final Direction direction) {
        if (this.isEmpty) {
            return (VoxelShape)(Object)this;
        }
        if ((VoxelShape)(Object)this == Shapes.block()) {
            return (VoxelShape)(Object)this;
        }

        VoxelShape[] cache = this.faceShapeClampedCache;
        if (cache != null) {
            final VoxelShape ret = cache[direction.ordinal()];
            if (ret != null) {
                return ret;
            }
        }


        if (cache == null) {
            this.faceShapeClampedCache = cache = new VoxelShape[6];
        }

        final Direction.Axis axis = direction.getAxis();

        final VoxelShape ret;

        if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            if (DoubleMath.fuzzyEquals(this.max(axis), 1.0, ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON)) {
                ret = ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.sliceShape((VoxelShape)(Object)this, axis, this.shape.getSize(axis) - 1);
            } else {
                ret = Shapes.empty();
            }
        } else {
            if (DoubleMath.fuzzyEquals(this.min(axis), 0.0, ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON)) {
                ret = ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.sliceShape((VoxelShape)(Object)this, axis, 0);
            } else {
                ret = Shapes.empty();
            }
        }

        cache[direction.ordinal()] = ret;

        return ret;
    }

    private boolean computeOccludesFullBlock() {
        if (this.isEmpty) {
            this.occludesFullBlock = Boolean.FALSE;
            return false;
        }

        if (this.moonrise$isFullBlock()) {
            this.occludesFullBlock = Boolean.TRUE;
            return true;
        }

        final AABB singleAABB = this.singleAABBRepresentation;
        if (singleAABB != null) {
            // check if the bounding box encloses the full cube
            final boolean ret =
                (singleAABB.minY <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON && singleAABB.maxY >= (1 - ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON)) &&
                    (singleAABB.minX <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON && singleAABB.maxX >= (1 - ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON)) &&
                    (singleAABB.minZ <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON && singleAABB.maxZ >= (1 - ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON));
            this.occludesFullBlock = Boolean.valueOf(ret);
            return ret;
        }

        final boolean ret = !Shapes.joinIsNotEmpty(Shapes.block(), ((VoxelShape)(Object)this), BooleanOp.ONLY_FIRST);
        this.occludesFullBlock = Boolean.valueOf(ret);
        return ret;
    }

    @Override
    public final boolean moonrise$occludesFullBlock() {
        final Boolean ret = this.occludesFullBlock;
        if (ret != null) {
            return ret.booleanValue();
        }

        return this.computeOccludesFullBlock();
    }

    @Override
    public final boolean moonrise$occludesFullBlockIfCached() {
        final Boolean ret = this.occludesFullBlock;
        return ret != null ? ret.booleanValue() : false;
    }

    private static int hash(final VoxelShape key) {
        return it.unimi.dsi.fastutil.HashCommon.mix(System.identityHashCode(key));
    }

    @Override
    public final VoxelShape moonrise$orUnoptimized(final VoxelShape other) {
        // don't cache simple cases
        if (((VoxelShape)(Object)this) == other) {
            return other;
        }

        if (this.isEmpty) {
            return other;
        }

        if (other.isEmpty()) {
            return (VoxelShape)(Object)this;
        }

        // try this cache first
        final int thisCacheKey = hash(other) & (MERGED_CACHE_SIZE - 1);
        final ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache cached = this.mergedORCache == null ? null : this.mergedORCache[thisCacheKey];
        if (cached != null && cached.key() == other) {
            return cached.result();
        }

        // try other cache
        final int otherCacheKey = hash((VoxelShape)(Object)this) & (MERGED_CACHE_SIZE - 1);
        final ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache otherCache = ((VoxelShape)(Object)other).mergedORCache == null ? null : ((VoxelShape)(Object)other).mergedORCache[otherCacheKey];
        if (otherCache != null && otherCache.key() == (VoxelShape)(Object)this) {
            return otherCache.result();
        }

        // note: unsure if joinUnoptimized(1, 2, OR) == joinUnoptimized(2, 1, OR) for all cases
        final VoxelShape result = Shapes.joinUnoptimized((VoxelShape)(Object)this, other, BooleanOp.OR);

        if (cached != null && otherCache == null) {
            // try to use second cache instead of replacing an entry in this cache
            if (((VoxelShape)(Object)other).mergedORCache == null) {
                ((VoxelShape)(Object)other).mergedORCache = new ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache[MERGED_CACHE_SIZE];
            }
            ((VoxelShape)(Object)other).mergedORCache[otherCacheKey] = new ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache((VoxelShape)(Object)this, result);
        } else {
            // line is not occupied or other cache line is full
            // always bias to replace this cache, as this cache is the first we check
            if (this.mergedORCache == null) {
                this.mergedORCache = new ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache[MERGED_CACHE_SIZE];
            }
            this.mergedORCache[thisCacheKey] = new ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache(other, result);
        }

        return result;
    }

    private static DoubleList offsetList(final double[] src, final double by) {
        final it.unimi.dsi.fastutil.doubles.DoubleArrayList wrap = it.unimi.dsi.fastutil.doubles.DoubleArrayList.wrap(src);
        if (by == 0.0) {
            return wrap;
        }
        return new OffsetDoubleList(wrap, by);
    }

    private List<AABB> toAabbsUncached() {
        final List<AABB> ret;
        if (this.singleAABBRepresentation != null) {
            ret = new java.util.ArrayList<>(1);
            ret.add(this.singleAABBRepresentation);
        } else {
            ret = new java.util.ArrayList<>();
            final double[] coordsX = this.rootCoordinatesX;
            final double[] coordsY = this.rootCoordinatesY;
            final double[] coordsZ = this.rootCoordinatesZ;

            final double offX = this.offsetX;
            final double offY = this.offsetY;
            final double offZ = this.offsetZ;

            this.shape.forAllBoxes((final int minX, final int minY, final int minZ,
                                    final int maxX, final int maxY, final int maxZ) -> {
                ret.add(new AABB(
                    coordsX[minX] + offX,
                    coordsY[minY] + offY,
                    coordsZ[minZ] + offZ,


                    coordsX[maxX] + offX,
                    coordsY[maxY] + offY,
                    coordsZ[maxZ] + offZ
                ));
            }, true);
        }

        // cache result
        this.cachedToAABBs = new ca.spottedleaf.moonrise.patches.collisions.shape.CachedToAABBs(ret, false, 0.0, 0.0, 0.0);

        return ret;
    }

    private boolean computeFullBlock() {
        Boolean ret;
        if (this.isEmpty) {
            ret = Boolean.FALSE;
        } else if ((VoxelShape)(Object)this == Shapes.block()) {
            ret = Boolean.TRUE;
        } else {
            final AABB singleAABB = this.singleAABBRepresentation;
            if (singleAABB == null) {
                final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData shapeData = this.cachedShapeData;
                final int sMinX = shapeData.minFullX();
                final int sMinY = shapeData.minFullY();
                final int sMinZ = shapeData.minFullZ();

                final int sMaxX = shapeData.maxFullX();
                final int sMaxY = shapeData.maxFullY();
                final int sMaxZ = shapeData.maxFullZ();

                if (Math.abs(this.rootCoordinatesX[sMinX] + this.offsetX) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(this.rootCoordinatesY[sMinY] + this.offsetY) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(this.rootCoordinatesZ[sMinZ] + this.offsetZ) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&

                    Math.abs(1.0 - (this.rootCoordinatesX[sMaxX] + this.offsetX)) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(1.0 - (this.rootCoordinatesY[sMaxY] + this.offsetY)) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(1.0 - (this.rootCoordinatesZ[sMaxZ] + this.offsetZ)) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON) {

                    // index = z + y*sizeZ + x*(sizeZ*sizeY)

                    final int sizeY = shapeData.sizeY();
                    final int sizeZ = shapeData.sizeZ();

                    final long[] bitset = shapeData.voxelSet();

                    ret = Boolean.TRUE;

                    check_full:
                    for (int x = sMinX; x < sMaxX; ++x) {
                        for (int y = sMinY; y < sMaxY; ++y) {
                            final int baseIndex = y*sizeZ + x*(sizeZ*sizeY);
                            if (!ca.spottedleaf.moonrise.common.util.FlatBitsetUtil.isRangeSet(bitset, baseIndex + sMinZ, baseIndex + sMaxZ)) {
                                ret = Boolean.FALSE;
                                break check_full;
                            }
                        }
                    }
                } else {
                    ret = Boolean.FALSE;
                }
            } else {
                ret = Boolean.valueOf(
                    Math.abs(singleAABB.minX) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                        Math.abs(singleAABB.minY) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                        Math.abs(singleAABB.minZ) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&

                        Math.abs(1.0 - singleAABB.maxX) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                        Math.abs(1.0 - singleAABB.maxY) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                        Math.abs(1.0 - singleAABB.maxZ) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON
                );
            }
        }

        this.isFullBlock = ret;

        return ret.booleanValue();
    }

    @Override
    public final boolean moonrise$isFullBlock() {
        final Boolean ret = this.isFullBlock;

        if (ret != null) {
            return ret.booleanValue();
        }

        return this.computeFullBlock();
    }

    private static BlockHitResult clip(final AABB aabb, final Vec3 from, final Vec3 to, final BlockPos offset) {
        final double[] minDistanceArr = new double[] { 1.0 };
        final double diffX = to.x - from.x;
        final double diffY = to.y - from.y;
        final double diffZ = to.z - from.z;

        final Direction direction = AABB.getDirection(aabb.move(offset), from, minDistanceArr, null, diffX, diffY, diffZ);

        if (direction == null) {
            return null;
        }

        final double minDistance = minDistanceArr[0];
        return new BlockHitResult(from.add(minDistance * diffX, minDistance * diffY, minDistance * diffZ), direction, offset, false);
    }

    private VoxelShape calculateFaceDirect(final Direction direction, final Direction.Axis axis, final double[] coords, final double offset) {
        if (coords.length == 2 &&
            DoubleMath.fuzzyEquals(coords[0] + offset, 0.0, ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON) &&
            DoubleMath.fuzzyEquals(coords[1] + offset, 1.0, ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON)) {
            return (VoxelShape)(Object)this;
        }

        final boolean positiveDir = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;

        // see findIndex
        final int index = ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.findFloor(
            coords, offset, (positiveDir ? (1.0 - ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON) : (0.0 + ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON)),
            0, coords.length - 1
        );

        return ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.sliceShape(
            (VoxelShape)(Object)this, axis, index
        );
    }
    // Paper end - optimise collisions

    protected VoxelShape(DiscreteVoxelShape shape) {
        this.shape = shape;
    }

    public double min(Direction.Axis axis) {
        // Paper start - optimise collisions
        final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData shapeData = this.cachedShapeData;
        switch (axis) {
            case X: {
                final int idx = shapeData.minFullX();
                return idx >= shapeData.sizeX() ? Double.POSITIVE_INFINITY : (this.rootCoordinatesX[idx] + this.offsetX);
            }
            case Y: {
                final int idx = shapeData.minFullY();
                return idx >= shapeData.sizeY() ? Double.POSITIVE_INFINITY : (this.rootCoordinatesY[idx] + this.offsetY);
            }
            case Z: {
                final int idx = shapeData.minFullZ();
                return idx >= shapeData.sizeZ() ? Double.POSITIVE_INFINITY : (this.rootCoordinatesZ[idx] + this.offsetZ);
            }
            default: {
                // should never get here
                return Double.POSITIVE_INFINITY;
            }
        }
        // Paper end - optimise collisions
    }

    public double max(Direction.Axis axis) {
        // Paper start - optimise collisions
        final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData shapeData = this.cachedShapeData;
        switch (axis) {
            case X: {
                final int idx = shapeData.maxFullX();
                return idx <= 0 ? Double.NEGATIVE_INFINITY : (this.rootCoordinatesX[idx] + this.offsetX);
            }
            case Y: {
                final int idx = shapeData.maxFullY();
                return idx <= 0 ? Double.NEGATIVE_INFINITY : (this.rootCoordinatesY[idx] + this.offsetY);
            }
            case Z: {
                final int idx = shapeData.maxFullZ();
                return idx <= 0 ? Double.NEGATIVE_INFINITY : (this.rootCoordinatesZ[idx] + this.offsetZ);
            }
            default: {
                // should never get here
                return Double.NEGATIVE_INFINITY;
            }
        }
        // Paper end - optimise collisions
    }

    public AABB bounds() {
        // Paper start - optimise collisions
        if (this.isEmpty) {
            throw Util.pauseInIde(new UnsupportedOperationException("No bounds for empty shape."));
        }
        AABB cached = this.cachedBounds;
        if (cached != null) {
            return cached;
        }

        final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData shapeData = this.cachedShapeData;

        final double[] coordsX = this.rootCoordinatesX;
        final double[] coordsY = this.rootCoordinatesY;
        final double[] coordsZ = this.rootCoordinatesZ;

        final double offX = this.offsetX;
        final double offY = this.offsetY;
        final double offZ = this.offsetZ;

        // note: if not empty, then there is one full AABB so no bounds checks are needed on the minFull/maxFull indices
        cached = new AABB(
            coordsX[shapeData.minFullX()] + offX,
            coordsY[shapeData.minFullY()] + offY,
            coordsZ[shapeData.minFullZ()] + offZ,

            coordsX[shapeData.maxFullX()] + offX,
            coordsY[shapeData.maxFullY()] + offY,
            coordsZ[shapeData.maxFullZ()] + offZ
        );

        this.cachedBounds = cached;
        return cached;
        // Paper end - optimise collisions
    }

    public VoxelShape singleEncompassing() {
        // Paper start - optimise collisions
        if (this.isEmpty) {
            return Shapes.empty();
        }
        return Shapes.create(this.bounds());
        // Paper end - optimise collisions
    }

    protected double get(Direction.Axis axis, int index) {
        // Paper start - optimise collisions
        final int idx = index;
        switch (axis) {
            case X: {
                return this.rootCoordinatesX[idx] + this.offsetX;
            }
            case Y: {
                return this.rootCoordinatesY[idx] + this.offsetY;
            }
            case Z: {
                return this.rootCoordinatesZ[idx] + this.offsetZ;
            }
            default: {
                throw new IllegalStateException("Unknown axis: " + axis);
            }
        }
        // Paper end - optimise collisions
    }

    public abstract DoubleList getCoords(Direction.Axis axis);

    public boolean isEmpty() {
        return this.isEmpty; // Paper - optimise collisions
    }

    public VoxelShape move(Vec3 offset) {
        return this.move(offset.x, offset.y, offset.z);
    }

    public VoxelShape move(Vec3i offset) {
        return this.move(offset.getX(), offset.getY(), offset.getZ());
    }

    public VoxelShape move(double xOffset, double yOffset, double zOffset) {
        // Paper start - optimise collisions
        if (this.isEmpty) {
            return Shapes.empty();
        }

        final ArrayVoxelShape ret = new ArrayVoxelShape(
            this.shape,
            offsetList(this.rootCoordinatesX, this.offsetX + xOffset),
            offsetList(this.rootCoordinatesY, this.offsetY + yOffset),
            offsetList(this.rootCoordinatesZ, this.offsetZ + zOffset)
        );

        final ca.spottedleaf.moonrise.patches.collisions.shape.CachedToAABBs cachedToAABBs = this.cachedToAABBs;
        if (cachedToAABBs != null) {
            ((VoxelShape)(Object)ret).cachedToAABBs = ca.spottedleaf.moonrise.patches.collisions.shape.CachedToAABBs.offset(cachedToAABBs, xOffset, yOffset, zOffset);
        }

        return ret;
        // Paper end - optimise collisions
    }

    public VoxelShape optimize() {
        // Paper start - optimise collisions
        if (this.isEmpty) {
            return Shapes.empty();
        }

        if (this.singleAABBRepresentation != null) {
            // note: the isFullBlock() is fuzzy, and Shapes.create() is also fuzzy which would return block()
            return this.moonrise$isFullBlock() ? Shapes.block() : (VoxelShape)(Object)this;
        }

        final List<AABB> aabbs = this.toAabbs();

        if (aabbs.isEmpty()) {
            // We are a SliceShape, which does not properly fill isEmpty for every case
            return Shapes.empty();
        }

        if (aabbs.size() == 1) {
            final AABB singleAABB = aabbs.get(0);
            final VoxelShape ret = Shapes.create(singleAABB);

            // forward AABB cache
            if (((VoxelShape)(Object)ret).cachedToAABBs == null) {
                ((VoxelShape)(Object)ret).cachedToAABBs = this.cachedToAABBs;
            }

            return ret;
        } else {
            // reduce complexity of joins by splitting the merges (old complexity: n^2, new: nlogn)

            // set up flat array so that this merge is done in-place
            final VoxelShape[] tmp = new VoxelShape[aabbs.size()];

            // initialise as unmerged
            for (int i = 0, len = aabbs.size(); i < len; ++i) {
                tmp[i] = Shapes.create(aabbs.get(i));
            }

            int size = aabbs.size();
            while (size > 1) {
                int newSize = 0;
                for (int i = 0; i < size; i += 2) {
                    final int next = i + 1;
                    if (next >= size) {
                        // nothing to merge with, so leave it for next iteration
                        tmp[newSize++] = tmp[i];
                        break;
                    } else {
                        // merge with adjacent
                        final VoxelShape first = tmp[i];
                        final VoxelShape second = tmp[next];

                        tmp[newSize++] = Shapes.joinUnoptimized(first, second, BooleanOp.OR);
                    }
                }
                size = newSize;
            }

            final VoxelShape ret = tmp[0];

            // forward AABB cache
            if (((VoxelShape)(Object)ret).cachedToAABBs == null) {
                ((VoxelShape)(Object)ret).cachedToAABBs = this.cachedToAABBs;
            }

            return ret;
        }
        // Paper end - optimise collisions
    }

    public void forAllEdges(Shapes.DoubleLineConsumer action) {
        this.shape
            .forAllEdges(
                (x1, y1, z1, x2, y2, z2) -> action.consume(
                    this.get(Direction.Axis.X, x1),
                    this.get(Direction.Axis.Y, y1),
                    this.get(Direction.Axis.Z, z1),
                    this.get(Direction.Axis.X, x2),
                    this.get(Direction.Axis.Y, y2),
                    this.get(Direction.Axis.Z, z2)
                ),
                true
            );
    }

    public void forAllBoxes(Shapes.DoubleLineConsumer action) {
        DoubleList coords = this.getCoords(Direction.Axis.X);
        DoubleList coords1 = this.getCoords(Direction.Axis.Y);
        DoubleList coords2 = this.getCoords(Direction.Axis.Z);
        this.shape
            .forAllBoxes(
                (x1, y1, z1, x2, y2, z2) -> action.consume(
                    coords.getDouble(x1), coords1.getDouble(y1), coords2.getDouble(z1), coords.getDouble(x2), coords1.getDouble(y2), coords2.getDouble(z2)
                ),
                true
            );
    }

    public List<AABB> toAabbs() {
        // Paper start - optimise collisions
        ca.spottedleaf.moonrise.patches.collisions.shape.CachedToAABBs cachedToAABBs = this.cachedToAABBs;
        if (cachedToAABBs != null) {
            if (!cachedToAABBs.isOffset()) {
                return cachedToAABBs.aabbs();
            }

            // all we need to do is offset the cache
            cachedToAABBs = cachedToAABBs.removeOffset();
            // update cache
            this.cachedToAABBs = cachedToAABBs;

            return cachedToAABBs.aabbs();
        }

        // make new cache
        return this.toAabbsUncached();
        // Paper end - optimise collisions
    }

    public double min(Direction.Axis axis, double primaryPosition, double secondaryPosition) {
        Direction.Axis axis1 = AxisCycle.FORWARD.cycle(axis);
        Direction.Axis axis2 = AxisCycle.BACKWARD.cycle(axis);
        int i = this.findIndex(axis1, primaryPosition);
        int i1 = this.findIndex(axis2, secondaryPosition);
        int i2 = this.shape.firstFull(axis, i, i1);
        return i2 >= this.shape.getSize(axis) ? Double.POSITIVE_INFINITY : this.get(axis, i2);
    }

    public double max(Direction.Axis axis, double primaryPosition, double secondaryPosition) {
        Direction.Axis axis1 = AxisCycle.FORWARD.cycle(axis);
        Direction.Axis axis2 = AxisCycle.BACKWARD.cycle(axis);
        int i = this.findIndex(axis1, primaryPosition);
        int i1 = this.findIndex(axis2, secondaryPosition);
        int i2 = this.shape.lastFull(axis, i, i1);
        return i2 <= 0 ? Double.NEGATIVE_INFINITY : this.get(axis, i2);
    }

    protected int findIndex(Direction.Axis axis, double position) {
        // Paper start - optimise collisions
        final double value = position;
        switch (axis) {
            case X: {
                final double[] values = this.rootCoordinatesX;
                return ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.findFloor(
                    values, this.offsetX, value, 0, values.length - 1
                );
            }
            case Y: {
                final double[] values = this.rootCoordinatesY;
                return ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.findFloor(
                    values, this.offsetY, value, 0, values.length - 1
                );
            }
            case Z: {
                final double[] values = this.rootCoordinatesZ;
                return ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.findFloor(
                    values, this.offsetZ, value, 0, values.length - 1
                );
            }
            default: {
                throw new IllegalStateException("Unknown axis: " + axis);
            }
        }
        // Paper end - optimise collisions
    }

    public @Nullable BlockHitResult clip(Vec3 startVec, Vec3 endVec, BlockPos pos) {
        // Paper start - optimise collisions
        if (this.isEmpty) {
            return null;
        }

        final Vec3 directionOpposite = endVec.subtract(startVec);
        if (directionOpposite.lengthSqr() < ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON) {
            return null;
        }

        final Vec3 fromBehind = startVec.add(directionOpposite.scale(0.001));
        final double fromBehindOffsetX = fromBehind.x - (double) pos.getX();
        final double fromBehindOffsetY = fromBehind.y - (double) pos.getY();
        final double fromBehindOffsetZ = fromBehind.z - (double) pos.getZ();

        final AABB singleAABB = this.singleAABBRepresentation;
        if (singleAABB != null) {
            if (singleAABB.contains(fromBehindOffsetX, fromBehindOffsetY, fromBehindOffsetZ)) {
                return new BlockHitResult(fromBehind, Direction.getApproximateNearest(directionOpposite.x, directionOpposite.y, directionOpposite.z).getOpposite(), pos, true);
            }
            return clip(singleAABB, startVec, endVec, pos);
        }

        if (ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.strictlyContains((VoxelShape) (Object) this, fromBehindOffsetX, fromBehindOffsetY, fromBehindOffsetZ)) {
            return new BlockHitResult(fromBehind, Direction.getApproximateNearest(directionOpposite.x, directionOpposite.y, directionOpposite.z).getOpposite(), pos, true);
        }

        return AABB.clip(((VoxelShape) (Object) this).toAabbs(), startVec, endVec, pos);
        // Paper end - optimise collisions
    }

    // Paper start - optimise collisions
    public Optional<Vec3> closestPointTo(Vec3 point) {
        if (this.isEmpty) {
            return Optional.empty();
        }

        Vec3 ret = null;
        double retDistance = Double.MAX_VALUE;

        final List<AABB> aabbs = this.toAabbs();
        for (int i = 0, len = aabbs.size(); i < len; ++i) {
            final AABB aabb = aabbs.get(i);
            final double x = Mth.clamp(point.x, aabb.minX, aabb.maxX);
            final double y = Mth.clamp(point.y, aabb.minY, aabb.maxY);
            final double z = Mth.clamp(point.z, aabb.minZ, aabb.maxZ);

            double dist = point.distanceToSqr(x, y, z);
            if (dist < retDistance) {
                ret = new Vec3(x, y, z);
                retDistance = dist;
            }
        }

        return Optional.ofNullable(ret);
        // Paper end - optimise collisions
    }

    public VoxelShape getFaceShape(Direction side) {
        if (!this.isEmpty() && this != Shapes.block()) {
            if (this.faces != null) {
                VoxelShape voxelShape = this.faces[side.ordinal()];
                if (voxelShape != null) {
                    return voxelShape;
                }
            } else {
                this.faces = new VoxelShape[6];
            }

            VoxelShape voxelShape = this.calculateFace(side);
            this.faces[side.ordinal()] = voxelShape;
            return voxelShape;
        } else {
            return this;
        }
    }

    private VoxelShape calculateFace(Direction side) {
        // Paper start - optimise collisions
        final Direction.Axis axis = side.getAxis();
        switch (axis) {
            case X: {
                return this.calculateFaceDirect(side, axis, this.rootCoordinatesX, this.offsetX);
            }
            case Y: {
                return this.calculateFaceDirect(side, axis, this.rootCoordinatesY, this.offsetY);
            }
            case Z: {
                return this.calculateFaceDirect(side, axis, this.rootCoordinatesZ, this.offsetZ);
            }
            default: {
                throw new IllegalStateException("Unknown axis: " + axis);
            }
        }
        // Paper end - optimise collisions
    }

    protected boolean isCubeLike() {
        for (Direction.Axis axis : Direction.Axis.VALUES) {
            if (!this.isCubeLikeAlong(axis)) {
                return false;
            }
        }

        return true;
    }

    private boolean isCubeLikeAlong(Direction.Axis axis) {
        DoubleList coords = this.getCoords(axis);
        return coords.size() == 2 && DoubleMath.fuzzyEquals(coords.getDouble(0), 0.0, 1.0E-7) && DoubleMath.fuzzyEquals(coords.getDouble(1), 1.0, 1.0E-7);
    }

    // Paper start - optimise collisions
    public double collide(final Direction.Axis axis, final AABB source, final double source_move) {
        if (this.isEmpty) {
            return source_move;
        }
        if (Math.abs(source_move) < ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON) {
            return 0.0;
        }
        switch (axis) {
            case X: {
                return ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.collideX((VoxelShape) (Object) this, source, source_move);
            }
            case Y: {
                return ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.collideY((VoxelShape) (Object) this, source, source_move);
            }
            case Z: {
                return ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.collideZ((VoxelShape) (Object) this, source, source_move);
            }
            default: {
                throw new RuntimeException("Unknown axis: " + axis);
            }
        }
    }
    // Paper end - optimise collisions

    protected double collideX(AxisCycle movementAxis, AABB collisionBox, double desiredOffset) {
        if (this.isEmpty()) {
            return desiredOffset;
        } else if (Math.abs(desiredOffset) < 1.0E-7) {
            return 0.0;
        } else {
            AxisCycle axisCycle = movementAxis.inverse();
            Direction.Axis axis = axisCycle.cycle(Direction.Axis.X);
            Direction.Axis axis1 = axisCycle.cycle(Direction.Axis.Y);
            Direction.Axis axis2 = axisCycle.cycle(Direction.Axis.Z);
            double d = collisionBox.max(axis);
            double d1 = collisionBox.min(axis);
            int i = this.findIndex(axis, d1 + 1.0E-7);
            int i1 = this.findIndex(axis, d - 1.0E-7);
            int max = Math.max(0, this.findIndex(axis1, collisionBox.min(axis1) + 1.0E-7));
            int min = Math.min(this.shape.getSize(axis1), this.findIndex(axis1, collisionBox.max(axis1) - 1.0E-7) + 1);
            int max1 = Math.max(0, this.findIndex(axis2, collisionBox.min(axis2) + 1.0E-7));
            int min1 = Math.min(this.shape.getSize(axis2), this.findIndex(axis2, collisionBox.max(axis2) - 1.0E-7) + 1);
            int size = this.shape.getSize(axis);
            if (desiredOffset > 0.0) {
                for (int i2 = i1 + 1; i2 < size; i2++) {
                    for (int i3 = max; i3 < min; i3++) {
                        for (int i4 = max1; i4 < min1; i4++) {
                            if (this.shape.isFullWide(axisCycle, i2, i3, i4)) {
                                double d2 = this.get(axis, i2) - d;
                                if (d2 >= -1.0E-7) {
                                    desiredOffset = Math.min(desiredOffset, d2);
                                }

                                return desiredOffset;
                            }
                        }
                    }
                }
            } else if (desiredOffset < 0.0) {
                for (int i2 = i - 1; i2 >= 0; i2--) {
                    for (int i3 = max; i3 < min; i3++) {
                        for (int i4x = max1; i4x < min1; i4x++) {
                            if (this.shape.isFullWide(axisCycle, i2, i3, i4x)) {
                                double d2 = this.get(axis, i2 + 1) - d1;
                                if (d2 <= 1.0E-7) {
                                    desiredOffset = Math.max(desiredOffset, d2);
                                }

                                return desiredOffset;
                            }
                        }
                    }
                }
            }

            return desiredOffset;
        }
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public String toString() {
        return this.isEmpty() ? "EMPTY" : "VoxelShape[" + this.bounds() + "]";
    }
}
