package net.minecraft.world.phys;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public class AABB {
    private static final double EPSILON = 1.0E-7;
    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    // Leaf start - Lithium - fast util
    static {
        assert Direction.Axis.X.ordinal() == 0;
        assert Direction.Axis.Y.ordinal() == 1;
        assert Direction.Axis.Z.ordinal() == 2;
        assert Direction.Axis.values().length == 3;
    }
    // Leaf end - Lithium - fast util

    public AABB(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public AABB(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    public AABB(Vec3 start, Vec3 end) {
        this(start.x, start.y, start.z, end.x, end.y, end.z);
    }

    public static AABB of(BoundingBox mutableBox) {
        return new AABB(mutableBox.minX(), mutableBox.minY(), mutableBox.minZ(), mutableBox.maxX() + 1, mutableBox.maxY() + 1, mutableBox.maxZ() + 1);
    }

    public static AABB unitCubeFromLowerCorner(Vec3 vector) {
        return new AABB(vector.x, vector.y, vector.z, vector.x + 1.0, vector.y + 1.0, vector.z + 1.0);
    }

    public static AABB encapsulatingFullBlocks(BlockPos startPos, BlockPos endPos) {
        return new AABB(
            Math.min(startPos.getX(), endPos.getX()),
            Math.min(startPos.getY(), endPos.getY()),
            Math.min(startPos.getZ(), endPos.getZ()),
            Math.max(startPos.getX(), endPos.getX()) + 1,
            Math.max(startPos.getY(), endPos.getY()) + 1,
            Math.max(startPos.getZ(), endPos.getZ()) + 1
        );
    }

    public AABB setMinX(double minX) {
        return new AABB(minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMinY(double minY) {
        return new AABB(this.minX, minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMinZ(double minZ) {
        return new AABB(this.minX, this.minY, minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMaxX(double maxX) {
        return new AABB(this.minX, this.minY, this.minZ, maxX, this.maxY, this.maxZ);
    }

    public AABB setMaxY(double maxY) {
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, maxY, this.maxZ);
    }

    public AABB setMaxZ(double maxZ) {
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, maxZ);
    }

    public double min(Direction.Axis axis) {
        // Leaf start - Lithium - fast util
        switch (axis.ordinal()) {
            case 0: //X
                return this.minX;
            case 1: //Y
                return this.minY;
            case 2: //Z
                return this.minZ;
        }

        throw new IllegalArgumentException();
        // Leaf end - Lithium - fast util
    }

    public double max(Direction.Axis axis) {
        // Leaf start - Lithium - fast util
        switch (axis.ordinal()) {
            case 0: //X
                return this.maxX;
            case 1: //Y
                return this.maxY;
            case 2: //Z
                return this.maxZ;
        }

        throw new IllegalArgumentException();
        // Leaf end - Lithium - fast util
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof AABB aabb
                && Double.compare(aabb.minX, this.minX) == 0
                && Double.compare(aabb.minY, this.minY) == 0
                && Double.compare(aabb.minZ, this.minZ) == 0
                && Double.compare(aabb.maxX, this.maxX) == 0
                && Double.compare(aabb.maxY, this.maxY) == 0
                && Double.compare(aabb.maxZ, this.maxZ) == 0;
    }

    @Override
    public int hashCode() {
        long l = Double.doubleToLongBits(this.minX);
        int i = (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.minY);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.minZ);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.maxX);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.maxY);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.maxZ);
        return 31 * i + (int)(l ^ l >>> 32);
    }

    public AABB contract(double x, double y, double z) {
        double d = this.minX;
        double d1 = this.minY;
        double d2 = this.minZ;
        double d3 = this.maxX;
        double d4 = this.maxY;
        double d5 = this.maxZ;
        if (x < 0.0) {
            d -= x;
        } else if (x > 0.0) {
            d3 -= x;
        }

        if (y < 0.0) {
            d1 -= y;
        } else if (y > 0.0) {
            d4 -= y;
        }

        if (z < 0.0) {
            d2 -= z;
        } else if (z > 0.0) {
            d5 -= z;
        }

        return new AABB(d, d1, d2, d3, d4, d5);
    }

    public AABB expandTowards(Vec3 vector) {
        return this.expandTowards(vector.x, vector.y, vector.z);
    }

    public AABB expandTowards(double x, double y, double z) {
        double d = this.minX;
        double d1 = this.minY;
        double d2 = this.minZ;
        double d3 = this.maxX;
        double d4 = this.maxY;
        double d5 = this.maxZ;
        if (x < 0.0) {
            d += x;
        } else if (x > 0.0) {
            d3 += x;
        }

        if (y < 0.0) {
            d1 += y;
        } else if (y > 0.0) {
            d4 += y;
        }

        if (z < 0.0) {
            d2 += z;
        } else if (z > 0.0) {
            d5 += z;
        }

        return new AABB(d, d1, d2, d3, d4, d5);
    }

    public AABB inflate(double x, double y, double z) {
        double d = this.minX - x;
        double d1 = this.minY - y;
        double d2 = this.minZ - z;
        double d3 = this.maxX + x;
        double d4 = this.maxY + y;
        double d5 = this.maxZ + z;
        return new AABB(d, d1, d2, d3, d4, d5);
    }

    public AABB inflate(double value) {
        return this.inflate(value, value, value);
    }

    public AABB intersect(AABB other) {
        double max = Math.max(this.minX, other.minX);
        double max1 = Math.max(this.minY, other.minY);
        double max2 = Math.max(this.minZ, other.minZ);
        double min = Math.min(this.maxX, other.maxX);
        double min1 = Math.min(this.maxY, other.maxY);
        double min2 = Math.min(this.maxZ, other.maxZ);
        return new AABB(max, max1, max2, min, min1, min2);
    }

    public AABB minmax(AABB other) {
        double min = Math.min(this.minX, other.minX);
        double min1 = Math.min(this.minY, other.minY);
        double min2 = Math.min(this.minZ, other.minZ);
        double max = Math.max(this.maxX, other.maxX);
        double max1 = Math.max(this.maxY, other.maxY);
        double max2 = Math.max(this.maxZ, other.maxZ);
        return new AABB(min, min1, min2, max, max1, max2);
    }

    public AABB move(double x, double y, double z) {
        return new AABB(this.minX + x, this.minY + y, this.minZ + z, this.maxX + x, this.maxY + y, this.maxZ + z);
    }

    public AABB move(BlockPos pos) {
        return new AABB(
            this.minX + pos.getX(), this.minY + pos.getY(), this.minZ + pos.getZ(), this.maxX + pos.getX(), this.maxY + pos.getY(), this.maxZ + pos.getZ()
        );
    }

    public AABB move(Vec3 vec) {
        return this.move(vec.x, vec.y, vec.z);
    }

    public AABB move(Vector3f vec) {
        return this.move(vec.x, vec.y, vec.z);
    }

    public boolean intersects(AABB other) {
        return this.intersects(other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
    }

    public boolean intersects(double x1, double y1, double z1, double x2, double y2, double z2) {
        return this.minX < x2 && this.maxX > x1 && this.minY < y2 && this.maxY > y1 && this.minZ < z2 && this.maxZ > z1;
    }

    public boolean intersects(Vec3 min, Vec3 max) {
        return this.intersects(
            Math.min(min.x, max.x), Math.min(min.y, max.y), Math.min(min.z, max.z), Math.max(min.x, max.x), Math.max(min.y, max.y), Math.max(min.z, max.z)
        );
    }

    public boolean intersects(BlockPos pos) {
        return this.intersects(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    public boolean contains(Vec3 vec) {
        return this.contains(vec.x, vec.y, vec.z);
    }

    public boolean contains(double x, double y, double z) {
        return x >= this.minX && x < this.maxX && y >= this.minY && y < this.maxY && z >= this.minZ && z < this.maxZ;
    }

    public double getSize() {
        double xsize = this.getXsize();
        double ysize = this.getYsize();
        double zsize = this.getZsize();
        return (xsize + ysize + zsize) / 3.0;
    }

    public double getXsize() {
        return this.maxX - this.minX;
    }

    public double getYsize() {
        return this.maxY - this.minY;
    }

    public double getZsize() {
        return this.maxZ - this.minZ;
    }

    public AABB deflate(double x, double y, double z) {
        return this.inflate(-x, -y, -z);
    }

    public AABB deflate(double value) {
        return this.inflate(-value);
    }

    public Optional<Vec3> clip(Vec3 from, Vec3 to) {
        return clip(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ, from, to);
    }

    public static Optional<Vec3> clip(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Vec3 from, Vec3 to) {
        double[] doubles = new double[]{1.0};
        double d = to.x - from.x;
        double d1 = to.y - from.y;
        double d2 = to.z - from.z;
        Direction direction = getDirection(minX, minY, minZ, maxX, maxY, maxZ, from, doubles, null, d, d1, d2);
        if (direction == null) {
            return Optional.empty();
        } else {
            double d3 = doubles[0];
            return Optional.of(from.add(d3 * d, d3 * d1, d3 * d2));
        }
    }

    public static @Nullable BlockHitResult clip(Iterable<AABB> boxes, Vec3 start, Vec3 end, BlockPos pos) {
        double[] doubles = new double[]{1.0};
        Direction direction = null;
        double d = end.x - start.x;
        double d1 = end.y - start.y;
        double d2 = end.z - start.z;

        for (AABB aabb : boxes) {
            direction = getDirection(aabb.move(pos), start, doubles, direction, d, d1, d2);
        }

        if (direction == null) {
            return null;
        } else {
            double d3 = doubles[0];
            return new BlockHitResult(start.add(d3 * d, d3 * d1, d3 * d2), direction, pos, false);
        }
    }

    public static @Nullable Direction getDirection( // Paper - optimise collisions - public
        AABB aabb, Vec3 start, double[] minDistance, @Nullable Direction facing, double deltaX, double deltaY, double deltaZ
    ) {
        return getDirection(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ, start, minDistance, facing, deltaX, deltaY, deltaZ);
    }

    private static @Nullable Direction getDirection(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        Vec3 start,
        double[] minDistance,
        @Nullable Direction facing,
        double deltaX,
        double deltaY,
        double deltaZ
    ) {
        if (deltaX > 1.0E-7) {
            facing = clipPoint(minDistance, facing, deltaX, deltaY, deltaZ, minX, minY, maxY, minZ, maxZ, Direction.WEST, start.x, start.y, start.z);
        } else if (deltaX < -1.0E-7) {
            facing = clipPoint(minDistance, facing, deltaX, deltaY, deltaZ, maxX, minY, maxY, minZ, maxZ, Direction.EAST, start.x, start.y, start.z);
        }

        if (deltaY > 1.0E-7) {
            facing = clipPoint(minDistance, facing, deltaY, deltaZ, deltaX, minY, minZ, maxZ, minX, maxX, Direction.DOWN, start.y, start.z, start.x);
        } else if (deltaY < -1.0E-7) {
            facing = clipPoint(minDistance, facing, deltaY, deltaZ, deltaX, maxY, minZ, maxZ, minX, maxX, Direction.UP, start.y, start.z, start.x);
        }

        if (deltaZ > 1.0E-7) {
            facing = clipPoint(minDistance, facing, deltaZ, deltaX, deltaY, minZ, minX, maxX, minY, maxY, Direction.NORTH, start.z, start.x, start.y);
        } else if (deltaZ < -1.0E-7) {
            facing = clipPoint(minDistance, facing, deltaZ, deltaX, deltaY, maxZ, minX, maxX, minY, maxY, Direction.SOUTH, start.z, start.x, start.y);
        }

        return facing;
    }

    private static @Nullable Direction clipPoint(
        double[] minDistance,
        @Nullable Direction prevDirection,
        double distanceSide,
        double distanceOtherA,
        double distanceOtherB,
        double minSide,
        double minOtherA,
        double maxOtherA,
        double minOtherB,
        double maxOtherB,
        Direction hitSide,
        double startSide,
        double startOtherA,
        double startOtherB
    ) {
        double d = (minSide - startSide) / distanceSide;
        double d1 = startOtherA + d * distanceOtherA;
        double d2 = startOtherB + d * distanceOtherB;
        if (0.0 < d && d < minDistance[0] && minOtherA - 1.0E-7 < d1 && d1 < maxOtherA + 1.0E-7 && minOtherB - 1.0E-7 < d2 && d2 < maxOtherB + 1.0E-7) {
            minDistance[0] = d;
            return hitSide;
        } else {
            return prevDirection;
        }
    }

    public boolean collidedAlongVector(Vec3 vector, List<AABB> boxes) {
        Vec3 center = this.getCenter();
        Vec3 vec3 = center.add(vector);

        for (AABB aabb : boxes) {
            AABB aabb1 = aabb.inflate(this.getXsize() * 0.5 - 1.0E-7, this.getYsize() * 0.5 - 1.0E-7, this.getZsize() * 0.5 - 1.0E-7);
            if (aabb1.contains(vec3) || aabb1.contains(center)) {
                return true;
            }

            if (aabb1.clip(center, vec3).isPresent()) {
                return true;
            }
        }

        return false;
    }

    public double distanceToSqr(Vec3 vec) {
        double max = Math.max(Math.max(this.minX - vec.x, vec.x - this.maxX), 0.0);
        double max1 = Math.max(Math.max(this.minY - vec.y, vec.y - this.maxY), 0.0);
        double max2 = Math.max(Math.max(this.minZ - vec.z, vec.z - this.maxZ), 0.0);
        return Mth.lengthSquared(max, max1, max2);
    }

    public double distanceToSqr(AABB box) {
        double max = Math.max(Math.max(this.minX - box.maxX, box.minX - this.maxX), 0.0);
        double max1 = Math.max(Math.max(this.minY - box.maxY, box.minY - this.maxY), 0.0);
        double max2 = Math.max(Math.max(this.minZ - box.maxZ, box.minZ - this.maxZ), 0.0);
        return Mth.lengthSquared(max, max1, max2);
    }

    @Override
    public String toString() {
        return "AABB[" + this.minX + ", " + this.minY + ", " + this.minZ + "] -> [" + this.maxX + ", " + this.maxY + ", " + this.maxZ + "]";
    }

    public boolean hasNaN() {
        return Double.isNaN(this.minX)
            || Double.isNaN(this.minY)
            || Double.isNaN(this.minZ)
            || Double.isNaN(this.maxX)
            || Double.isNaN(this.maxY)
            || Double.isNaN(this.maxZ);
    }

    public Vec3 getCenter() {
        return new Vec3(Mth.lerp(0.5, this.minX, this.maxX), Mth.lerp(0.5, this.minY, this.maxY), Mth.lerp(0.5, this.minZ, this.maxZ));
    }

    public Vec3 getBottomCenter() {
        return new Vec3(Mth.lerp(0.5, this.minX, this.maxX), this.minY, Mth.lerp(0.5, this.minZ, this.maxZ));
    }

    public Vec3 getMinPosition() {
        return new Vec3(this.minX, this.minY, this.minZ);
    }

    public Vec3 getMaxPosition() {
        return new Vec3(this.maxX, this.maxY, this.maxZ);
    }

    public static AABB ofSize(Vec3 center, double xSize, double ySize, double zSize) {
        return new AABB(
            center.x - xSize / 2.0, center.y - ySize / 2.0, center.z - zSize / 2.0, center.x + xSize / 2.0, center.y + ySize / 2.0, center.z + zSize / 2.0
        );
    }

    public static class Builder {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;

        public void include(Vector3fc pos) {
            this.minX = Math.min(this.minX, pos.x());
            this.minY = Math.min(this.minY, pos.y());
            this.minZ = Math.min(this.minZ, pos.z());
            this.maxX = Math.max(this.maxX, pos.x());
            this.maxY = Math.max(this.maxY, pos.y());
            this.maxZ = Math.max(this.maxZ, pos.z());
        }

        public AABB build() {
            return new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        }
    }
}
