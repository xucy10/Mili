package net.minecraft.world.attribute;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class GaussianSampler {
    private static final int GAUSSIAN_SAMPLE_RADIUS = 2;
    private static final int GAUSSIAN_SAMPLE_BREADTH = 6;
    private static final double[] GAUSSIAN_SAMPLE_KERNEL = new double[]{0.0, 1.0, 4.0, 6.0, 4.0, 1.0, 0.0};

    public static <V> void sample(Vec3 pos, GaussianSampler.Sampler<V> sampler, GaussianSampler.Accumulator<V> accumulator) {
        pos = pos.subtract(0.5, 0.5, 0.5);
        int floor = Mth.floor(pos.x());
        int floor1 = Mth.floor(pos.y());
        int floor2 = Mth.floor(pos.z());
        double d = pos.x() - floor;
        double d1 = pos.y() - floor1;
        double d2 = pos.z() - floor2;

        for (int i = 0; i < 6; i++) {
            double d3 = Mth.lerp(d2, GAUSSIAN_SAMPLE_KERNEL[i + 1], GAUSSIAN_SAMPLE_KERNEL[i]);
            int i1 = floor2 - 2 + i;

            for (int i2 = 0; i2 < 6; i2++) {
                double d4 = Mth.lerp(d, GAUSSIAN_SAMPLE_KERNEL[i2 + 1], GAUSSIAN_SAMPLE_KERNEL[i2]);
                int i3 = floor - 2 + i2;

                for (int i4 = 0; i4 < 6; i4++) {
                    double d5 = Mth.lerp(d1, GAUSSIAN_SAMPLE_KERNEL[i4 + 1], GAUSSIAN_SAMPLE_KERNEL[i4]);
                    int i5 = floor1 - 2 + i4;
                    double d6 = d4 * d5 * d3;
                    V object = sampler.get(i3, i5, i1);
                    accumulator.accumulate(d6, object);
                }
            }
        }
    }

    @FunctionalInterface
    public interface Accumulator<V> {
        void accumulate(double weight, V value);
    }

    @FunctionalInterface
    public interface Sampler<V> {
        V get(int x, int y, int z);
    }
}
