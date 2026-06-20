package net.minecraft.util.valueproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class ClampedInt extends IntProvider {
    public static final MapCodec<ClampedInt> CODEC = RecordCodecBuilder.<ClampedInt>mapCodec(
            instance -> instance.group(
                    IntProvider.CODEC.fieldOf("source").forGetter(clampedInt -> clampedInt.source),
                    Codec.INT.fieldOf("min_inclusive").forGetter(clampedInt -> clampedInt.minInclusive),
                    Codec.INT.fieldOf("max_inclusive").forGetter(clampedInt -> clampedInt.maxInclusive)
                )
                .apply(instance, ClampedInt::new)
        )
        .validate(
            clampedInt -> clampedInt.maxInclusive < clampedInt.minInclusive
                ? DataResult.error(() -> "Max must be at least min, min_inclusive: " + clampedInt.minInclusive + ", max_inclusive: " + clampedInt.maxInclusive)
                : DataResult.success(clampedInt)
        );
    private final IntProvider source;
    private final int minInclusive;
    private final int maxInclusive;

    public static ClampedInt of(IntProvider source, int minInclusive, int maxInclusive) {
        return new ClampedInt(source, minInclusive, maxInclusive);
    }

    public ClampedInt(IntProvider source, int minInclusive, int maxInclusive) {
        this.source = source;
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    @Override
    public int sample(RandomSource random) {
        return Mth.clamp(this.source.sample(random), this.minInclusive, this.maxInclusive);
    }

    @Override
    public int getMinValue() {
        return Math.max(this.minInclusive, this.source.getMinValue());
    }

    @Override
    public int getMaxValue() {
        return Math.min(this.maxInclusive, this.source.getMaxValue());
    }

    @Override
    public IntProviderType<?> getType() {
        return IntProviderType.CLAMPED;
    }
}
