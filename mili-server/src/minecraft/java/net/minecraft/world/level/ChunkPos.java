package net.minecraft.world.level;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

public class ChunkPos {
    public static final Codec<ChunkPos> CODEC = Codec.INT_STREAM
        .<ChunkPos>comapFlatMap(
            intStream -> Util.fixedSize(intStream, 2).map(ints -> new ChunkPos(ints[0], ints[1])), chunkPos -> IntStream.of(chunkPos.x, chunkPos.z)
        )
        .stable();
    public static final StreamCodec<ByteBuf, ChunkPos> STREAM_CODEC = new StreamCodec<ByteBuf, ChunkPos>() {
        @Override
        public ChunkPos decode(ByteBuf buffer) {
            return FriendlyByteBuf.readChunkPos(buffer);
        }

        @Override
        public void encode(ByteBuf buffer, ChunkPos value) {
            FriendlyByteBuf.writeChunkPos(buffer, value);
        }
    };
    private static final int SAFETY_MARGIN = 1056;
    public static final long INVALID_CHUNK_POS = asLong(1875066, 1875066);
    private static final int SAFETY_MARGIN_CHUNKS = (32 + ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FULL).accumulatedDependencies().size() + 1) * 2;
    public static final int MAX_COORDINATE_VALUE = SectionPos.blockToSectionCoord(BlockPos.MAX_HORIZONTAL_COORDINATE) - SAFETY_MARGIN_CHUNKS;
    public static final ChunkPos ZERO = new ChunkPos(0, 0);
    private static final long COORD_BITS = 32L;
    private static final long COORD_MASK = 4294967295L;
    private static final int REGION_BITS = 5;
    public static final int REGION_SIZE = 32;
    private static final int REGION_MASK = 31;
    public static final int REGION_MAX_INDEX = 31;
    public final int x;
    public final int z;
    public final long longKey; // Paper
    private static final int HASH_A = 1664525;
    private static final int HASH_C = 1013904223;
    private static final int HASH_Z_XOR = -559038737;

    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
        this.longKey = asLong(this.x, this.z); // Paper
    }

    public ChunkPos(BlockPos pos) {
        this.x = SectionPos.blockToSectionCoord(pos.getX());
        this.z = SectionPos.blockToSectionCoord(pos.getZ());
        this.longKey = asLong(this.x, this.z); // Paper
    }

    public ChunkPos(long packedPos) {
        this.x = (int)packedPos;
        this.z = (int)(packedPos >> 32);
        this.longKey = asLong(this.x, this.z); // Paper
    }

    public static ChunkPos minFromRegion(int chunkX, int chunkZ) {
        return new ChunkPos(chunkX << 5, chunkZ << 5);
    }

    public static ChunkPos maxFromRegion(int chunkX, int chunkZ) {
        return new ChunkPos((chunkX << 5) + 31, (chunkZ << 5) + 31);
    }

    public boolean isValid() {
        return isValid(this.x, this.z);
    }

    public static boolean isValid(int i, int i1) {
        return Mth.absMax(i, i1) <= MAX_COORDINATE_VALUE;
    }

    public long toLong() {
        return this.longKey; // Paper
    }

    public static long asLong(int x, int z) {
        return x & 4294967295L | (z & 4294967295L) << 32;
    }

    public static long asLong(BlockPos pos) {
        return asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    public static int getX(long chunkAsLong) {
        return (int)(chunkAsLong & 4294967295L);
    }

    public static int getZ(long chunkAsLong) {
        return (int)(chunkAsLong >>> 32 & 4294967295L);
    }

    @Override
    public int hashCode() {
        return hash(this.x, this.z);
    }

    public static int hash(int x, int z) {
        int i = 1664525 * x + 1013904223;
        int i1 = 1664525 * (z ^ -559038737) + 1013904223;
        return i ^ i1;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ChunkPos chunkPos && this.x == chunkPos.x && this.z == chunkPos.z;
    }

    public int getMiddleBlockX() {
        return this.getBlockX(8);
    }

    public int getMiddleBlockZ() {
        return this.getBlockZ(8);
    }

    public int getMinBlockX() {
        return SectionPos.sectionToBlockCoord(this.x);
    }

    public int getMinBlockZ() {
        return SectionPos.sectionToBlockCoord(this.z);
    }

    public int getMaxBlockX() {
        return this.getBlockX(15);
    }

    public int getMaxBlockZ() {
        return this.getBlockZ(15);
    }

    public int getRegionX() {
        return this.x >> 5;
    }

    public int getRegionZ() {
        return this.z >> 5;
    }

    public int getRegionLocalX() {
        return this.x & 31;
    }

    public int getRegionLocalZ() {
        return this.z & 31;
    }

    public BlockPos getBlockAt(int xSection, int y, int zSection) {
        return new BlockPos(this.getBlockX(xSection), y, this.getBlockZ(zSection));
    }

    public int getBlockX(int x) {
        return SectionPos.sectionToBlockCoord(this.x, x);
    }

    public int getBlockZ(int z) {
        return SectionPos.sectionToBlockCoord(this.z, z);
    }

    public BlockPos getMiddleBlockPosition(int y) {
        return new BlockPos(this.getMiddleBlockX(), y, this.getMiddleBlockZ());
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= this.getMinBlockX() && pos.getZ() >= this.getMinBlockZ() && pos.getX() <= this.getMaxBlockX() && pos.getZ() <= this.getMaxBlockZ();
    }

    @Override
    public String toString() {
        return "[" + this.x + ", " + this.z + "]";
    }

    public BlockPos getWorldPosition() {
        return new BlockPos(this.getMinBlockX(), 0, this.getMinBlockZ());
    }

    public int getChessboardDistance(ChunkPos chunkPos) {
        return this.getChessboardDistance(chunkPos.x, chunkPos.z);
    }

    public int getChessboardDistance(int x, int z) {
        return Mth.chessboardDistance(x, z, this.x, this.z);
    }

    public int distanceSquared(ChunkPos chunkPos) {
        return this.distanceSquared(chunkPos.x, chunkPos.z);
    }

    public int distanceSquared(long packedPos) {
        return this.distanceSquared(getX(packedPos), getZ(packedPos));
    }

    private int distanceSquared(int x, int z) {
        int i = x - this.x;
        int i1 = z - this.z;
        return i * i + i1 * i1;
    }

    public static Stream<ChunkPos> rangeClosed(ChunkPos center, int radius) {
        return rangeClosed(new ChunkPos(center.x - radius, center.z - radius), new ChunkPos(center.x + radius, center.z + radius));
    }

    public static Stream<ChunkPos> rangeClosed(final ChunkPos start, final ChunkPos end) {
        int i = Math.abs(start.x - end.x) + 1;
        int i1 = Math.abs(start.z - end.z) + 1;
        final int i2 = start.x < end.x ? 1 : -1;
        final int i3 = start.z < end.z ? 1 : -1;
        return StreamSupport.stream(new AbstractSpliterator<ChunkPos>(i * i1, 64) {
            private @Nullable ChunkPos pos;

            @Override
            public boolean tryAdvance(Consumer<? super ChunkPos> consumer) {
                if (this.pos == null) {
                    this.pos = start;
                } else {
                    int i4 = this.pos.x;
                    int i5 = this.pos.z;
                    if (i4 == end.x) {
                        if (i5 == end.z) {
                            return false;
                        }

                        this.pos = new ChunkPos(start.x, i5 + i3);
                    } else {
                        this.pos = new ChunkPos(i4 + i2, i5);
                    }
                }

                consumer.accept(this.pos);
                return true;
            }
        }, false);
    }
}
