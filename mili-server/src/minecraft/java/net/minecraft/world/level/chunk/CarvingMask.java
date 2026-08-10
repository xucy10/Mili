package net.minecraft.world.level.chunk;

import java.util.BitSet;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public class CarvingMask {
    private final int minY;
    private final BitSet mask;
    private CarvingMask.Mask additionalMask = (x, y, z) -> false;

    public CarvingMask(int mask, int minY) {
        this.minY = minY;
        this.mask = new BitSet(256 * mask);
    }

    public void setAdditionalMask(CarvingMask.Mask additionalMask) {
        this.additionalMask = additionalMask;
    }

    public CarvingMask(long[] mask, int minY) {
        this.minY = minY;
        this.mask = BitSet.valueOf(mask);
    }

    private int getIndex(int x, int y, int z) {
        return x & 15 | (z & 15) << 4 | y - this.minY << 8;
    }

    public void set(int x, int y, int z) {
        this.mask.set(this.getIndex(x, y, z));
    }

    public boolean get(int x, int y, int z) {
        return this.additionalMask.test(x, y, z) || this.mask.get(this.getIndex(x, y, z));
    }

    public Stream<BlockPos> stream(ChunkPos pos) {
        return this.mask.stream().mapToObj(longPosition -> {
            int i = longPosition & 15;
            int i1 = longPosition >> 4 & 15;
            int i2 = longPosition >> 8;
            return pos.getBlockAt(i, i2 + this.minY, i1);
        });
    }

    public long[] toArray() {
        return this.mask.toLongArray();
    }

    public interface Mask {
        boolean test(int x, int y, int z);
    }
}
