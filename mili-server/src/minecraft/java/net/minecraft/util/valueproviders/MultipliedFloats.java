package net.minecraft.util.valueproviders;

import java.util.Arrays;
import net.minecraft.util.RandomSource;

public class MultipliedFloats implements SampledFloat {
    private final SampledFloat[] values;

    public MultipliedFloats(SampledFloat... values) {
        this.values = values;
    }

    @Override
    public float sample(RandomSource random) {
        float f = 1.0F;

        for (SampledFloat sampledFloat : this.values) {
            f *= sampledFloat.sample(random);
        }

        return f;
    }

    @Override
    public String toString() {
        return "MultipliedFloats" + Arrays.toString((Object[])this.values);
    }
}
