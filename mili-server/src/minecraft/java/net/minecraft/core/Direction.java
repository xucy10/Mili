package net.minecraft.core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Contract;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public enum Direction implements StringRepresentable, ca.spottedleaf.moonrise.patches.collisions.util.CollisionDirection { // Paper - optimise collisions
    DOWN(0, 1, -1, "down", Direction.AxisDirection.NEGATIVE, Direction.Axis.Y, new Vec3i(0, -1, 0)),
    UP(1, 0, -1, "up", Direction.AxisDirection.POSITIVE, Direction.Axis.Y, new Vec3i(0, 1, 0)),
    NORTH(2, 3, 2, "north", Direction.AxisDirection.NEGATIVE, Direction.Axis.Z, new Vec3i(0, 0, -1)),
    SOUTH(3, 2, 0, "south", Direction.AxisDirection.POSITIVE, Direction.Axis.Z, new Vec3i(0, 0, 1)),
    WEST(4, 5, 1, "west", Direction.AxisDirection.NEGATIVE, Direction.Axis.X, new Vec3i(-1, 0, 0)),
    EAST(5, 4, 3, "east", Direction.AxisDirection.POSITIVE, Direction.Axis.X, new Vec3i(1, 0, 0));

    public static final StringRepresentable.EnumCodec<Direction> CODEC = StringRepresentable.fromEnum(Direction::values);
    public static final Codec<Direction> VERTICAL_CODEC = CODEC.validate(Direction::verifyVertical);
    public static final IntFunction<Direction> BY_ID = ByIdMap.continuous(Direction::get3DDataValue, values(), ByIdMap.OutOfBoundsStrategy.WRAP);
    public static final StreamCodec<ByteBuf, Direction> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Direction::get3DDataValue);
    @Deprecated
    public static final Codec<Direction> LEGACY_ID_CODEC = Codec.BYTE.xmap(Direction::from3DDataValue, direction -> (byte)direction.get3DDataValue());
    @Deprecated
    public static final Codec<Direction> LEGACY_ID_CODEC_2D = Codec.BYTE.xmap(Direction::from2DDataValue, direction -> (byte)direction.get2DDataValue());
    private static final ImmutableList<Direction.Axis> YXZ_AXIS_ORDER = ImmutableList.of(Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z);
    private static final ImmutableList<Direction.Axis> YZX_AXIS_ORDER = ImmutableList.of(Direction.Axis.Y, Direction.Axis.Z, Direction.Axis.X);
    private final int data3d;
    private final int oppositeIndex;
    private final int data2d;
    private final String name;
    private final Direction.Axis axis;
    private final Direction.AxisDirection axisDirection;
    private final Vec3i normal;
    private final Vec3 normalVec3;
    private final Vector3fc normalVec3f;
    private static final Direction[] VALUES = values();
    private static final Direction[] BY_3D_DATA = Arrays.stream(VALUES)
        .sorted(Comparator.comparingInt(direction -> direction.data3d))
        .toArray(Direction[]::new);
    private static final Direction[] BY_2D_DATA = Arrays.stream(VALUES)
        .filter(direction -> direction.getAxis().isHorizontal())
        .sorted(Comparator.comparingInt(direction -> direction.data2d))
        .toArray(Direction[]::new);

    // Paper start - Perf: Inline shift direction fields
    private final int adjX;
    private final int adjY;
    private final int adjZ;
    // Paper end - Perf: Inline shift direction fields
    // Paper start - optimise collisions
    private static final int RANDOM_OFFSET = 2017601568;
    private Direction opposite;
    private Quaternionf rotation;
    private int id;
    private int stepX;
    private int stepY;
    private int stepZ;

    private Quaternionf getRotationUncached() {
        switch ((Direction)(Object)this) {
            case DOWN: {
                return new Quaternionf().rotationX(3.1415927F);
            }
            case UP: {
                return new Quaternionf();
            }
            case NORTH: {
                return new Quaternionf().rotationXYZ(1.5707964F, 0.0F, 3.1415927F);
            }
            case SOUTH: {
                return new Quaternionf().rotationX(1.5707964F);
            }
            case WEST: {
                return new Quaternionf().rotationXYZ(1.5707964F, 0.0F, 1.5707964F);
            }
            case EAST: {
                return new Quaternionf().rotationXYZ(1.5707964F, 0.0F, -1.5707964F);
            }
            default: {
                throw new IllegalStateException();
            }
        }
    }

    @Override
    public final int moonrise$uniqueId() {
        return this.id;
    }
    // Paper end - optimise collisions

    private Direction(
        final int data3d,
        final int oppositeIndex,
        final int data2d,
        final String name,
        final Direction.AxisDirection axisDirection,
        final Direction.Axis axis,
        final Vec3i normal
    ) {
        this.data3d = data3d;
        this.data2d = data2d;
        this.oppositeIndex = oppositeIndex;
        this.name = name;
        this.axis = axis;
        this.axisDirection = axisDirection;
        this.normal = normal;
        this.normalVec3 = Vec3.atLowerCornerOf(normal);
        this.normalVec3f = new Vector3f(normal.getX(), normal.getY(), normal.getZ());
        // Paper start - Perf: Inline shift direction fields
        this.adjX = normal.getX();
        this.adjY = normal.getY();
        this.adjZ = normal.getZ();
        // Paper end - Perf: Inline shift direction fields
    }

    public static Direction[] orderedByNearest(Entity entity) {
        float f = entity.getViewXRot(1.0F) * (float) (Math.PI / 180.0);
        float f1 = -(fun.bm.mili.carpet.config.modules.GeneralCompatConfig.placementRotationFix ? entity.getYRot(1.0F) : entity.getViewYRot(1.0F)) * (float) (Math.PI / 180.0);
        float sin = Mth.sin(f);
        float cos = Mth.cos(f);
        float sin1 = Mth.sin(f1);
        float cos1 = Mth.cos(f1);
        boolean flag = sin1 > 0.0F;
        boolean flag1 = sin < 0.0F;
        boolean flag2 = cos1 > 0.0F;
        float f2 = flag ? sin1 : -sin1;
        float f3 = flag1 ? -sin : sin;
        float f4 = flag2 ? cos1 : -cos1;
        float f5 = f2 * cos;
        float f6 = f4 * cos;
        Direction direction = flag ? EAST : WEST;
        Direction direction1 = flag1 ? UP : DOWN;
        Direction direction2 = flag2 ? SOUTH : NORTH;
        if (f2 > f4) {
            if (f3 > f5) {
                return makeDirectionArray(direction1, direction, direction2);
            } else {
                return f6 > f3 ? makeDirectionArray(direction, direction2, direction1) : makeDirectionArray(direction, direction1, direction2);
            }
        } else if (f3 > f6) {
            return makeDirectionArray(direction1, direction2, direction);
        } else {
            return f5 > f3 ? makeDirectionArray(direction2, direction, direction1) : makeDirectionArray(direction2, direction1, direction);
        }
    }

    private static Direction[] makeDirectionArray(Direction first, Direction second, Direction third) {
        return new Direction[]{first, second, third, third.getOpposite(), second.getOpposite(), first.getOpposite()};
    }

    public static Direction rotate(Matrix4fc matrix, Direction direction) {
        Vector3f vector3f = matrix.transformDirection(direction.normalVec3f, new Vector3f());
        return getApproximateNearest(vector3f.x(), vector3f.y(), vector3f.z());
    }

    public static Collection<Direction> allShuffled(RandomSource random) {
        return Util.shuffledCopy(values(), random);
    }

    public static Stream<Direction> stream() {
        return Stream.of(VALUES);
    }

    public static float getYRot(Direction direction) {
        return switch (direction) {
            case NORTH -> 180.0F;
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> throw new IllegalStateException("No y-Rot for vertical axis: " + direction);
        };
    }

    public Quaternionf getRotation() {
        // Paper start - optimise collisions
        try {
            return (Quaternionf)this.rotation.clone();
        } catch (final CloneNotSupportedException ex) {
            throw new InternalError(ex);
        }
        // Paper end - optimise collisions
    }

    public int get3DDataValue() {
        return this.data3d;
    }

    public int get2DDataValue() {
        return this.data2d;
    }

    public Direction.AxisDirection getAxisDirection() {
        return this.axisDirection;
    }

    public static Direction getFacingAxis(Entity entity, Direction.Axis axis) {
        return switch (axis) {
            case X -> EAST.isFacingAngle(entity.getViewYRot(1.0F)) ? EAST : WEST;
            case Y -> entity.getViewXRot(1.0F) < 0.0F ? UP : DOWN;
            case Z -> SOUTH.isFacingAngle(entity.getViewYRot(1.0F)) ? SOUTH : NORTH;
        };
    }

    public Direction getOpposite() {
        return VALUES[this.oppositeIndex]; // Leaf - Lithium - fast util
    }

    public Direction getClockWise(Direction.Axis axis) {
        return switch (axis) {
            case X -> this != WEST && this != EAST ? this.getClockWiseX() : this;
            case Y -> this != UP && this != DOWN ? this.getClockWise() : this;
            case Z -> this != NORTH && this != SOUTH ? this.getClockWiseZ() : this;
        };
    }

    public Direction getCounterClockWise(Direction.Axis axis) {
        return switch (axis) {
            case X -> this != WEST && this != EAST ? this.getCounterClockWiseX() : this;
            case Y -> this != UP && this != DOWN ? this.getCounterClockWise() : this;
            case Z -> this != NORTH && this != SOUTH ? this.getCounterClockWiseZ() : this;
        };
    }

    public Direction getClockWise() {
        return switch (this) {
            case NORTH -> EAST;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            case EAST -> SOUTH;
            default -> throw new IllegalStateException("Unable to get Y-rotated facing of " + this);
        };
    }

    private Direction getClockWiseX() {
        return switch (this) {
            case DOWN -> SOUTH;
            case UP -> NORTH;
            case NORTH -> DOWN;
            case SOUTH -> UP;
            default -> throw new IllegalStateException("Unable to get X-rotated facing of " + this);
        };
    }

    private Direction getCounterClockWiseX() {
        return switch (this) {
            case DOWN -> NORTH;
            case UP -> SOUTH;
            case NORTH -> UP;
            case SOUTH -> DOWN;
            default -> throw new IllegalStateException("Unable to get X-rotated facing of " + this);
        };
    }

    private Direction getClockWiseZ() {
        return switch (this) {
            case DOWN -> WEST;
            case UP -> EAST;
            default -> throw new IllegalStateException("Unable to get Z-rotated facing of " + this);
            case WEST -> UP;
            case EAST -> DOWN;
        };
    }

    private Direction getCounterClockWiseZ() {
        return switch (this) {
            case DOWN -> EAST;
            case UP -> WEST;
            default -> throw new IllegalStateException("Unable to get Z-rotated facing of " + this);
            case WEST -> DOWN;
            case EAST -> UP;
        };
    }

    public Direction getCounterClockWise() {
        return switch (this) {
            case NORTH -> WEST;
            case SOUTH -> EAST;
            case WEST -> SOUTH;
            case EAST -> NORTH;
            default -> throw new IllegalStateException("Unable to get CCW facing of " + this);
        };
    }

    public int getStepX() {
        return this.adjX; // Paper - Perf: Inline shift direction fields
    }

    public int getStepY() {
        return this.adjY; // Paper - Perf: Inline shift direction fields
    }

    public int getStepZ() {
        return this.adjZ; // Paper - Perf: Inline shift direction fields
    }

    public Vector3f step() {
        return new Vector3f(this.normalVec3f);
    }

    public String getName() {
        return this.name;
    }

    public Direction.Axis getAxis() {
        return this.axis;
    }

    public static @Nullable Direction byName(String name) {
        return CODEC.byName(name);
    }

    public static Direction from3DDataValue(int index) {
        return BY_3D_DATA[Mth.abs(index % BY_3D_DATA.length)];
    }

    public static Direction from2DDataValue(int horizontalIndex) {
        return BY_2D_DATA[Mth.abs(horizontalIndex % BY_2D_DATA.length)];
    }

    public static Direction fromYRot(double yRot) {
        return from2DDataValue(Mth.floor(yRot / 90.0 + 0.5) & 3);
    }

    public static Direction fromAxisAndDirection(Direction.Axis axis, Direction.AxisDirection direction) {
        return switch (axis) {
            case X -> direction == Direction.AxisDirection.POSITIVE ? EAST : WEST;
            case Y -> direction == Direction.AxisDirection.POSITIVE ? UP : DOWN;
            case Z -> direction == Direction.AxisDirection.POSITIVE ? SOUTH : NORTH;
        };
    }

    public float toYRot() {
        return (this.data2d & 3) * 90;
    }

    public static Direction getRandom(RandomSource random) {
        return VALUES[random.nextInt(VALUES.length)]; // Leaf - Lithium - fast util
    }

    public static Direction getApproximateNearest(double x, double y, double z) {
        return getApproximateNearest((float)x, (float)y, (float)z);
    }

    public static Direction getApproximateNearest(float x, float y, float z) {
        Direction direction = NORTH;
        float f = Float.MIN_VALUE;

        for (Direction direction1 : VALUES) {
            float f1 = x * direction1.normal.getX() + y * direction1.normal.getY() + z * direction1.normal.getZ();
            if (f1 > f) {
                f = f1;
                direction = direction1;
            }
        }

        return direction;
    }

    public static Direction getApproximateNearest(Vec3 vector) {
        return getApproximateNearest(vector.x, vector.y, vector.z);
    }

    @Contract("_,_,_,!null->!null;_,_,_,_->_")
    public static @Nullable Direction getNearest(int x, int y, int z, @Nullable Direction defaultValue) {
        int abs = Math.abs(x);
        int abs1 = Math.abs(y);
        int abs2 = Math.abs(z);
        if (abs > abs2 && abs > abs1) {
            return x < 0 ? WEST : EAST;
        } else if (abs2 > abs && abs2 > abs1) {
            return z < 0 ? NORTH : SOUTH;
        } else if (abs1 > abs && abs1 > abs2) {
            return y < 0 ? DOWN : UP;
        } else {
            return defaultValue;
        }
    }

    @Contract("_,!null->!null;_,_->_")
    public static @Nullable Direction getNearest(Vec3i vector, @Nullable Direction defaultValue) {
        return getNearest(vector.getX(), vector.getY(), vector.getZ(), defaultValue);
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    private static DataResult<Direction> verifyVertical(Direction direction) {
        return direction.getAxis().isVertical() ? DataResult.success(direction) : DataResult.error(() -> "Expected a vertical direction");
    }

    public static Direction get(Direction.AxisDirection direction, Direction.Axis axis) {
        for (Direction direction1 : VALUES) {
            if (direction1.getAxisDirection() == direction && direction1.getAxis() == axis) {
                return direction1;
            }
        }

        throw new IllegalArgumentException("No such direction: " + direction + " " + axis);
    }

    public static ImmutableList<Direction.Axis> axisStepOrder(Vec3 vector) {
        return Math.abs(vector.x) < Math.abs(vector.z) ? YZX_AXIS_ORDER : YXZ_AXIS_ORDER;
    }

    public Vec3i getUnitVec3i() {
        return this.normal;
    }

    public Vec3 getUnitVec3() {
        return this.normalVec3;
    }

    public Vector3fc getUnitVec3f() {
        return this.normalVec3f;
    }

    public boolean isFacingAngle(float angle) {
        float f = angle * (float) (Math.PI / 180.0);
        float f1 = -Mth.sin(f);
        float cos = Mth.cos(f);
        return this.normal.getX() * f1 + this.normal.getZ() * cos > 0.0F;
    }

    public static enum Axis implements StringRepresentable, Predicate<Direction> {
        X("x") {
            @Override
            public int choose(int x, int y, int z) {
                return x;
            }

            @Override
            public boolean choose(boolean x, boolean y, boolean z) {
                return x;
            }

            @Override
            public double choose(double x, double y, double z) {
                return x;
            }

            @Override
            public Direction getPositive() {
                return Direction.EAST;
            }

            @Override
            public Direction getNegative() {
                return Direction.WEST;
            }
        },
        Y("y") {
            @Override
            public int choose(int x, int y, int z) {
                return y;
            }

            @Override
            public double choose(double x, double y, double z) {
                return y;
            }

            @Override
            public boolean choose(boolean x, boolean y, boolean z) {
                return y;
            }

            @Override
            public Direction getPositive() {
                return Direction.UP;
            }

            @Override
            public Direction getNegative() {
                return Direction.DOWN;
            }
        },
        Z("z") {
            @Override
            public int choose(int x, int y, int z) {
                return z;
            }

            @Override
            public double choose(double x, double y, double z) {
                return z;
            }

            @Override
            public boolean choose(boolean x, boolean y, boolean z) {
                return z;
            }

            @Override
            public Direction getPositive() {
                return Direction.SOUTH;
            }

            @Override
            public Direction getNegative() {
                return Direction.NORTH;
            }
        };

        public static final Direction.Axis[] VALUES = values();
        public static final StringRepresentable.EnumCodec<Direction.Axis> CODEC = StringRepresentable.fromEnum(Direction.Axis::values);
        private final String name;

        Axis(final String name) {
            this.name = name;
        }

        public static Direction.@Nullable Axis byName(String name) {
            return CODEC.byName(name);
        }

        public String getName() {
            return this.name;
        }

        public boolean isVertical() {
            return this == Y;
        }

        public boolean isHorizontal() {
            return this == X || this == Z;
        }

        public abstract Direction getPositive();

        public abstract Direction getNegative();

        public Direction[] getDirections() {
            return new Direction[]{this.getPositive(), this.getNegative()};
        }

        @Override
        public String toString() {
            return this.name;
        }

        public static Direction.Axis getRandom(RandomSource random) {
            return Util.getRandom(VALUES, random);
        }

        @Override
        public boolean test(@Nullable Direction direction) {
            return direction != null && direction.getAxis() == this;
        }

        public Direction.Plane getPlane() {
            return switch (this) {
                case X, Z -> Direction.Plane.HORIZONTAL;
                case Y -> Direction.Plane.VERTICAL;
            };
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public abstract int choose(int x, int y, int z);

        public abstract double choose(double x, double y, double z);

        public abstract boolean choose(boolean x, boolean y, boolean z);
    }

    public static enum AxisDirection {
        POSITIVE(1, "Towards positive"),
        NEGATIVE(-1, "Towards negative");

        private final int step;
        private final String name;

        private AxisDirection(final int step, final String name) {
            this.step = step;
            this.name = name;
        }

        public int getStep() {
            return this.step;
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String toString() {
            return this.name;
        }

        public Direction.AxisDirection opposite() {
            return this == POSITIVE ? NEGATIVE : POSITIVE;
        }
    }

    public static enum Plane implements Iterable<Direction>, Predicate<Direction> {
        HORIZONTAL(new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}, new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}),
        VERTICAL(new Direction[]{Direction.UP, Direction.DOWN}, new Direction.Axis[]{Direction.Axis.Y});

        private final Direction[] faces;
        private final Direction.Axis[] axis;

        private Plane(final Direction[] faces, final Direction.Axis[] axis) {
            this.faces = faces;
            this.axis = axis;
        }

        public Direction getRandomDirection(RandomSource random) {
            return Util.getRandom(this.faces, random);
        }

        public Direction.Axis getRandomAxis(RandomSource random) {
            return Util.getRandom(this.axis, random);
        }

        @Override
        public boolean test(@Nullable Direction direction) {
            return direction != null && direction.getAxis().getPlane() == this;
        }

        @Override
        public Iterator<Direction> iterator() {
            return Iterators.forArray(this.faces);
        }

        public Stream<Direction> stream() {
            return Arrays.stream(this.faces);
        }

        public List<Direction> shuffledCopy(RandomSource random) {
            return Util.shuffledCopy(this.faces, random);
        }

        public int length() {
            return this.faces.length;
        }
    }

    // Paper start - optimise collisions
    static {
        for (final Direction direction : VALUES) {
            ((Direction)(Object)direction).opposite = from3DDataValue(((Direction)(Object)direction).oppositeIndex);
            ((Direction)(Object)direction).rotation = ((Direction)(Object)direction).getRotationUncached();
            ((Direction)(Object)direction).id = it.unimi.dsi.fastutil.HashCommon.murmurHash3(it.unimi.dsi.fastutil.HashCommon.murmurHash3(direction.ordinal() + RANDOM_OFFSET) + RANDOM_OFFSET);
            ((Direction)(Object)direction).stepX = ((Direction)(Object)direction).normal.getX();
            ((Direction)(Object)direction).stepY = ((Direction)(Object)direction).normal.getY();
            ((Direction)(Object)direction).stepZ = ((Direction)(Object)direction).normal.getZ();
        }
    }
    // Paper end - optimise collisions
}
