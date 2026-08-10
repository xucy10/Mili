package net.minecraft.util.valueproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class UniformInt extends IntProvider {
    public static final MapCodec<UniformInt> CODEC = RecordCodecBuilder.<UniformInt>mapCodec(
            instance -> instance.group(
                    Codec.INT.fieldOf("min_inclusive").forGetter(uniformInt -> uniformInt.minInclusive),
                    Codec.INT.fieldOf("max_inclusive").forGetter(uniformInt -> uniformInt.maxInclusive)
                )
                .apply(instance, UniformInt::new)
        )
        .validate(
            uniformInt -> uniformInt.maxInclusive < uniformInt.minInclusive
                ? DataResult.error(() -> "Max must be at least min, min_inclusive: " + uniformInt.minInclusive + ", max_inclusive: " + uniformInt.maxInclusive)
                : DataResult.success(uniformInt)
        );
    private final int minInclusive;
    private final int maxInclusive;

    private UniformInt(int minInclusive, int maxInclusive) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public static UniformInt of(int minInclusive, int maxInclusive) {
        return new UniformInt(minInclusive, maxInclusive);
    }

    @Override
    public int sample(RandomSource random) {
        return Mth.randomBetweenInclusive(random, this.minInclusive, this.maxInclusive);
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
        return IntProviderType.UNIFORM;
    }

    @Override
    public String toString() {
        return "[" + this.minInclusive + "-" + this.maxInclusive + "]";
    }
}
