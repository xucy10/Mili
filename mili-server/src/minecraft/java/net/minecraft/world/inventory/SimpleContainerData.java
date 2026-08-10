package net.minecraft.world.inventory;

public class SimpleContainerData implements ContainerData {
    private final int[] ints;

    public SimpleContainerData(int size) {
        this.ints = new int[size];
    }

    @Override
    public int get(int index) {
        return this.ints[index];
    }

    @Override
    public void set(int index, int value) {
        this.ints[index] = value;
    }

    @Override
    public int getCount() {
        return this.ints.length;
    }
}
