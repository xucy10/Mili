package net.minecraft.util.valueproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class ClampedNormalInt extends IntProvider {
    public static final MapCodec<ClampedNormalInt> CODEC = RecordCodecBuilder.<ClampedNormalInt>mapCodec(
            instance -> instance.group(
                    Codec.FLOAT.fieldOf("mean").forGetter(clampedNormalInt -> clampedNormalInt.mean),
                    Codec.FLOAT.fieldOf("deviation").forGetter(clampedNormalInt -> clampedNormalInt.deviation),
                    Codec.INT.fieldOf("min_inclusive").forGetter(clampedNormalInt -> clampedNormalInt.minInclusive),
                    Codec.INT.fieldOf("max_inclusive").forGetter(clampedNormalInt -> clampedNormalInt.maxInclusive)
                )
                .apply(instance, ClampedNormalInt::new)
        )
        .validate(
            clampedNormalInt -> clampedNormalInt.maxInclusive < clampedNormalInt.minInclusive
                ? DataResult.error(() -> "Max must be larger than min: [" + clampedNormalInt.minInclusive + ", " + clampedNormalInt.maxInclusive + "]")
                : DataResult.success(clampedNormalInt)
        );
    private final float mean;
    private final float deviation;
    private final int minInclusive;
    private final int maxInclusive;

    public static ClampedNormalInt of(float mean, float deviation, int minInclusive, int maxInclusive) {
        return new ClampedNormalInt(mean, deviation, minInclusive, maxInclusive);
    }

    private ClampedNormalInt(float mean, float deviation, int minInclusive, int maxInclusive) {
        this.mean = mean;
        this.deviation = deviation;
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    @Override
    public int sample(RandomSource random) {
        return sample(random, this.mean, this.deviation, this.minInclusive, this.maxInclusive);
    }

    public static int sample(RandomSource random, float mean, float deviation, float minInclusive, float maxInclusive) {
        return (int)Mth.clamp(Mth.normal(random, mean, deviation), minInclusive, maxInclusive);
    }

    @Override
    public int getMinValue() {
        return this.minInclusive;
    }

    @Override
    public int getMaxValue() {
        return this.maxInclusive;
    }

    @Override
    public IntProviderType<?> getType() {
        return IntProviderType.CLAMPED_NORMAL;
    }

    @Override
    public String toString() {
        return "normal(" + this.mean + ", " + this.deviation + ") in [" + this.minInclusive + "-" + this.maxInclusive + "]";
    }
}
