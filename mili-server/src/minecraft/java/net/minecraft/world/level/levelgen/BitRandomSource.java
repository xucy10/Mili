package net.minecraft.world.level.levelgen;

import net.minecraft.util.RandomSource;

public interface BitRandomSource extends RandomSource {
    float FLOAT_MULTIPLIER = 5.9604645E-8F;
    double DOUBLE_MULTIPLIER = 1.110223E-16F;

    int next(int size);

    @Override
    default int nextInt() {
        return this.next(32);
    }

    @Override
    default int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        } else if ((bound & bound - 1) == 0) {
            return (int)((long)bound * this.next(31) >> 31);
        } else {
            int i;
            int i1;
            do {
                i = this.next(31);
                i1 = i % bound;
            } while (i - i1 + (bound - 1) < 0);

            return i1;
        }
    }

    @Override
    default long nextLong() {
        int i = this.next(32);
        int i1 = this.next(32);
        long l = (long)i << 32;
        return l + i1;
    }

    @Override
    default boolean nextBoolean() {
        return this.next(1) != 0;
    }

    @Override
    default float nextFloat() {
        return this.next(24) * 5.9604645E-8F;
    }

    @Override
    default double nextDouble() {
        int i = this.next(26);
        int i1 = this.next(27);
        long l = ((long)i << 27) + i1;
        return l * 1.110223E-16F;
    }
}
