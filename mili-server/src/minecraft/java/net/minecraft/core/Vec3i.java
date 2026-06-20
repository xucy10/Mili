package net.minecraft.core;

import com.google.common.base.MoreObjects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.util.stream.IntStream;
import javax.annotation.concurrent.Immutable;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Vector3i;

@Immutable
public class Vec3i implements Comparable<Vec3i> {
    public static final Codec<Vec3i> CODEC = Codec.INT_STREAM
        .comapFlatMap(
            positions -> Util.fixedSize(positions, 3).map(positions1 -> new Vec3i(positions1[0], positions1[1], positions1[2])),
            vec3i -> IntStream.of(vec3i.getX(), vec3i.getY(), vec3i.getZ())
        );
    public static final StreamCodec<ByteBuf, Vec3i> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, Vec3i::getX, ByteBufCodecs.VAR_INT, Vec3i::getY, ByteBufCodecs.VAR_INT, Vec3i::getZ, Vec3i::new
    );
    public static final Vec3i ZERO = new Vec3i(0, 0, 0);
    protected int x; // Paper - Perf: Manually inline methods in BlockPosition; protected
    protected int y; // Paper - Perf: Manually inline methods in BlockPosition; protected
    protected int z; // Paper - Perf: Manually inline methods in BlockPosition; protected

    public static Codec<Vec3i> offsetCodec(int maxOffset) {
        return CODEC.validate(
            vec3i -> Math.abs(vec3i.getX()) < maxOffset && Math.abs(vec3i.getY()) < maxOffset && Math.abs(vec3i.getZ()) < maxOffset
                ? DataResult.success(vec3i)
                : DataResult.error(() -> "Position out of range, expected at most " + maxOffset + ": " + vec3i)
        );
    }

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public final boolean equals(Object other) { // Paper - Perf: Final for inline
        return this == other || other instanceof Vec3i vec3i && this.getX() == vec3i.getX() && this.getY() == vec3i.getY() && this.getZ() == vec3i.getZ();
    }

    @Override
    public final int hashCode() { // Paper - Perf: Final for inline
        return (this.getY() + this.getZ() * 31) * 31 + this.getX();
    }

    @Override
    public int compareTo(Vec3i other) {
        if (this.getY() == other.getY()) {
            return this.getZ() == other.getZ() ? this.getX() - other.getX() : this.getZ() - other.getZ();
        } else {
            return this.getY() - other.getY();
        }
    }

    public final int getX() { // Paper - Perf: Final for inline
        return this.x;
    }

    public final int getY() { // Paper - Perf: Final for inline
        return this.y;
    }

    public final int getZ() { // Paper - Perf: Final for inline
        return this.z;
    }

    protected Vec3i setX(int x) {
        this.x = x;
        return this;
    }

    protected Vec3i setY(int y) {
        this.y = y;
        return this;
    }

    protected Vec3i setZ(int z) {
        this.z = z;
        return this;
    }

    public Vec3i offset(int dx, int dy, int dz) {
        return dx == 0 && dy == 0 && dz == 0 ? this : new Vec3i(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
    }

    public Vec3i offset(Vec3i vector) {
        return this.offset(vector.getX(), vector.getY(), vector.getZ());
    }

    public Vec3i subtract(Vec3i vector) {
        return this.offset(-vector.getX(), -vector.getY(), -vector.getZ());
    }

    public Vec3i multiply(int scalar) {
        if (scalar == 1) {
            return this;
        } else {
            return scalar == 0 ? ZERO : new Vec3i(this.getX() * scalar, this.getY() * scalar, this.getZ() * scalar);
        }
    }

    public Vec3i multiply(int factorX, int factorY, int factorZ) {
        return new Vec3i(this.getX() * factorX, this.getY() * factorY, this.getZ() * factorZ);
    }

    public Vec3i above() {
        return this.above(1);
    }

    public Vec3i above(int distance) {
        return this.relative(Direction.UP, distance);
    }

    public Vec3i below() {
        return this.below(1);
    }

    public Vec3i below(int distance) {
        return this.relative(Direction.DOWN, distance);
    }

    public Vec3i north() {
        return this.north(1);
    }

    public Vec3i north(int distance) {
        return this.relative(Direction.NORTH, distance);
    }

    public Vec3i south() {
        return this.south(1);
    }

    public Vec3i south(int distance) {
        return this.relative(Direction.SOUTH, distance);
    }

    public Vec3i west() {
        return this.west(1);
    }

    public Vec3i west(int distance) {
        return this.relative(Direction.WEST, distance);
    }

    public Vec3i east() {
        return this.east(1);
    }

    public Vec3i east(int distance) {
        return this.relative(Direction.EAST, distance);
    }

    public Vec3i relative(Direction direction) {
        return this.relative(direction, 1);
    }

    public Vec3i relative(Direction direction, int distance) {
        return distance == 0
            ? this
            : new Vec3i(
                this.getX() + direction.getStepX() * distance, this.getY() + direction.getStepY() * distance, this.getZ() + direction.getStepZ() * distance
            );
    }

    public Vec3i relative(Direction.Axis axis, int amount) {
        if (amount == 0) {
            return this;
        } else {
            int i = axis == Direction.Axis.X ? amount : 0;
            int i1 = axis == Direction.Axis.Y ? amount : 0;
            int i2 = axis == Direction.Axis.Z ? amount : 0;
            return new Vec3i(this.getX() + i, this.getY() + i1, this.getZ() + i2);
        }
    }

    public Vec3i cross(Vec3i vector) {
        return new Vec3i(
            this.getY() * vector.getZ() - this.getZ() * vector.getY(),
            this.getZ() * vector.getX() - this.getX() * vector.getZ(),
            this.getX() * vector.getY() - this.getY() * vector.getX()
        );
    }

    public boolean closerThan(Vec3i vector, double distance) {
        return this.distSqr(vector) < Mth.square(distance);
    }

    public boolean closerToCenterThan(Position position, double distance) {
        return this.distToCenterSqr(position) < Mth.square(distance);
    }

    public double distSqr(Vec3i vector) {
        return this.distToLowCornerSqr(vector.getX(), vector.getY(), vector.getZ());
    }

    public double distToCenterSqr(Position position) {
        return this.distToCenterSqr(position.x(), position.y(), position.z());
    }

    public double distToCenterSqr(double x, double y, double z) {
        double d = this.getX() + 0.5 - x;
        double d1 = this.getY() + 0.5 - y;
        double d2 = this.getZ() + 0.5 - z;
        return d * d + d1 * d1 + d2 * d2;
    }

    public double distToLowCornerSqr(double x, double y, double z) {
        double d = this.getX() - x;
        double d1 = this.getY() - y;
        double d2 = this.getZ() - z;
        return d * d + d1 * d1 + d2 * d2;
    }

    public int distManhattan(Vec3i vector) {
        float f = Math.abs(vector.getX() - this.getX());
        float f1 = Math.abs(vector.getY() - this.getY());
        float f2 = Math.abs(vector.getZ() - this.getZ());
        return (int)(f + f1 + f2);
    }

    public int distChessboard(Vec3i vector) {
        int abs = Math.abs(this.getX() - vector.getX());
        int abs1 = Math.abs(this.getY() - vector.getY());
        int abs2 = Math.abs(this.getZ() - vector.getZ());
        return Math.max(Math.max(abs, abs1), abs2);
    }

    public int get(Direction.Axis axis) {
        return axis.choose(this.x, this.y, this.z);
    }

    public Vector3i toMutable() {
        return new Vector3i(this.x, this.y, this.z);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("x", this.getX()).add("y", this.getY()).add("z", this.getZ()).toString();
    }

    public String toShortString() {
        return this.getX() + ", " + this.getY() + ", " + this.getZ();
    }

    // Paper start - Perf: Optimize isInWorldBounds
    public final boolean isInsideBuildHeightAndWorldBoundsHorizontal(final net.minecraft.world.level.LevelHeightAccessor levelHeightAccessor) {
        int maxSize = net.minecraft.world.level.Level.MAX_LEVEL_SIZE;
        return this.getX() >= -maxSize && this.getZ() >= -maxSize && this.getX() < maxSize && this.getZ() < maxSize && !levelHeightAccessor.isOutsideBuildHeight(this.getY());
    }
    // Paper end - Perf: Optimize isInWorldBounds
}
