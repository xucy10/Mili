package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;

public class IdenticalMerger implements IndexMerger {
    private final DoubleList coords;

    public IdenticalMerger(DoubleList coords) {
        this.coords = coords;
    }

    @Override
    public boolean forMergedIndexes(IndexMerger.IndexConsumer consumer) {
        int i = this.coords.size() - 1;

        for (int i1 = 0; i1 < i; i1++) {
            if (!consumer.merge(i1, i1, i1)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int size() {
        return this.coords.size();
    }

    @Override
    public DoubleList getList() {
        return this.coords;
    }
}
