package net.minecraft.world.level.levelgen.structure;

import com.google.common.base.MoreObjects;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class BoundingBox {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<BoundingBox> CODEC = Codec.INT_STREAM
        .<BoundingBox>comapFlatMap(
            stream -> Util.fixedSize(stream, 6).map(ints -> new BoundingBox(ints[0], ints[1], ints[2], ints[3], ints[4], ints[5])),
            boundingBox -> IntStream.of(boundingBox.minX, boundingBox.minY, boundingBox.minZ, boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ)
        )
        .stable();
    public static final StreamCodec<ByteBuf, BoundingBox> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        boundingBox -> new BlockPos(boundingBox.minX, boundingBox.minY, boundingBox.minZ),
        BlockPos.STREAM_CODEC,
        boundingBox -> new BlockPos(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ),
        (blockPos, blockPos1) -> new BoundingBox(blockPos.getX(), blockPos.getY(), blockPos.getZ(), blockPos1.getX(), blockPos1.getY(), blockPos1.getZ())
    );
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    public BoundingBox(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    public BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            Util.logAndPauseIfInIde("Invalid bounding box data, inverted bounds for: " + this);
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
        }
    }

    public static BoundingBox fromCorners(Vec3i first, Vec3i second) {
        return new BoundingBox(
            Math.min(first.getX(), second.getX()),
            Math.min(first.getY(), second.getY()),
            Math.min(first.getZ(), second.getZ()),
            Math.max(first.getX(), second.getX()),
            Math.max(first.getY(), second.getY()),
            Math.max(first.getZ(), second.getZ())
        );
    }

    public static BoundingBox infinite() {
        return new BoundingBox(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public static BoundingBox orientBox(
        int structureMinX, int structureMinY, int structureMinZ, int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Direction facing
    ) {
        switch (facing) {
            case SOUTH:
            default:
                return new BoundingBox(
                    structureMinX + xMin,
                    structureMinY + yMin,
                    structureMinZ + zMin,
                    structureMinX + xMax - 1 + xMin,
                    structureMinY + yMax - 1 + yMin,
                    structureMinZ + zMax - 1 + zMin
                );
            case NORTH:
                return new BoundingBox(
                    structureMinX + xMin,
                    structureMinY + yMin,
                    structureMinZ - zMax + 1 + zMin,
                    structureMinX + xMax - 1 + xMin,
                    structureMinY + yMax - 1 + yMin,
                    structureMinZ + zMin
                );
            case WEST:
                return new BoundingBox(
                    structureMinX - zMax + 1 + zMin,
                    structureMinY + yMin,
                    structureMinZ + xMin,
                    structureMinX + zMin,
                    structureMinY + yMax - 1 + yMin,
                    structureMinZ + xMax - 1 + xMin
                );
            case EAST:
                return new BoundingBox(
                    structureMinX + zMin,
                    structureMinY + yMin,
                    structureMinZ + xMin,
                    structureMinX + zMax - 1 + zMin,
                    structureMinY + yMax - 1 + yMin,
                    structureMinZ + xMax - 1 + xMin
                );
        }
    }

    public Stream<ChunkPos> intersectingChunks() {
        int sectionPosMinX = SectionPos.blockToSectionCoord(this.minX());
        int sectionPosMinZ = SectionPos.blockToSectionCoord(this.minZ());
        int sectionPosMaxX = SectionPos.blockToSectionCoord(this.maxX());
        int sectionPosMaxZ = SectionPos.blockToSectionCoord(this.maxZ());
        return ChunkPos.rangeClosed(new ChunkPos(sectionPosMinX, sectionPosMinZ), new ChunkPos(sectionPosMaxX, sectionPosMaxZ));
    }

    public boolean intersects(BoundingBox box) {
        return this.maxX >= box.minX
            && this.minX <= box.maxX
            && this.maxZ >= box.minZ
            && this.minZ <= box.maxZ
            && this.maxY >= box.minY
            && this.minY <= box.maxY;
    }

    public boolean intersects(int minX, int minZ, int maxX, int maxZ) {
        return this.maxX >= minX && this.minX <= maxX && this.maxZ >= minZ && this.minZ <= maxZ;
    }

    public static Optional<BoundingBox> encapsulatingPositions(Iterable<BlockPos> positions) {
        Iterator<BlockPos> iterator = positions.iterator();
        if (!iterator.hasNext()) {
            return Optional.empty();
        } else {
            BoundingBox boundingBox = new BoundingBox(iterator.next());
            iterator.forEachRemaining(boundingBox::encapsulate);
            return Optional.of(boundingBox);
        }
    }

    public static Optional<BoundingBox> encapsulatingBoxes(Iterable<BoundingBox> boxes) {
        Iterator<BoundingBox> iterator = boxes.iterator();
        if (!iterator.hasNext()) {
            return Optional.empty();
        } else {
            BoundingBox boundingBox = iterator.next();
            BoundingBox boundingBox1 = new BoundingBox(
                boundingBox.minX, boundingBox.minY, boundingBox.minZ, boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ
            );
            iterator.forEachRemaining(boundingBox1::encapsulate);
            return Optional.of(boundingBox1);
        }
    }

    @Deprecated
    public BoundingBox encapsulate(BoundingBox box) {
        this.minX = Math.min(this.minX, box.minX);
        this.minY = Math.min(this.minY, box.minY);
        this.minZ = Math.min(this.minZ, box.minZ);
        this.maxX = Math.max(this.maxX, box.maxX);
        this.maxY = Math.max(this.maxY, box.maxY);
        this.maxZ = Math.max(this.maxZ, box.maxZ);
        return this;
    }

    public static BoundingBox encapsulating(BoundingBox box1, BoundingBox box2) {
        return new BoundingBox(
            Math.min(box1.minX, box2.minX),
            Math.min(box1.minY, box2.minY),
            Math.min(box1.minZ, box2.minZ),
            Math.max(box1.maxX, box2.maxX),
            Math.max(box1.maxY, box2.maxY),
            Math.max(box1.maxZ, box2.maxZ)
        );
    }

    @Deprecated
    public BoundingBox encapsulate(BlockPos pos) {
        this.minX = Math.min(this.minX, pos.getX());
        this.minY = Math.min(this.minY, pos.getY());
        this.minZ = Math.min(this.minZ, pos.getZ());
        this.maxX = Math.max(this.maxX, pos.getX());
        this.maxY = Math.max(this.maxY, pos.getY());
        this.maxZ = Math.max(this.maxZ, pos.getZ());
        return this;
    }

    @Deprecated
    public BoundingBox move(int x, int y, int z) {
        this.minX += x;
        this.minY += y;
        this.minZ += z;
        this.maxX += x;
        this.maxY += y;
        this.maxZ += z;
        return this;
    }

    @Deprecated
    public BoundingBox move(Vec3i vector) {
        return this.move(vector.getX(), vector.getY(), vector.getZ());
    }

    public BoundingBox moved(int x, int y, int z) {
        return new BoundingBox(this.minX + x, this.minY + y, this.minZ + z, this.maxX + x, this.maxY + y, this.maxZ + z);
    }

    public BoundingBox inflatedBy(int value) {
        return this.inflatedBy(value, value, value);
    }

    public BoundingBox inflatedBy(int x, int y, int z) {
        return new BoundingBox(this.minX() - x, this.minY() - y, this.minZ() - z, this.maxX() + x, this.maxY() + y, this.maxZ() + z);
    }

    public boolean isInside(Vec3i vector) {
        return this.isInside(vector.getX(), vector.getY(), vector.getZ());
    }

    public boolean isInside(int x, int y, int z) {
        return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ && y >= this.minY && y <= this.maxY;
    }

    public Vec3i getLength() {
        return new Vec3i(this.maxX - this.minX, this.maxY - this.minY, this.maxZ - this.minZ);
    }

    public int getXSpan() {
        return this.maxX - this.minX + 1;
    }

    public int getYSpan() {
        return this.maxY - this.minY + 1;
    }

    public int getZSpan() {
        return this.maxZ - this.minZ + 1;
    }

    public BlockPos getCenter() {
        return new BlockPos(
            this.minX + (this.maxX - this.minX + 1) / 2, this.minY + (this.maxY - this.minY + 1) / 2, this.minZ + (this.maxZ - this.minZ + 1) / 2
        );
    }

    public void forAllCorners(Consumer<BlockPos> pos) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        pos.accept(mutableBlockPos.set(this.maxX, this.maxY, this.maxZ));
        pos.accept(mutableBlockPos.set(this.minX, this.maxY, this.maxZ));
        pos.accept(mutableBlockPos.set(this.maxX, this.minY, this.maxZ));
        pos.accept(mutableBlockPos.set(this.minX, this.minY, this.maxZ));
        pos.accept(mutableBlockPos.set(this.maxX, this.maxY, this.minZ));
        pos.accept(mutableBlockPos.set(this.minX, this.maxY, this.minZ));
        pos.accept(mutableBlockPos.set(this.maxX, this.minY, this.minZ));
        pos.accept(mutableBlockPos.set(this.minX, this.minY, this.minZ));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("minX", this.minX)
            .add("minY", this.minY)
            .add("minZ", this.minZ)
            .add("maxX", this.maxX)
            .add("maxY", this.maxY)
            .add("maxZ", this.maxZ)
            .toString();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof BoundingBox boundingBox
                && this.minX == boundingBox.minX
                && this.minY == boundingBox.minY
                && this.minZ == boundingBox.minZ
                && this.maxX == boundingBox.maxX
                && this.maxY == boundingBox.maxY
                && this.maxZ == boundingBox.maxZ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public int minX() {
        return this.minX;
    }

    public int minY() {
        return this.minY;
    }

    public int minZ() {
        return this.minZ;
    }

    public int maxX() {
        return this.maxX;
    }

    public int maxY() {
        return this.maxY;
    }

    public int maxZ() {
        return this.maxZ;
    }
}
