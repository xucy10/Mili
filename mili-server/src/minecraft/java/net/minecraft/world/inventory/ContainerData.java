package net.minecraft.world.inventory;

public interface ContainerData {
    int get(int index);

    void set(int index, int value);

    int getCount();
}
