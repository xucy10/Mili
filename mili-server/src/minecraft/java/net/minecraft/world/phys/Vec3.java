package net.minecraft.world.phys;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class Vec3 implements Position {
    public static final Codec<Vec3> CODEC = Codec.DOUBLE
        .listOf()
        .comapFlatMap(
            list -> Util.fixedSize((List<Double>)list, 3).map(list1 -> new Vec3(list1.get(0), list1.get(1), list1.get(2))),
            vec3 -> List.of(vec3.x(), vec3.y(), vec3.z())
        );
    public static final StreamCodec<ByteBuf, Vec3> STREAM_CODEC = new StreamCodec<ByteBuf, Vec3>() {
        @Override
        public Vec3 decode(ByteBuf buffer) {
            return FriendlyByteBuf.readVec3(buffer);
        }

        @Override
        public void encode(ByteBuf buffer, Vec3 value) {
            FriendlyByteBuf.writeVec3(buffer, value);
        }
    };
    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);
    public static final Vec3 X_AXIS = new Vec3(1.0, 0.0, 0.0);
    public static final Vec3 Y_AXIS = new Vec3(0.0, 1.0, 0.0);
    public static final Vec3 Z_AXIS = new Vec3(0.0, 0.0, 1.0);
    public final double x;
    public final double y;
    public final double z;

    public static Vec3 atLowerCornerOf(Vec3i toCopy) {
        return new Vec3(toCopy.getX(), toCopy.getY(), toCopy.getZ());
    }

    public static Vec3 atLowerCornerWithOffset(Vec3i toCopy, double offsetX, double offsetY, double offsetZ) {
        return new Vec3(toCopy.getX() + offsetX, toCopy.getY() + offsetY, toCopy.getZ() + offsetZ);
    }

    public static Vec3 atCenterOf(Vec3i toCopy) {
        return atLowerCornerWithOffset(toCopy, 0.5, 0.5, 0.5);
    }

    public static Vec3 atBottomCenterOf(Vec3i toCopy) {
        return atLowerCornerWithOffset(toCopy, 0.5, 0.0, 0.5);
    }

    public static Vec3 upFromBottomCenterOf(Vec3i toCopy, double verticalOffset) {
        return atLowerCornerWithOffset(toCopy, 0.5, verticalOffset, 0.5);
    }

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vector3fc vector) {
        this(vector.x(), vector.y(), vector.z());
    }

    public Vec3(Vec3i vector) {
        this(vector.getX(), vector.getY(), vector.getZ());
    }

    public Vec3 vectorTo(Vec3 vec) {
        return new Vec3(vec.x - this.x, vec.y - this.y, vec.z - this.z);
    }

    public Vec3 normalize() {
        double squareRoot = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        return squareRoot < 1.0E-5F ? ZERO : new Vec3(this.x / squareRoot, this.y / squareRoot, this.z / squareRoot);
    }

    public double dot(Vec3 vec) {
        return this.x * vec.x + this.y * vec.y + this.z * vec.z;
    }

    public Vec3 cross(Vec3 vec) {
        return new Vec3(this.y * vec.z - this.z * vec.y, this.z * vec.x - this.x * vec.z, this.x * vec.y - this.y * vec.x);
    }

    public Vec3 subtract(Vec3 vec) {
        return this.subtract(vec.x, vec.y, vec.z);
    }

    public Vec3 subtract(double amount) {
        return this.subtract(amount, amount, amount);
    }

    public Vec3 subtract(double x, double y, double z) {
        return this.add(-x, -y, -z);
    }

    public Vec3 add(double amount) {
        return this.add(amount, amount, amount);
    }

    public Vec3 add(Vec3 vec) {
        return this.add(vec.x, vec.y, vec.z);
    }

    public Vec3 add(double x, double y, double z) {
        return new Vec3(this.x + x, this.y + y, this.z + z);
    }

    public boolean closerThan(Position pos, double distance) {
        return this.distanceToSqr(pos.x(), pos.y(), pos.z()) < distance * distance;
    }

    public double distanceTo(Vec3 vec) {
        double d = vec.x - this.x;
        double d1 = vec.y - this.y;
        double d2 = vec.z - this.z;
        return Math.sqrt(d * d + d1 * d1 + d2 * d2);
    }

    public double distanceToSqr(Vec3 vec) {
        double d = vec.x - this.x;
        double d1 = vec.y - this.y;
        double d2 = vec.z - this.z;
        return d * d + d1 * d1 + d2 * d2;
    }

    public double distanceToSqr(double x, double y, double z) {
        double d = x - this.x;
        double d1 = y - this.y;
        double d2 = z - this.z;
        return d * d + d1 * d1 + d2 * d2;
    }

    public boolean closerThan(Vec3 pos, double horizontalDistance, double verticalDistance) {
        double d = pos.x() - this.x;
        double d1 = pos.y() - this.y;
        double d2 = pos.z() - this.z;
        return Mth.lengthSquared(d, d2) < Mth.square(horizontalDistance) && Math.abs(d1) < verticalDistance;
    }

    public Vec3 scale(double factor) {
        return this.multiply(factor, factor, factor);
    }

    public Vec3 reverse() {
        return this.scale(-1.0);
    }

    public Vec3 multiply(Vec3 vec) {
        return this.multiply(vec.x, vec.y, vec.z);
    }

    public Vec3 multiply(double factorX, double factorY, double factorZ) {
        return new Vec3(this.x * factorX, this.y * factorY, this.z * factorZ);
    }

    public Vec3 horizontal() {
        return new Vec3(this.x, 0.0, this.z);
    }

    public Vec3 offsetRandom(RandomSource random, float factor) {
        return this.add((random.nextFloat() - 0.5F) * factor, (random.nextFloat() - 0.5F) * factor, (random.nextFloat() - 0.5F) * factor);
    }

    public Vec3 offsetRandomXZ(RandomSource random, float factor) {
        return this.add((random.nextFloat() - 0.5F) * factor, 0.0, (random.nextFloat() - 0.5F) * factor);
    }

    public double length() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    public double lengthSqr() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    public double horizontalDistance() {
        return Math.sqrt(this.x * this.x + this.z * this.z);
    }

    public double horizontalDistanceSqr() {
        return this.x * this.x + this.z * this.z;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof Vec3 vec3 && Double.compare(vec3.x, this.x) == 0 && Double.compare(vec3.y, this.y) == 0 && Double.compare(vec3.z, this.z) == 0;
    }

    @Override
    public int hashCode() {
        long l = Double.doubleToLongBits(this.x);
        int i = (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.y);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.z);
        return 31 * i + (int)(l ^ l >>> 32);
    }

    @Override
    public String toString() {
        return "(" + this.x + ", " + this.y + ", " + this.z + ")";
    }

    public Vec3 lerp(Vec3 to, double delta) {
        return new Vec3(Mth.lerp(delta, this.x, to.x), Mth.lerp(delta, this.y, to.y), Mth.lerp(delta, this.z, to.z));
    }

    public Vec3 xRot(float pitch) {
        float cos = Mth.cos(pitch);
        float sin = Mth.sin(pitch);
        double d = this.x;
        double d1 = this.y * cos + this.z * sin;
        double d2 = this.z * cos - this.y * sin;
        return new Vec3(d, d1, d2);
    }

    public Vec3 yRot(float yaw) {
        float cos = Mth.cos(yaw);
        float sin = Mth.sin(yaw);
        double d = this.x * cos + this.z * sin;
        double d1 = this.y;
        double d2 = this.z * cos - this.x * sin;
        return new Vec3(d, d1, d2);
    }

    public Vec3 zRot(float roll) {
        float cos = Mth.cos(roll);
        float sin = Mth.sin(roll);
        double d = this.x * cos + this.y * sin;
        double d1 = this.y * cos - this.x * sin;
        double d2 = this.z;
        return new Vec3(d, d1, d2);
    }

    public Vec3 rotateClockwise90() {
        return new Vec3(-this.z, this.y, this.x);
    }

    public static Vec3 directionFromRotation(Vec2 rot) {
        return directionFromRotation(rot.x, rot.y);
    }

    public static Vec3 directionFromRotation(float pitch, float yaw) {
        float cos = Mth.cos(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float sin = Mth.sin(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f = -Mth.cos(-pitch * (float) (Math.PI / 180.0));
        float sin1 = Mth.sin(-pitch * (float) (Math.PI / 180.0));
        return new Vec3(sin * f, sin1, cos * f);
    }

    public Vec2 rotation() {
        float f = (float)Math.atan2(-this.x, this.z) * (180.0F / (float)Math.PI);
        float f1 = (float)Math.asin(-this.y / Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z)) * (180.0F / (float)Math.PI);
        return new Vec2(f1, f);
    }

    public Vec3 align(EnumSet<Direction.Axis> axes) {
        double d = axes.contains(Direction.Axis.X) ? Mth.floor(this.x) : this.x;
        double d1 = axes.contains(Direction.Axis.Y) ? Mth.floor(this.y) : this.y;
        double d2 = axes.contains(Direction.Axis.Z) ? Mth.floor(this.z) : this.z;
        return new Vec3(d, d1, d2);
    }

    public double get(Direction.Axis axis) {
        return axis.choose(this.x, this.y, this.z);
    }

    public Vec3 with(Direction.Axis axis, double length) {
        double d = axis == Direction.Axis.X ? length : this.x;
        double d1 = axis == Direction.Axis.Y ? length : this.y;
        double d2 = axis == Direction.Axis.Z ? length : this.z;
        return new Vec3(d, d1, d2);
    }

    public Vec3 relative(Direction direction, double length) {
        Vec3i unitVec3i = direction.getUnitVec3i();
        return new Vec3(this.x + length * unitVec3i.getX(), this.y + length * unitVec3i.getY(), this.z + length * unitVec3i.getZ());
    }

    @Override
    public final double x() {
        return this.x;
    }

    @Override
    public final double y() {
        return this.y;
    }

    @Override
    public final double z() {
        return this.z;
    }

    public Vector3f toVector3f() {
        return new Vector3f((float)this.x, (float)this.y, (float)this.z);
    }

    public Vec3 projectedOn(Vec3 vector) {
        return vector.lengthSqr() == 0.0 ? vector : vector.scale(this.dot(vector)).scale(1.0 / vector.lengthSqr());
    }

    public static Vec3 applyLocalCoordinatesToRotation(Vec2 rotation, Vec3 pos) {
        float cos = Mth.cos((rotation.y + 90.0F) * (float) (Math.PI / 180.0));
        float sin = Mth.sin((rotation.y + 90.0F) * (float) (Math.PI / 180.0));
        float cos1 = Mth.cos(-rotation.x * (float) (Math.PI / 180.0));
        float sin1 = Mth.sin(-rotation.x * (float) (Math.PI / 180.0));
        float cos2 = Mth.cos((-rotation.x + 90.0F) * (float) (Math.PI / 180.0));
        float sin2 = Mth.sin((-rotation.x + 90.0F) * (float) (Math.PI / 180.0));
        Vec3 vec3 = new Vec3(cos * cos1, sin1, sin * cos1);
        Vec3 vec31 = new Vec3(cos * cos2, sin2, sin * cos2);
        Vec3 vec32 = vec3.cross(vec31).scale(-1.0);
        double d = vec3.x * pos.z + vec31.x * pos.y + vec32.x * pos.x;
        double d1 = vec3.y * pos.z + vec31.y * pos.y + vec32.y * pos.x;
        double d2 = vec3.z * pos.z + vec31.z * pos.y + vec32.z * pos.x;
        return new Vec3(d, d1, d2);
    }

    public Vec3 addLocalCoordinates(Vec3 pos) {
        return applyLocalCoordinatesToRotation(this.rotation(), pos);
    }

    public boolean isFinite() {
        return Double.isFinite(this.x) && Double.isFinite(this.y) && Double.isFinite(this.z);
    }
}
