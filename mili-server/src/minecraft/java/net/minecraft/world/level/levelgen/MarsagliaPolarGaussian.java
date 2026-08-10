package net.minecraft.world.level.levelgen;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class MarsagliaPolarGaussian {
    public final RandomSource randomSource;
    private double nextNextGaussian;
    private boolean haveNextNextGaussian;

    public MarsagliaPolarGaussian(RandomSource randomSource) {
        this.randomSource = randomSource;
    }

    public void reset() {
        this.haveNextNextGaussian = false;
    }

    public double nextGaussian() {
        if (this.haveNextNextGaussian) {
            this.haveNextNextGaussian = false;
            return this.nextNextGaussian;
        } else {
            double d;
            double d1;
            double d2;
            do {
                d = 2.0 * this.randomSource.nextDouble() - 1.0;
                d1 = 2.0 * this.randomSource.nextDouble() - 1.0;
                d2 = Mth.square(d) + Mth.square(d1);
            } while (d2 >= 1.0 || d2 == 0.0);

            double squareRoot = Math.sqrt(-2.0 * Math.log(d2) / d2);
            this.nextNextGaussian = d1 * squareRoot;
            this.haveNextNextGaussian = true;
            return d * squareRoot;
        }
    }
}
