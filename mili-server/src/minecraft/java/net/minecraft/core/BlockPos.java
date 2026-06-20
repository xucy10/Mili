package net.minecraft.core;

import com.google.common.collect.AbstractIterator;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.concurrent.Immutable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;

@Immutable
public class BlockPos extends Vec3i {
    public static final Codec<BlockPos> CODEC = Codec.INT_STREAM
        .<BlockPos>comapFlatMap(
            positions -> Util.fixedSize(positions, 3).map(positions1 -> new BlockPos(positions1[0], positions1[1], positions1[2])),
            pos -> IntStream.of(pos.getX(), pos.getY(), pos.getZ())
        )
        .stable();
    public static final StreamCodec<ByteBuf, BlockPos> STREAM_CODEC = new StreamCodec<ByteBuf, BlockPos>() {
        @Override
        public BlockPos decode(ByteBuf buffer) {
            return FriendlyByteBuf.readBlockPos(buffer);
        }

        @Override
        public void encode(ByteBuf buffer, BlockPos value) {
            FriendlyByteBuf.writeBlockPos(buffer, value);
        }
    };
    public static final BlockPos ZERO = new BlockPos(0, 0, 0);
    // Paper start - Optimize Bit Operations by inlining
    public static final int PACKED_HORIZONTAL_LENGTH = 26;
    public static final int PACKED_Y_LENGTH = 12;
    private static final long PACKED_X_MASK = 67108863;
    private static final long PACKED_Y_MASK = 4095;
    private static final long PACKED_Z_MASK = 67108863;
    private static final int Y_OFFSET = 0;
    private static final int Z_OFFSET = 12;
    private static final int X_OFFSET = 38;
    public static final int MAX_HORIZONTAL_COORDINATE = 33554431;
    // Paper end - Optimize Bit Operations by inlining

    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    public BlockPos(Vec3i vector) {
        this(vector.getX(), vector.getY(), vector.getZ());
    }

    public static long getAdjacent(int baseX, int baseY, int baseZ, Direction direction) { return asLong(baseX + direction.getStepX(), baseY + direction.getStepY(), baseZ + direction.getStepZ()); } // Paper
    public static long offset(long pos, Direction direction) {
        return offset(pos, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    public static long offset(long pos, int dx, int dy, int dz) {
        return asLong((int) (pos >> 38) + dx, (int) ((pos << 52) >> 52) + dy, (int) ((pos << 26) >> 38) + dz); // Paper - simplify/inline
    }

    public static int getX(long packedPos) {
        return (int) (packedPos >> 38); // Paper - simplify/inline
    }

    public static int getY(long packedPos) {
        return (int) ((packedPos << 52) >> 52); // Paper - simplify/inline
    }

    public static int getZ(long packedPos) {
        return (int) ((packedPos << 26) >> 38); // Paper - simplify/inline
    }

    public static BlockPos of(long packedPos) {
        return new BlockPos((int) (packedPos >> 38), (int) ((packedPos << 52) >> 52), (int) ((packedPos << 26) >> 38)); // Paper - simplify/inline
    }

    public static BlockPos containing(double x, double y, double z) {
        return new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }

    public static BlockPos containing(Position position) {
        return containing(position.x(), position.y(), position.z());
    }

    public static BlockPos min(BlockPos pos1, BlockPos pos2) {
        return new BlockPos(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
    }

    public static BlockPos max(BlockPos pos1, BlockPos pos2) {
        return new BlockPos(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
    }

    public long asLong() {
        return asLong(this.getX(), this.getY(), this.getZ());
    }

    public static long asLong(int x, int y, int z) {
        return ((x & 67108863L) << 38) | ((y &  4095L)) | ((z & 67108863L) << 12); // Paper - inline constants and simplify
    }

    public static long getFlatIndex(long packedPos) {
        return packedPos & -16L;
    }

    @Override
    public BlockPos offset(int dx, int dy, int dz) {
        return dx == 0 && dy == 0 && dz == 0 ? this : new BlockPos(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
    }

    public Vec3 getCenter() {
        return Vec3.atCenterOf(this);
    }

    public Vec3 getBottomCenter() {
        return Vec3.atBottomCenterOf(this);
    }

    @Override
    public BlockPos offset(Vec3i vector) {
        return this.offset(vector.getX(), vector.getY(), vector.getZ());
    }

    @Override
    public BlockPos subtract(Vec3i vector) {
        return this.offset(-vector.getX(), -vector.getY(), -vector.getZ());
    }

    @Override
    public BlockPos multiply(int scalar) {
        if (scalar == 1) {
            return this;
        } else {
            return scalar == 0 ? ZERO : new BlockPos(this.getX() * scalar, this.getY() * scalar, this.getZ() * scalar);
        }
    }

    @Override
    public BlockPos above() {
        return new BlockPos(this.getX(), this.getY() + 1, this.getZ()); // Paper - Perf: Optimize BlockPosition
    }

    @Override
    public BlockPos above(int distance) {
        return distance == 0 ? this.immutable() : new BlockPos(this.getX(), this.getY() + distance, this.getZ()); // Paper - Perf: Optimize BlockPosition
    }

    @Override
    public BlockPos below() {
        return new BlockPos(this.getX(), this.getY() - 1, this.getZ()); // Paper - Perf: Optimize BlockPosition
    }

    @Override
    public BlockPos below(int distance) {
        return distance == 0 ? this.immutable() : new BlockPos(this.getX(), this.getY() - distance, this.getZ()); // Paper - Perf: Optimize BlockPosition
    }

    @Override
    public BlockPos north() {
        return new BlockPos(this.getX(), this.getY(), this.getZ() - 1); // Paper - Perf: Optimize BlockPosition
    }

    @Override
    public BlockPos north(int distance) {
        return distance == 0 ? this.immutable() : new BlockPos(this.getX(), this.getY(), this.getZ() - distance); // Paper - Perf: Optimize BlockPosition
    }

    @Override
    public BlockPos south() {
        return new BlockPos(this.getX(), this.getY(), this.getZ() + 1); // Paper - Perf: Optimize BlockPosition
    }

    @Override
    public BlockPos south(int distance) {
        return distance == 0 ? this.immutable() : new BlockPos(this.getX(), this.getY(), this.getZ() + distance); // Paper - Perf: Optimize BlockPosition
    }

    @Override
    public BlockPos west() {
        return new BlockPos(this.getX() - 1, this.getY(), this.getZ()); // Paper - Perf: Optimize BlockPosition
    }

    @Override
    public BlockPos west(int distance) {
        return distance == 0 ? this.immutable() : new BlockPos(this.getX() - distance, this.getY(), this.getZ()); // Paper - Perf: Optimize BlockPosition
    }

    @Override
    public BlockPos east() {
        return new BlockPos(this.getX() + 1, this.getY(), this.getZ()); // Paper - Perf: Optimize BlockPosition
    }

    @Override
    public BlockPos east(int distance) {
        return distance == 0 ? this.immutable() : new BlockPos(this.getX() + distance, this.getY(), this.getZ()); // Paper - Perf: Optimize BlockPosition
    }

    @Override
    public BlockPos relative(Direction direction) {
        // Paper start - Perf: Optimize BlockPosition
        switch(direction) {
            case UP:
                return new BlockPos(this.getX(), this.getY() + 1, this.getZ());
            case DOWN:
                return new BlockPos(this.getX(), this.getY() - 1, this.getZ());
            case NORTH:
                return new BlockPos(this.getX(), this.getY(), this.getZ() - 1);
            case SOUTH:
                return new BlockPos(this.getX(), this.getY(), this.getZ() + 1);
            case WEST:
                return new BlockPos(this.getX() - 1, this.getY(), this.getZ());
            case EAST:
                return new BlockPos(this.getX() + 1, this.getY(), this.getZ());
            default:
                return new BlockPos(this.getX() + direction.getStepX(), this.getY() + direction.getStepY(), this.getZ() + direction.getStepZ());
        }
        // Paper end - Perf: Optimize BlockPosition
    }

    @Override
    public BlockPos relative(Direction direction, int distance) {
        return distance == 0
            ? this
            : new BlockPos(
                this.getX() + direction.getStepX() * distance, this.getY() + direction.getStepY() * distance, this.getZ() + direction.getStepZ() * distance
            );
    }

    @Override
    public BlockPos relative(Direction.Axis axis, int amount) {
        if (amount == 0) {
            return this;
        } else {
            int i = axis == Direction.Axis.X ? amount : 0;
            int i1 = axis == Direction.Axis.Y ? amount : 0;
            int i2 = axis == Direction.Axis.Z ? amount : 0;
            return new BlockPos(this.getX() + i, this.getY() + i1, this.getZ() + i2);
        }
    }

    public BlockPos rotate(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(-this.getZ(), this.getY(), this.getX());
            case CLOCKWISE_180 -> new BlockPos(-this.getX(), this.getY(), -this.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(this.getZ(), this.getY(), -this.getX());
            case NONE -> this;
        };
    }

    @Override
    public BlockPos cross(Vec3i vector) {
        return new BlockPos(
            this.getY() * vector.getZ() - this.getZ() * vector.getY(),
            this.getZ() * vector.getX() - this.getX() * vector.getZ(),
            this.getX() * vector.getY() - this.getY() * vector.getX()
        );
    }

    public BlockPos atY(int y) {
        return new BlockPos(this.getX(), y, this.getZ());
    }

    public BlockPos immutable() {
        return this;
    }

    public BlockPos.MutableBlockPos mutable() {
        return new BlockPos.MutableBlockPos(this.getX(), this.getY(), this.getZ());
    }

    public Vec3 clampLocationWithin(Vec3 pos) {
        return new Vec3(
            Mth.clamp(pos.x, (double)(this.getX() + 1.0E-5F), this.getX() + 1.0 - 1.0E-5F),
            Mth.clamp(pos.y, (double)(this.getY() + 1.0E-5F), this.getY() + 1.0 - 1.0E-5F),
            Mth.clamp(pos.z, (double)(this.getZ() + 1.0E-5F), this.getZ() + 1.0 - 1.0E-5F)
        );
    }

    public static Iterable<BlockPos> randomInCube(RandomSource random, int amount, BlockPos center, int radius) {
        return randomBetweenClosed(
            random,
            amount,
            center.getX() - radius,
            center.getY() - radius,
            center.getZ() - radius,
            center.getX() + radius,
            center.getY() + radius,
            center.getZ() + radius
        );
    }

    @Deprecated
    public static Stream<BlockPos> squareOutSouthEast(BlockPos pos) {
        return Stream.of(pos, pos.south(), pos.east(), pos.south().east());
    }

    public static Iterable<BlockPos> randomBetweenClosed(RandomSource random, int amount, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int i = maxX - minX + 1;
        int i1 = maxY - minY + 1;
        int i2 = maxZ - minZ + 1;
        return () -> new AbstractIterator<BlockPos>() {
            final BlockPos.MutableBlockPos nextPos = new BlockPos.MutableBlockPos();
            int counter = amount;

            @Override
            protected BlockPos computeNext() {
                if (this.counter <= 0) {
                    return this.endOfData();
                } else {
                    BlockPos blockPos = this.nextPos.set(minX + random.nextInt(i), minY + random.nextInt(i1), minZ + random.nextInt(i2));
                    this.counter--;
                    return blockPos;
                }
            }
        };
    }

    public static Iterable<BlockPos> withinManhattan(BlockPos pos, int xSize, int ySize, int zSize) {
        int i = xSize + ySize + zSize;
        int x1 = pos.getX();
        int y1 = pos.getY();
        int z = pos.getZ();
        return () -> new AbstractIterator<BlockPos>() {
            private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            private int currentDepth;
            private int maxX;
            private int maxY;
            private int x;
            private int y;
            private boolean zMirror;

            @Override
            protected BlockPos computeNext() {
                if (this.zMirror) {
                    this.zMirror = false;
                    this.cursor.setZ(z - (this.cursor.getZ() - z));
                    return this.cursor;
                } else {
                    BlockPos blockPos;
                    for (blockPos = null; blockPos == null; this.y++) {
                        if (this.y > this.maxY) {
                            this.x++;
                            if (this.x > this.maxX) {
                                this.currentDepth++;
                                if (this.currentDepth > i) {
                                    return this.endOfData();
                                }

                                this.maxX = Math.min(xSize, this.currentDepth);
                                this.x = -this.maxX;
                            }

                            this.maxY = Math.min(ySize, this.currentDepth - Math.abs(this.x));
                            this.y = -this.maxY;
                        }

                        int i1 = this.x;
                        int i2 = this.y;
                        int i3 = this.currentDepth - Math.abs(i1) - Math.abs(i2);
                        if (i3 <= zSize) {
                            this.zMirror = i3 != 0;
                            blockPos = this.cursor.set(x1 + i1, y1 + i2, z + i3);
                        }
                    }

                    return blockPos;
                }
            }
        };
    }

    public static Optional<BlockPos> findClosestMatch(BlockPos pos, int width, int height, Predicate<BlockPos> posFilter) {
        for (BlockPos blockPos : withinManhattan(pos, width, height, width)) {
            if (posFilter.test(blockPos)) {
                return Optional.of(blockPos);
            }
        }

        return Optional.empty();
    }

    public static Stream<BlockPos> withinManhattanStream(BlockPos pos, int xSize, int ySize, int zSize) {
        return StreamSupport.stream(withinManhattan(pos, xSize, ySize, zSize).spliterator(), false);
    }

    public static Iterable<BlockPos> betweenClosed(AABB box) {
        BlockPos blockPos = containing(box.minX, box.minY, box.minZ);
        BlockPos blockPos1 = containing(box.maxX, box.maxY, box.maxZ);
        return betweenClosed(blockPos, blockPos1);
    }

    public static Iterable<BlockPos> betweenClosed(BlockPos pos1, BlockPos pos2) {
        return betweenClosed(
            Math.min(pos1.getX(), pos2.getX()),
            Math.min(pos1.getY(), pos2.getY()),
            Math.min(pos1.getZ(), pos2.getZ()),
            Math.max(pos1.getX(), pos2.getX()),
            Math.max(pos1.getY(), pos2.getY()),
            Math.max(pos1.getZ(), pos2.getZ())
        );
    }

    public static Stream<BlockPos> betweenClosedStream(BlockPos pos1, BlockPos pos2) {
        return StreamSupport.stream(betweenClosed(pos1, pos2).spliterator(), false);
    }

    public static Stream<BlockPos> betweenClosedStream(BoundingBox box) {
        return betweenClosedStream(
            Math.min(box.minX(), box.maxX()),
            Math.min(box.minY(), box.maxY()),
            Math.min(box.minZ(), box.maxZ()),
            Math.max(box.minX(), box.maxX()),
            Math.max(box.minY(), box.maxY()),
            Math.max(box.minZ(), box.maxZ())
        );
    }

    public static Stream<BlockPos> betweenClosedStream(AABB aabb) {
        return betweenClosedStream(
            Mth.floor(aabb.minX), Mth.floor(aabb.minY), Mth.floor(aabb.minZ), Mth.floor(aabb.maxX), Mth.floor(aabb.maxY), Mth.floor(aabb.maxZ)
        );
    }

    public static Stream<BlockPos> betweenClosedStream(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return StreamSupport.stream(betweenClosed(minX, minY, minZ, maxX, maxY, maxZ).spliterator(), false);
    }

    public static Iterable<BlockPos> betweenClosed(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int i = maxX - minX + 1;
        int i1 = maxY - minY + 1;
        int i2 = maxZ - minZ + 1;
        int i3 = i * i1 * i2;
        return () -> new AbstractIterator<BlockPos>() {
            private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            private int index;

            @Override
            protected BlockPos computeNext() {
                if (this.index == i3) {
                    return this.endOfData();
                } else {
                    int i4 = this.index % i;
                    int i5 = this.index / i;
                    int i6 = i5 % i1;
                    int i7 = i5 / i1;
                    this.index++;
                    return this.cursor.set(minX + i4, minY + i6, minZ + i7);
                }
            }
        };
    }

    public static Iterable<BlockPos.MutableBlockPos> spiralAround(BlockPos center, int size, Direction rotationDirection, Direction expansionDirection) {
        Validate.validState(rotationDirection.getAxis() != expansionDirection.getAxis(), "The two directions cannot be on the same axis");
        return () -> new AbstractIterator<BlockPos.MutableBlockPos>() {
            private final Direction[] directions = new Direction[]{
                rotationDirection, expansionDirection, rotationDirection.getOpposite(), expansionDirection.getOpposite()
            };
            private final BlockPos.MutableBlockPos cursor = center.mutable().move(expansionDirection);
            private final int legs = 4 * size;
            private int leg = -1;
            private int legSize;
            private int legIndex;
            private int lastX = this.cursor.getX();
            private int lastY = this.cursor.getY();
            private int lastZ = this.cursor.getZ();

            @Override
            protected BlockPos.MutableBlockPos computeNext() {
                this.cursor.set(this.lastX, this.lastY, this.lastZ).move(this.directions[(this.leg + 4) % 4]);
                this.lastX = this.cursor.getX();
                this.lastY = this.cursor.getY();
                this.lastZ = this.cursor.getZ();
                if (this.legIndex >= this.legSize) {
                    if (this.leg >= this.legs) {
                        return this.endOfData();
                    }

                    this.leg++;
                    this.legIndex = 0;
                    this.legSize = this.leg / 2 + 1;
                }

                this.legIndex++;
                return this.cursor;
            }
        };
    }

    public static int breadthFirstTraversal(
        BlockPos startPos,
        int radius,
        int maxBlocks,
        BiConsumer<BlockPos, Consumer<BlockPos>> childrenGetter,
        Function<BlockPos, BlockPos.TraversalNodeStatus> action
    ) {
        Queue<Pair<BlockPos, Integer>> queue = new ArrayDeque<>();
        LongSet set = new LongOpenHashSet();
        queue.add(Pair.of(startPos, 0));
        int i = 0;

        while (!queue.isEmpty()) {
            Pair<BlockPos, Integer> pair = queue.poll();
            BlockPos blockPos = pair.getLeft();
            int right = pair.getRight();
            long packedBlockPos = blockPos.asLong();
            if (set.add(packedBlockPos)) {
                BlockPos.TraversalNodeStatus traversalNodeStatus = action.apply(blockPos);
                if (traversalNodeStatus != BlockPos.TraversalNodeStatus.SKIP) {
                    if (traversalNodeStatus == BlockPos.TraversalNodeStatus.STOP) {
                        break;
                    }

                    if (++i >= maxBlocks) {
                        return i;
                    }

                    if (right < radius) {
                        childrenGetter.accept(blockPos, pos -> queue.add(Pair.of(pos, right + 1)));
                    }
                }
            }
        }

        return i;
    }

    public static Iterable<BlockPos> betweenCornersInDirection(AABB aabb, Vec3 direction) {
        Vec3 minPosition = aabb.getMinPosition();
        int floor = Mth.floor(minPosition.x());
        int floor1 = Mth.floor(minPosition.y());
        int floor2 = Mth.floor(minPosition.z());
        Vec3 maxPosition = aabb.getMaxPosition();
        int floor3 = Mth.floor(maxPosition.x());
        int floor4 = Mth.floor(maxPosition.y());
        int floor5 = Mth.floor(maxPosition.z());
        return betweenCornersInDirection(floor, floor1, floor2, floor3, floor4, floor5, direction);
    }

    public static Iterable<BlockPos> betweenCornersInDirection(BlockPos pos1, BlockPos pos2, Vec3 direction) {
        return betweenCornersInDirection(pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ(), direction);
    }

    public static Iterable<BlockPos> betweenCornersInDirection(int x1, int y1, int z1, int x2, int y2, int z2, Vec3 direction) {
        int min = Math.min(x1, x2);
        int min1 = Math.min(y1, y2);
        int min2 = Math.min(z1, z2);
        int max = Math.max(x1, x2);
        int max1 = Math.max(y1, y2);
        int max2 = Math.max(z1, z2);
        int i = max - min;
        int i1 = max1 - min1;
        int i2 = max2 - min2;
        int i3 = direction.x >= 0.0 ? min : max;
        int i4 = direction.y >= 0.0 ? min1 : max1;
        int i5 = direction.z >= 0.0 ? min2 : max2;
        List<Direction.Axis> list = Direction.axisStepOrder(direction);
        Direction.Axis axis = list.get(0);
        Direction.Axis axis1 = list.get(1);
        Direction.Axis axis2 = list.get(2);
        Direction direction1 = direction.get(axis) >= 0.0 ? axis.getPositive() : axis.getNegative();
        Direction direction2 = direction.get(axis1) >= 0.0 ? axis1.getPositive() : axis1.getNegative();
        Direction direction3 = direction.get(axis2) >= 0.0 ? axis2.getPositive() : axis2.getNegative();
        int i6 = axis.choose(i, i1, i2);
        int i7 = axis1.choose(i, i1, i2);
        int i8 = axis2.choose(i, i1, i2);
        return () -> new AbstractIterator<BlockPos>() {
            private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            private int firstIndex;
            private int secondIndex;
            private int thirdIndex;
            private boolean end;
            private final int firstDirX = direction1.getStepX();
            private final int firstDirY = direction1.getStepY();
            private final int firstDirZ = direction1.getStepZ();
            private final int secondDirX = direction2.getStepX();
            private final int secondDirY = direction2.getStepY();
            private final int secondDirZ = direction2.getStepZ();
            private final int thirdDirX = direction3.getStepX();
            private final int thirdDirY = direction3.getStepY();
            private final int thirdDirZ = direction3.getStepZ();

            @Override
            protected BlockPos computeNext() {
                if (this.end) {
                    return this.endOfData();
                } else {
                    this.cursor
                        .set(
                            i3 + this.firstDirX * this.firstIndex + this.secondDirX * this.secondIndex + this.thirdDirX * this.thirdIndex,
                            i4 + this.firstDirY * this.firstIndex + this.secondDirY * this.secondIndex + this.thirdDirY * this.thirdIndex,
                            i5 + this.firstDirZ * this.firstIndex + this.secondDirZ * this.secondIndex + this.thirdDirZ * this.thirdIndex
                        );
                    if (this.thirdIndex < i8) {
                        this.thirdIndex++;
                    } else if (this.secondIndex < i7) {
                        this.secondIndex++;
                        this.thirdIndex = 0;
                    } else if (this.firstIndex < i6) {
                        this.firstIndex++;
                        this.thirdIndex = 0;
                        this.secondIndex = 0;
                    } else {
                        this.end = true;
                    }

                    return this.cursor;
                }
            }
        };
    }

    public static class MutableBlockPos extends BlockPos {
        public MutableBlockPos() {
            this(0, 0, 0);
        }

        public MutableBlockPos(int x, int y, int z) {
            super(x, y, z);
        }

        public MutableBlockPos(double x, double y, double z) {
            this(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        }

        @Override
        public BlockPos offset(int dx, int dy, int dz) {
            return super.offset(dx, dy, dz).immutable();
        }

        @Override
        public BlockPos multiply(int scalar) {
            return super.multiply(scalar).immutable();
        }

        @Override
        public BlockPos relative(Direction direction, int distance) {
            return super.relative(direction, distance).immutable();
        }

        @Override
        public BlockPos relative(Direction.Axis axis, int amount) {
            return super.relative(axis, amount).immutable();
        }

        @Override
        public BlockPos rotate(Rotation rotation) {
            return super.rotate(rotation).immutable();
        }

        public BlockPos.MutableBlockPos set(int x, int y, int z) {
            this.x = x; // Paper - Perf: Manually inline methods in BlockPosition
            this.y = y; // Paper - Perf: Manually inline methods in BlockPosition
            this.z = z; // Paper - Perf: Manually inline methods in BlockPosition
            return this;
        }

        public BlockPos.MutableBlockPos set(double x, double y, double z) {
            return this.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        }

        public BlockPos.MutableBlockPos set(Vec3i pos) {
            return this.set(pos.getX(), pos.getY(), pos.getZ());
        }

        public BlockPos.MutableBlockPos set(long packedPos) {
            return this.set(getX(packedPos), getY(packedPos), getZ(packedPos));
        }

        public BlockPos.MutableBlockPos set(AxisCycle cycle, int x, int y, int z) {
            return this.set(cycle.cycle(x, y, z, Direction.Axis.X), cycle.cycle(x, y, z, Direction.Axis.Y), cycle.cycle(x, y, z, Direction.Axis.Z));
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i pos, Direction direction) {
            return this.set(pos.getX() + direction.getStepX(), pos.getY() + direction.getStepY(), pos.getZ() + direction.getStepZ());
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i pos, int offsetX, int offsetY, int offsetZ) {
            return this.set(pos.getX() + offsetX, pos.getY() + offsetY, pos.getZ() + offsetZ);
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i pos, Vec3i offset) {
            return this.set(pos.getX() + offset.getX(), pos.getY() + offset.getY(), pos.getZ() + offset.getZ());
        }

        public BlockPos.MutableBlockPos move(Direction direction) {
            return this.move(direction, 1);
        }

        public BlockPos.MutableBlockPos move(Direction direction, int n) {
            return this.set(this.getX() + direction.getStepX() * n, this.getY() + direction.getStepY() * n, this.getZ() + direction.getStepZ() * n);
        }

        public BlockPos.MutableBlockPos move(int x, int y, int z) {
            return this.set(this.getX() + x, this.getY() + y, this.getZ() + z);
        }

        public BlockPos.MutableBlockPos move(Vec3i offset) {
            return this.set(this.getX() + offset.getX(), this.getY() + offset.getY(), this.getZ() + offset.getZ());
        }

        public BlockPos.MutableBlockPos clamp(Direction.Axis axis, int min, int max) {
            return switch (axis) {
                case X -> this.set(Mth.clamp(this.getX(), min, max), this.getY(), this.getZ());
                case Y -> this.set(this.getX(), Mth.clamp(this.getY(), min, max), this.getZ());
                case Z -> this.set(this.getX(), this.getY(), Mth.clamp(this.getZ(), min, max));
            };
        }

        @Override
        public BlockPos.MutableBlockPos setX(int x) {
            this.x = x; // Paper - Perf: Manually inline methods in BlockPosition
            return this;
        }

        @Override
        public BlockPos.MutableBlockPos setY(int y) {
            this.y = y; // Paper - Perf: Manually inline methods in BlockPosition
            return this;
        }

        @Override
        public BlockPos.MutableBlockPos setZ(int z) {
            this.z = z; // Paper - Perf: Manually inline methods in BlockPosition
            return this;
        }

        @Override
        public BlockPos immutable() {
            return new BlockPos(this);
        }
    }

    public static enum TraversalNodeStatus {
        ACCEPT,
        SKIP,
        STOP;
    }
}
