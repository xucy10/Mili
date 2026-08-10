package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.NoSuchElementException;
import net.minecraft.util.Mth;

public class SpatialLongSet extends LongLinkedOpenHashSet {
    private final SpatialLongSet.InternalMap map;

    public SpatialLongSet(int expectedSize, float loadFactor) {
        super(expectedSize, loadFactor);
        this.map = new SpatialLongSet.InternalMap(expectedSize / 64, loadFactor);
    }

    @Override
    public boolean add(long value) {
        return this.map.addBit(value);
    }

    @Override
    public boolean rem(long value) {
        return this.map.removeBit(value);
    }

    @Override
    public long removeFirstLong() {
        return this.map.removeFirstBit();
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    protected static class InternalMap extends Long2LongLinkedOpenHashMap {
        private static final int X_BITS = Mth.log2(60000000);
        private static final int Z_BITS = Mth.log2(60000000);
        private static final int Y_BITS = 64 - X_BITS - Z_BITS;
        private static final int Y_OFFSET = 0;
        private static final int Z_OFFSET = Y_BITS;
        private static final int X_OFFSET = Y_BITS + Z_BITS;
        private static final long OUTER_MASK = 3L << X_OFFSET | 3L | 3L << Z_OFFSET;
        private int lastPos = -1;
        private long lastOuterKey;
        private final int minSize;

        public InternalMap(int minSize, float loadFactor) {
            super(minSize, loadFactor);
            this.minSize = minSize;
        }

        static long getOuterKey(long value) {
            return value & ~OUTER_MASK;
        }

        static int getInnerKey(long value) {
            int i = (int)(value >>> X_OFFSET & 3L);
            int i1 = (int)(value >>> 0 & 3L);
            int i2 = (int)(value >>> Z_OFFSET & 3L);
            return i << 4 | i2 << 2 | i1;
        }

        static long getFullKey(long value, int trailingZeros) {
            value |= (long)(trailingZeros >>> 4 & 3) << X_OFFSET;
            value |= (long)(trailingZeros >>> 2 & 3) << Z_OFFSET;
            return value | (long)(trailingZeros >>> 0 & 3) << 0;
        }

        public boolean addBit(long value) {
            long outerKey = getOuterKey(value);
            int innerKey = getInnerKey(value);
            long l = 1L << innerKey;
            int i;
            if (outerKey == 0L) {
                if (this.containsNullKey) {
                    return this.replaceBit(this.n, l);
                }

                this.containsNullKey = true;
                i = this.n;
            } else {
                if (this.lastPos != -1 && outerKey == this.lastOuterKey) {
                    return this.replaceBit(this.lastPos, l);
                }

                long[] longs = this.key;
                i = (int)HashCommon.mix(outerKey) & this.mask;

                for (long l1 = longs[i]; l1 != 0L; l1 = longs[i]) {
                    if (l1 == outerKey) {
                        this.lastPos = i;
                        this.lastOuterKey = outerKey;
                        return this.replaceBit(i, l);
                    }

                    i = i + 1 & this.mask;
                }
            }

            this.key[i] = outerKey;
            this.value[i] = l;
            if (this.size == 0) {
                this.first = this.last = i;
                this.link[i] = -1L;
            } else {
                this.link[this.last] = this.link[this.last] ^ (this.link[this.last] ^ i & 4294967295L) & 4294967295L;
                this.link[i] = (this.last & 4294967295L) << 32 | 4294967295L;
                this.last = i;
            }

            if (this.size++ >= this.maxFill) {
                this.rehash(HashCommon.arraySize(this.size + 1, this.f));
            }

            return false;
        }

        private boolean replaceBit(int index, long value) {
            boolean flag = (this.value[index] & value) != 0L;
            this.value[index] = this.value[index] | value;
            return flag;
        }

        public boolean removeBit(long value) {
            long outerKey = getOuterKey(value);
            int innerKey = getInnerKey(value);
            long l = 1L << innerKey;
            if (outerKey == 0L) {
                return this.containsNullKey && this.removeFromNullEntry(l);
            } else if (this.lastPos != -1 && outerKey == this.lastOuterKey) {
                return this.removeFromEntry(this.lastPos, l);
            } else {
                long[] longs = this.key;
                int i = (int)HashCommon.mix(outerKey) & this.mask;

                for (long l1 = longs[i]; l1 != 0L; l1 = longs[i]) {
                    if (outerKey == l1) {
                        this.lastPos = i;
                        this.lastOuterKey = outerKey;
                        return this.removeFromEntry(i, l);
                    }

                    i = i + 1 & this.mask;
                }

                return false;
            }
        }

        private boolean removeFromNullEntry(long value) {
            if ((this.value[this.n] & value) == 0L) {
                return false;
            } else {
                this.value[this.n] = this.value[this.n] & ~value;
                if (this.value[this.n] != 0L) {
                    return true;
                } else {
                    this.containsNullKey = false;
                    this.size--;
                    this.fixPointers(this.n);
                    if (this.size < this.maxFill / 4 && this.n > 16) {
                        this.rehash(this.n / 2);
                    }

                    return true;
                }
            }
        }

        private boolean removeFromEntry(int index, long value) {
            if ((this.value[index] & value) == 0L) {
                return false;
            } else {
                this.value[index] = this.value[index] & ~value;
                if (this.value[index] != 0L) {
                    return true;
                } else {
                    this.lastPos = -1;
                    this.size--;
                    this.fixPointers(index);
                    this.shiftKeys(index);
                    if (this.size < this.maxFill / 4 && this.n > 16) {
                        this.rehash(this.n / 2);
                    }

                    return true;
                }
            }
        }

        public long removeFirstBit() {
            if (this.size == 0) {
                throw new NoSuchElementException();
            } else {
                int i = this.first;
                long l = this.key[i];
                int i1 = Long.numberOfTrailingZeros(this.value[i]);
                this.value[i] = this.value[i] & ~(1L << i1);
                if (this.value[i] == 0L) {
                    this.removeFirstLong();
                    this.lastPos = -1;
                }

                return getFullKey(l, i1);
            }
        }

        @Override
        protected void rehash(int newSize) {
            if (newSize > this.minSize) {
                super.rehash(newSize);
            }
        }
    }
}
