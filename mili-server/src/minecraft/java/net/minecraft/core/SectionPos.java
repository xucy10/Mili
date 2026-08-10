package net.minecraft.core;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.entity.EntityAccess;

public class SectionPos extends Vec3i {
    public static final int SECTION_BITS = 4;
    public static final int SECTION_SIZE = 16;
    public static final int SECTION_MASK = 15;
    public static final int SECTION_HALF_SIZE = 8;
    public static final int SECTION_MAX_INDEX = 15;
    private static final int PACKED_X_LENGTH = 22;
    private static final int PACKED_Y_LENGTH = 20;
    private static final int PACKED_Z_LENGTH = 22;
    private static final long PACKED_X_MASK = 4194303L;
    private static final long PACKED_Y_MASK = 1048575L;
    private static final long PACKED_Z_MASK = 4194303L;
    private static final int Y_OFFSET = 0;
    private static final int Z_OFFSET = 20;
    private static final int X_OFFSET = 42;
    private static final int RELATIVE_X_SHIFT = 8;
    private static final int RELATIVE_Y_SHIFT = 0;
    private static final int RELATIVE_Z_SHIFT = 4;
    public static final StreamCodec<ByteBuf, SectionPos> STREAM_CODEC = ByteBufCodecs.LONG.map(SectionPos::of, SectionPos::asLong);

    SectionPos(int x, int y, int z) {
        super(x, y, z);
    }

    public static SectionPos of(int x, int y, int z) {
        return new SectionPos(x, y, z);
    }

    public static SectionPos of(BlockPos pos) {
        return new SectionPos(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4); // Paper
    }

    public static SectionPos of(ChunkPos chunkPos, int y) {
        return new SectionPos(chunkPos.x, y, chunkPos.z);
    }

    public static SectionPos of(EntityAccess entity) {
        return of(entity.blockPosition());
    }

    public static SectionPos of(Position position) {
        return new SectionPos(blockToSectionCoord(position.x()), blockToSectionCoord(position.y()), blockToSectionCoord(position.z()));
    }

    public static SectionPos of(long packed) {
        return new SectionPos((int) (packed >> 42), (int) (packed << 44 >> 44), (int) (packed << 22 >> 42)); // Paper
    }

    public static SectionPos bottomOf(ChunkAccess chunk) {
        return of(chunk.getPos(), chunk.getMinSectionY());
    }

    public static long offset(long packed, Direction direction) {
        return offset(packed, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    // Paper start
    public static long getAdjacentFromBlockPos(int x, int y, int z, Direction direction) {
        return (((long) ((x >> 4) + direction.getStepX()) & 4194303L) << 42) | (((long) ((y >> 4) + direction.getStepY()) & 1048575L)) | (((long) ((z >> 4) + direction.getStepZ()) & 4194303L) << 20);
    }
    public static long getAdjacentFromSectionPos(int x, int y, int z, Direction direction) {
        return (((long) (x + direction.getStepX()) & 4194303L) << 42) | (((long) ((y) + direction.getStepY()) & 1048575L)) | (((long) (z + direction.getStepZ()) & 4194303L) << 20);
    }
    // Paper end
    public static long offset(long packed, int dx, int dy, int dz) {
        return (((long) ((int) (packed >> 42) + dx) & 4194303L) << 42) | (((long) ((int) (packed << 44 >> 44) + dy) & 1048575L)) | (((long) ((int) (packed << 22 >> 42) + dz) & 4194303L) << 20); // Simplify to reduce instruction count
    }

    public static int posToSectionCoord(double pos) {
        return blockToSectionCoord(Mth.floor(pos));
    }

    public static int blockToSectionCoord(int blockCoord) {
        return blockCoord >> 4;
    }

    public static int blockToSectionCoord(double blockCoord) {
        return Mth.floor(blockCoord) >> 4;
    }

    public static int sectionRelative(int blockCoord) {
        return blockCoord & 15;
    }

    public static short sectionRelativePos(BlockPos pos) {
        return (short) ((pos.getX() & 15) << 8 | (pos.getZ() & 15) << 4 | pos.getY() & 15); // Paper - simplify/inline
    }

    public static int sectionRelativeX(short x) {
        return x >>> 8 & 15;
    }

    public static int sectionRelativeY(short y) {
        return y >>> 0 & 15;
    }

    public static int sectionRelativeZ(short z) {
        return z >>> 4 & 15;
    }

    public int relativeToBlockX(short x) {
        return this.minBlockX() + sectionRelativeX(x);
    }

    public int relativeToBlockY(short y) {
        return this.minBlockY() + sectionRelativeY(y);
    }

    public int relativeToBlockZ(short z) {
        return this.minBlockZ() + sectionRelativeZ(z);
    }

    public BlockPos relativeToBlockPos(short pos) {
        return new BlockPos(this.relativeToBlockX(pos), this.relativeToBlockY(pos), this.relativeToBlockZ(pos));
    }

    public static int sectionToBlockCoord(int sectionCoord) {
        return sectionCoord << 4;
    }

    public static int sectionToBlockCoord(int sectionCoord, int offset) {
        return sectionToBlockCoord(sectionCoord) + offset;
    }

    public static int x(long packed) {
        return (int)(packed << 0 >> 42);
    }

    public static int y(long packed) {
        return (int)(packed << 44 >> 44);
    }

    public static int z(long packed) {
        return (int)(packed << 22 >> 42);
    }

    public int x() {
        return this.getX();
    }

    public int y() {
        return this.getY();
    }

    public int z() {
        return this.getZ();
    }

    public final int minBlockX() { // Paper - final
        return this.getX() << 4; // Paper - inline
    }

    public final int minBlockY() { // Paper - final
        return this.getY() << 4; // Paper - inline
    }

    public final int minBlockZ() { // Paper - final
        return this.getZ() << 4; // Paper - inline
    }

    public int maxBlockX() {
        return sectionToBlockCoord(this.x(), 15);
    }

    public int maxBlockY() {
        return sectionToBlockCoord(this.y(), 15);
    }

    public int maxBlockZ() {
        return sectionToBlockCoord(this.z(), 15);
    }

    public static long blockToSection(long pos) {
        return (((long) (int) (pos >> 42) & 4194303L) << 42) | (((long) (int) ((pos << 52) >> 56) & 1048575L)) | (((long) (int) ((pos << 26) >> 42) & 4194303L) << 20); // Simplify to reduce instruction count
    }

    public static long getZeroNode(int x, int z) {
        return getZeroNode(asLong(x, 0, z));
    }

    public static long getZeroNode(long pos) {
        return pos & -1048576L;
    }

    public static long sectionToChunk(long pos) {
        return ChunkPos.asLong(x(pos), z(pos));
    }

    public BlockPos origin() {
        return new BlockPos(sectionToBlockCoord(this.x()), sectionToBlockCoord(this.y()), sectionToBlockCoord(this.z()));
    }

    public BlockPos center() {
        int i = 8;
        return this.origin().offset(8, 8, 8);
    }

    public ChunkPos chunk() {
        return new ChunkPos(this.x(), this.z());
    }

    public static long asLong(BlockPos pos) {
        return asLong(blockToSectionCoord(pos.getX()), blockToSectionCoord(pos.getY()), blockToSectionCoord(pos.getZ()));
    }

    // Paper start
    public static long blockPosAsSectionLong(int x, int y, int z) {
        return (((long) (x >> 4) & 4194303L) << 42) | (((long) (y >> 4) & 1048575L)) | (((long) (z >> 4) & 4194303L) << 20);
    }
    // Paper end
    public static long asLong(int x, int y, int z) {
        return ((x & 4194303L) << 42) | ((y & 1048575L)) | ((z & 4194303L) << 20); // Paper - Simplify to reduce instruction count
    }

    public long asLong() {
        return ((this.getX() & 4194303L) << 42) | ((this.getY() & 1048575L)) | ((this.getZ() & 4194303L) << 20); // Paper - Simplify to reduce instruction count
    }

    @Override
    public SectionPos offset(int dx, int dy, int dz) {
        return dx == 0 && dy == 0 && dz == 0 ? this : new SectionPos(this.x() + dx, this.y() + dy, this.z() + dz);
    }

    public Stream<BlockPos> blocksInside() {
        return BlockPos.betweenClosedStream(this.minBlockX(), this.minBlockY(), this.minBlockZ(), this.maxBlockX(), this.maxBlockY(), this.maxBlockZ());
    }

    public static Stream<SectionPos> cube(SectionPos center, int radius) {
        return betweenClosedStream(center.getX() - radius, center.getY() - radius, center.getZ() - radius, center.getX() + radius, center.getY() + radius, center.getZ() + radius); // Paper - simplify/inline
    }

    public static Stream<SectionPos> aroundChunk(ChunkPos chunkPos, int radius, int minY, int maxY) {
        int i = chunkPos.x;
        int i1 = chunkPos.z;
        return betweenClosedStream(i - radius, minY, i1 - radius, i + radius, maxY, i1 + radius);
    }

    public static Stream<SectionPos> betweenClosedStream(final int x1, final int y1, final int z1, final int x2, final int y2, final int z2) {
        return StreamSupport.stream(new AbstractSpliterator<SectionPos>((x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1), 64) {
            final Cursor3D cursor = new Cursor3D(x1, y1, z1, x2, y2, z2);

            @Override
            public boolean tryAdvance(Consumer<? super SectionPos> consumer) {
                if (this.cursor.advance()) {
                    consumer.accept(new SectionPos(this.cursor.nextX(), this.cursor.nextY(), this.cursor.nextZ()));
                    return true;
                } else {
                    return false;
                }
            }
        }, false);
    }

    public static void aroundAndAtBlockPos(BlockPos pos, LongConsumer consumer) {
        aroundAndAtBlockPos(pos.getX(), pos.getY(), pos.getZ(), consumer);
    }

    public static void aroundAndAtBlockPos(long pos, LongConsumer consumer) {
        aroundAndAtBlockPos(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos), consumer);
    }

    public static void aroundAndAtBlockPos(int x, int y, int z, LongConsumer consumer) {
        int sectionPosCoord = blockToSectionCoord(x - 1);
        int sectionPosCoord1 = blockToSectionCoord(x + 1);
        int sectionPosCoord2 = blockToSectionCoord(y - 1);
        int sectionPosCoord3 = blockToSectionCoord(y + 1);
        int sectionPosCoord4 = blockToSectionCoord(z - 1);
        int sectionPosCoord5 = blockToSectionCoord(z + 1);
        if (sectionPosCoord == sectionPosCoord1 && sectionPosCoord2 == sectionPosCoord3 && sectionPosCoord4 == sectionPosCoord5) {
            consumer.accept(asLong(sectionPosCoord, sectionPosCoord2, sectionPosCoord4));
        } else {
            for (int i = sectionPosCoord; i <= sectionPosCoord1; i++) {
                for (int i1 = sectionPosCoord2; i1 <= sectionPosCoord3; i1++) {
                    for (int i2 = sectionPosCoord4; i2 <= sectionPosCoord5; i2++) {
                        consumer.accept(asLong(i, i1, i2));
                    }
                }
            }
        }
    }
}
