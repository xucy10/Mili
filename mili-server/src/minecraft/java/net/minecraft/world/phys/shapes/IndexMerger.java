package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;

interface IndexMerger {
    DoubleList getList();

    boolean forMergedIndexes(IndexMerger.IndexConsumer consumer);

    int size();

    public interface IndexConsumer {
        boolean merge(int firstValue, int secondValue, int thirdValue);
    }
}
