package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleLists;

public class IndirectMerger implements IndexMerger {
    private static final DoubleList EMPTY = DoubleLists.unmodifiable(DoubleArrayList.wrap(new double[]{0.0}));
    private final double[] result;
    private final int[] firstIndices;
    private final int[] secondIndices;
    private final int resultLength;
    // Paper start
    private static final int[] INFINITE_B_1 = {1, 1};
    private static final int[] INFINITE_B_0 = {0, 0};
    private static final int[] INFINITE_C = {0, 1};
    // Paper end

    public IndirectMerger(DoubleList lower, DoubleList upper, boolean excludeUpper, boolean excludeLower) {
        double d = Double.NaN;
        int size = lower.size();
        int size1 = upper.size();
        int i = size + size1;
        // Paper start - optimize common path of infinity doublelist
        double tail = lower.getDouble(size - 1);
        double head = lower.getDouble(0);
        if (head == Double.NEGATIVE_INFINITY && tail == Double.POSITIVE_INFINITY && !excludeUpper && !excludeLower && (size == 2 || size == 4)) {
            this.result = upper.toDoubleArray();
            this.resultLength = upper.size();
            if (size == 2) {
                this.firstIndices = INFINITE_B_0;
            } else {
                this.firstIndices = INFINITE_B_1;
            }
            this.secondIndices = INFINITE_C;
            return;
        }
        // Paper end
        this.result = new double[i];
        this.firstIndices = new int[i];
        this.secondIndices = new int[i];
        boolean flag = !excludeUpper;
        boolean flag1 = !excludeLower;
        int i1 = 0;
        int i2 = 0;
        int i3 = 0;

        while (true) {
            boolean flag2 = i2 >= size;
            boolean flag3 = i3 >= size1;
            if (flag2 && flag3) {
                this.resultLength = Math.max(1, i1);
                return;
            }

            boolean flag4 = !flag2 && (flag3 || lower.getDouble(i2) < upper.getDouble(i3) + 1.0E-7);
            if (flag4) {
                i2++;
                if (flag && (i3 == 0 || flag3)) {
                    continue;
                }
            } else {
                i3++;
                if (flag1 && (i2 == 0 || flag2)) {
                    continue;
                }
            }

            int i4 = i2 - 1;
            int i5 = i3 - 1;
            double d1 = flag4 ? lower.getDouble(i4) : upper.getDouble(i5);
            if (!(d >= d1 - 1.0E-7)) {
                this.firstIndices[i1] = i4;
                this.secondIndices[i1] = i5;
                this.result[i1] = d1;
                i1++;
                d = d1;
            } else {
                this.firstIndices[i1 - 1] = i4;
                this.secondIndices[i1 - 1] = i5;
            }
        }
    }

    @Override
    public boolean forMergedIndexes(IndexMerger.IndexConsumer consumer) {
        int i = this.resultLength - 1;

        for (int i1 = 0; i1 < i; i1++) {
            if (!consumer.merge(this.firstIndices[i1], this.secondIndices[i1], i1)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int size() {
        return this.resultLength;
    }

    @Override
    public DoubleList getList() {
        return (DoubleList)(this.resultLength <= 1 ? EMPTY : DoubleArrayList.wrap(this.result, this.resultLength));
    }
}
