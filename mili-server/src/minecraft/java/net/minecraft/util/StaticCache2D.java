package net.minecraft.util;

import java.util.Locale;
import java.util.function.Consumer;

public class StaticCache2D<T> {
    private final int minX;
    private final int minZ;
    private final int sizeX;
    private final int sizeZ;
    private final Object[] cache;

    public static <T> StaticCache2D<T> create(int centerX, int centerZ, int size, StaticCache2D.Initializer<T> initializer) {
        int i = centerX - size;
        int i1 = centerZ - size;
        int i2 = 2 * size + 1;
        return new StaticCache2D<>(i, i1, i2, i2, initializer);
    }

    private StaticCache2D(int minX, int minZ, int sizeX, int sizeZ, StaticCache2D.Initializer<T> initializer) {
        this.minX = minX;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.cache = new Object[this.sizeX * this.sizeZ];

        for (int i = minX; i < minX + sizeX; i++) {
            for (int i1 = minZ; i1 < minZ + sizeZ; i1++) {
                this.cache[this.getIndex(i, i1)] = initializer.get(i, i1);
            }
        }
    }

    public void forEach(Consumer<T> action) {
        for (Object object : this.cache) {
            action.accept((T)object);
        }
    }

    public T get(int x, int z) {
        if (!this.contains(x, z)) {
            throw new IllegalArgumentException("Requested out of range value (" + x + "," + z + ") from " + this);
        } else {
            return (T)this.cache[this.getIndex(x, z)];
        }
    }

    public boolean contains(int x, int z) {
        int i = x - this.minX;
        int i1 = z - this.minZ;
        return i >= 0 && i < this.sizeX && i1 >= 0 && i1 < this.sizeZ;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "StaticCache2D[%d, %d, %d, %d]", this.minX, this.minZ, this.minX + this.sizeX, this.minZ + this.sizeZ);
    }

    private int getIndex(int x, int z) {
        int i = x - this.minX;
        int i1 = z - this.minZ;
        return i * this.sizeZ + i1;
    }

    @FunctionalInterface
    public interface Initializer<T> {
        T get(int x, int z);
    }
}
