package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.AbstractDoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleList;

public class NonOverlappingMerger extends AbstractDoubleList implements IndexMerger {
    private final DoubleList lower;
    private final DoubleList upper;
    private final boolean swap;

    protected NonOverlappingMerger(DoubleList lower, DoubleList upper, boolean swap) {
        this.lower = lower;
        this.upper = upper;
        this.swap = swap;
    }

    @Override
    public int size() {
        return this.lower.size() + this.upper.size();
    }

    @Override
    public boolean forMergedIndexes(IndexMerger.IndexConsumer consumer) {
        return this.swap ? this.forNonSwappedIndexes((x, y, z) -> consumer.merge(y, x, z)) : this.forNonSwappedIndexes(consumer);
    }

    private boolean forNonSwappedIndexes(IndexMerger.IndexConsumer consumer) {
        int size = this.lower.size();

        for (int i = 0; i < size; i++) {
            if (!consumer.merge(i, -1, i)) {
                return false;
            }
        }

        int ix = this.upper.size() - 1;

        for (int i1 = 0; i1 < ix; i1++) {
            if (!consumer.merge(size - 1, i1, size + i1)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public double getDouble(int index) {
        return index < this.lower.size() ? this.lower.getDouble(index) : this.upper.getDouble(index - this.lower.size());
    }

    @Override
    public DoubleList getList() {
        return this;
    }
}
