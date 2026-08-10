package net.minecraft.util;

import java.util.function.IntConsumer;
import org.apache.commons.lang3.Validate;
import org.jspecify.annotations.Nullable;

public class SimpleBitStorage implements BitStorage {
    private static final int[] MAGIC = new int[]{
        -1,
        -1,
        0,
        Integer.MIN_VALUE,
        0,
        0,
        1431655765,
        1431655765,
        0,
        Integer.MIN_VALUE,
        0,
        1,
        858993459,
        858993459,
        0,
        715827882,
        715827882,
        0,
        613566756,
        613566756,
        0,
        Integer.MIN_VALUE,
        0,
        2,
        477218588,
        477218588,
        0,
        429496729,
        429496729,
        0,
        390451572,
        390451572,
        0,
        357913941,
        357913941,
        0,
        330382099,
        330382099,
        0,
        306783378,
        306783378,
        0,
        286331153,
        286331153,
        0,
        Integer.MIN_VALUE,
        0,
        3,
        252645135,
        252645135,
        0,
        238609294,
        238609294,
        0,
        226050910,
        226050910,
        0,
        214748364,
        214748364,
        0,
        204522252,
        204522252,
        0,
        195225786,
        195225786,
        0,
        186737708,
        186737708,
        0,
        178956970,
        178956970,
        0,
        171798691,
        171798691,
        0,
        165191049,
        165191049,
        0,
        159072862,
        159072862,
        0,
        153391689,
        153391689,
        0,
        148102320,
        148102320,
        0,
        143165576,
        143165576,
        0,
        138547332,
        138547332,
        0,
        Integer.MIN_VALUE,
        0,
        4,
        130150524,
        130150524,
        0,
        126322567,
        126322567,
        0,
        122713351,
        122713351,
        0,
        119304647,
        119304647,
        0,
        116080197,
        116080197,
        0,
        113025455,
        113025455,
        0,
        110127366,
        110127366,
        0,
        107374182,
        107374182,
        0,
        104755299,
        104755299,
        0,
        102261126,
        102261126,
        0,
        99882960,
        99882960,
        0,
        97612893,
        97612893,
        0,
        95443717,
        95443717,
        0,
        93368854,
        93368854,
        0,
        91382282,
        91382282,
        0,
        89478485,
        89478485,
        0,
        87652393,
        87652393,
        0,
        85899345,
        85899345,
        0,
        84215045,
        84215045,
        0,
        82595524,
        82595524,
        0,
        81037118,
        81037118,
        0,
        79536431,
        79536431,
        0,
        78090314,
        78090314,
        0,
        76695844,
        76695844,
        0,
        75350303,
        75350303,
        0,
        74051160,
        74051160,
        0,
        72796055,
        72796055,
        0,
        71582788,
        71582788,
        0,
        70409299,
        70409299,
        0,
        69273666,
        69273666,
        0,
        68174084,
        68174084,
        0,
        Integer.MIN_VALUE,
        0,
        5
    };
    private final long[] data;
    private final int bits;
    private final long mask;
    private final int size;
    private final int valuesPerLong;
    private final int divideMul; private final long divideMulUnsigned; // Paper - Perf: Optimize SimpleBitStorage; referenced in b(int) with 2 Integer.toUnsignedLong calls
    private final int divideAdd; private final long divideAddUnsigned; // Paper - Perf: Optimize SimpleBitStorage
    private final int divideShift;

    // Paper start - optimise bitstorage read/write operations
    private static final int[] BETTER_MAGIC = new int[33];
    static {
        // 20 bits of precision
        // since index is always [0, 4095] (i.e 12 bits), multiplication by a magic value here (20 bits)
        // fits exactly in an int and allows us to use integer arithmetic
        for (int bits = 1; bits < BETTER_MAGIC.length; ++bits) {
            BETTER_MAGIC[bits] = (int)ca.spottedleaf.concurrentutil.util.IntegerUtil.getUnsignedDivisorMagic(64L / bits, 20);
        }
    }
    private final int magic;
    private final int mulBits;
    // Paper end - optimise bitstorage read/write operations

    public SimpleBitStorage(int bits, int size, int[] data) {
        this(bits, size);
        int i = 0;

        int i1;
        for (i1 = 0; i1 <= size - this.valuesPerLong; i1 += this.valuesPerLong) {
            long l = 0L;

            for (int i2 = this.valuesPerLong - 1; i2 >= 0; i2--) {
                l <<= bits;
                l |= data[i1 + i2] & this.mask;
            }

            this.data[i++] = l;
        }

        int i3 = size - i1;
        if (i3 > 0) {
            long l1 = 0L;

            for (int i4 = i3 - 1; i4 >= 0; i4--) {
                l1 <<= bits;
                l1 |= data[i1 + i4] & this.mask;
            }

            this.data[i] = l1;
        }
    }

    public SimpleBitStorage(int bits, int size) {
        this(bits, size, (long[])null);
    }

    public SimpleBitStorage(int bits, int size, long @Nullable [] data) {
        Validate.inclusiveBetween(1L, 32L, (long)bits);
        this.size = size;
        this.bits = bits;
        this.mask = (1L << bits) - 1L;
        this.valuesPerLong = (char)(64 / bits);
        int i = 3 * (this.valuesPerLong - 1);
        this.divideMul = MAGIC[i + 0]; this.divideMulUnsigned = Integer.toUnsignedLong(this.divideMul); // Paper - Perf: Optimize SimpleBitStorage
        this.divideAdd = MAGIC[i + 1]; this.divideAddUnsigned = Integer.toUnsignedLong(this.divideAdd); // Paper - Perf: Optimize SimpleBitStorage
        this.divideShift = MAGIC[i + 2];
        int i1 = (size + this.valuesPerLong - 1) / this.valuesPerLong;
        if (data != null) {
            if (data.length != i1) {
                throw new SimpleBitStorage.InitializationException("Invalid length given for storage, got: " + data.length + " but expected: " + i1);
            }

            this.data = data;
        } else {
            this.data = new long[i1];
        }
        // Paper start - optimise bitstorage read/write operations
        this.magic = BETTER_MAGIC[this.bits];
        this.mulBits = (64 / this.bits) * this.bits;
        if (this.size > 4096) {
            throw new IllegalStateException("Size > 4096 not supported");
        }
        // Paper end - optimise bitstorage read/write operations
    }

    private int cellIndex(int index) {
        return (int)(index * this.divideMulUnsigned + this.divideAddUnsigned >> 32 >> this.divideShift); // Paper - Perf: Optimize SimpleBitStorage
    }

    @Override
    public final int getAndSet(int index, int value) { // Paper - Perf: Optimize SimpleBitStorage
        // Paper start - optimise bitstorage read/write operations
        final int full = this.magic * index; // 20 bits of magic + 12 bits of index = barely int
        final int divQ = full >>> 20;
        final int divR = (full & 0xFFFFF) * this.mulBits >>> 20;

        final long[] dataArray = this.data;

        final long data = dataArray[divQ];
        final long mask = this.mask;

        final long write = data & ~(mask << divR) | ((long)value & mask) << divR;

        dataArray[divQ] = write;

        return (int)(data >>> divR & mask);
        // Paper end - optimise bitstorage read/write operations
    }

    @Override
    public final void set(int index, int value) { // Paper - Perf: Optimize SimpleBitStorage
        // Paper start - optimise bitstorage read/write operations
        final int full = this.magic * index; // 20 bits of magic + 12 bits of index = barely int
        final int divQ = full >>> 20;
        final int divR = (full & 0xFFFFF) * this.mulBits >>> 20;

        final long[] dataArray = this.data;

        final long data = dataArray[divQ];
        final long mask = this.mask;

        final long write = data & ~(mask << divR) | ((long)value & mask) << divR;

        dataArray[divQ] = write;
        // Paper end - optimise bitstorage read/write operations
    }

    @Override
    public final int get(int index) { // Paper - Perf: Optimize SimpleBitStorage
        // Paper start - optimise bitstorage read/write operations
        final int full = this.magic * index; // 20 bits of magic + 12 bits of index = barely int
        final int divQ = full >>> 20;
        final int divR = (full & 0xFFFFF) * this.mulBits >>> 20;

        return (int)(this.data[divQ] >>> divR & this.mask);
        // Paper end - optimise bitstorage read/write operations
    }

    @Override
    public long[] getRaw() {
        return this.data;
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public int getBits() {
        return this.bits;
    }

    @Override
    public void getAll(IntConsumer consumer) {
        int i = 0;

        for (long l : this.data) {
            for (int i1 = 0; i1 < this.valuesPerLong; i1++) {
                consumer.accept((int)(l & this.mask));
                l >>= this.bits;
                if (++i >= this.size) {
                    return;
                }
            }
        }
    }

    @Override
    public void unpack(int[] array) {
        int i = this.data.length;
        int i1 = 0;

        for (int i2 = 0; i2 < i - 1; i2++) {
            long l = this.data[i2];

            for (int i3 = 0; i3 < this.valuesPerLong; i3++) {
                array[i1 + i3] = (int)(l & this.mask);
                l >>= this.bits;
            }

            i1 += this.valuesPerLong;
        }

        int i2 = this.size - i1;
        if (i2 > 0) {
            long l = this.data[i - 1];

            for (int i3 = 0; i3 < i2; i3++) {
                array[i1 + i3] = (int)(l & this.mask);
                l >>= this.bits;
            }
        }
    }

    @Override
    public BitStorage copy() {
        return new SimpleBitStorage(this.bits, this.size, (long[])this.data.clone());
    }

    // Paper start - block counting
    @Override
    public final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<it.unimi.dsi.fastutil.shorts.ShortArrayList> moonrise$countEntries() {
        final int valuesPerLong = this.valuesPerLong;
        final int bits = this.bits;
        final long mask = (1L << bits) - 1L;
        final int size = this.size;

        if (bits <= 6) {
            final it.unimi.dsi.fastutil.shorts.ShortArrayList[] byId = new it.unimi.dsi.fastutil.shorts.ShortArrayList[1 << bits];
            final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<it.unimi.dsi.fastutil.shorts.ShortArrayList> ret = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(1 << bits);

            int index = 0;

            for (long value : this.data) {
                int li = 0;
                do {
                    final int paletteIdx = (int)(value & mask);
                    value >>= bits;
                    ++li;

                    final it.unimi.dsi.fastutil.shorts.ShortArrayList coords = byId[paletteIdx];
                    if (coords != null) {
                        coords.add((short)index++);
                        continue;
                    } else {
                        final it.unimi.dsi.fastutil.shorts.ShortArrayList newCoords = new it.unimi.dsi.fastutil.shorts.ShortArrayList(64);
                        byId[paletteIdx] = newCoords;
                        newCoords.add((short)index++);
                        ret.put(paletteIdx, newCoords);
                        continue;
                    }
                } while (li < valuesPerLong && index < size);
            }

            return ret;
        } else {
            final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<it.unimi.dsi.fastutil.shorts.ShortArrayList> ret = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(
                1 << 6
            );

            int index = 0;

            for (long value : this.data) {
                int li = 0;
                do {
                    final int paletteIdx = (int)(value & mask);
                    value >>= bits;
                    ++li;

                    ret.computeIfAbsent(paletteIdx, (final int key) -> {
                        return new it.unimi.dsi.fastutil.shorts.ShortArrayList(64);
                    }).add((short)index++);
                } while (li < valuesPerLong && index < size);
            }

            return ret;
        }
    }
    // Paper end - block counting

    public static class InitializationException extends RuntimeException {
        InitializationException(String message) {
            super(message);
        }
    }
}
