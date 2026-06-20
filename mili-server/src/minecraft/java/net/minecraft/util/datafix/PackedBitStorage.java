package net.minecraft.util.datafix;

import net.minecraft.util.Mth;
import org.apache.commons.lang3.Validate;

public class PackedBitStorage {
    private static final int BIT_TO_LONG_SHIFT = 6;
    private final long[] data;
    private final int bits;
    private final long mask;
    private final int size;

    public PackedBitStorage(int bits, int size) {
        this(bits, size, new long[Mth.roundToward(size * bits, 64) / 64]);
    }

    public PackedBitStorage(int bits, int size, long[] data) {
        Validate.inclusiveBetween(1L, 32L, (long)bits);
        this.size = size;
        this.bits = bits;
        this.data = data;
        this.mask = (1L << bits) - 1L;
        int i = Mth.roundToward(size * bits, 64) / 64;
        if (data.length != i) {
            throw new IllegalArgumentException("Invalid length given for storage, got: " + data.length + " but expected: " + i);
        }
    }

    public void set(int index, int value) {
        Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index);
        Validate.inclusiveBetween(0L, this.mask, (long)value);
        int i = index * this.bits;
        int i1 = i >> 6;
        int i2 = (index + 1) * this.bits - 1 >> 6;
        int i3 = i ^ i1 << 6;
        this.data[i1] = this.data[i1] & ~(this.mask << i3) | (value & this.mask) << i3;
        if (i1 != i2) {
            int i4 = 64 - i3;
            int i5 = this.bits - i4;
            this.data[i2] = this.data[i2] >>> i5 << i5 | (value & this.mask) >> i4;
        }
    }

    public int get(int index) {
        Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index);
        int i = index * this.bits;
        int i1 = i >> 6;
        int i2 = (index + 1) * this.bits - 1 >> 6;
        int i3 = i ^ i1 << 6;
        if (i1 == i2) {
            return (int)(this.data[i1] >>> i3 & this.mask);
        } else {
            int i4 = 64 - i3;
            return (int)((this.data[i1] >>> i3 | this.data[i2] << i4) & this.mask);
        }
    }

    public long[] getRaw() {
        return this.data;
    }

    public int getBits() {
        return this.bits;
    }
}
