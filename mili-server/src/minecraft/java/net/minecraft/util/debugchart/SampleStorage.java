package net.minecraft.util.debugchart;

public interface SampleStorage {
    int capacity();

    int size();

    long get(int index);

    long get(int index, int dimension);

    void reset();
}
