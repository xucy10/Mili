package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.stream.IntStream;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

public class BlendedNoise implements DensityFunction.SimpleFunction {
    private static final Codec<Double> SCALE_RANGE = Codec.doubleRange(0.001, 1000.0);
    private static final MapCodec<BlendedNoise> DATA_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                SCALE_RANGE.fieldOf("xz_scale").forGetter(blendedNoise -> blendedNoise.xzScale),
                SCALE_RANGE.fieldOf("y_scale").forGetter(blendedNoise -> blendedNoise.yScale),
                SCALE_RANGE.fieldOf("xz_factor").forGetter(blendedNoise -> blendedNoise.xzFactor),
                SCALE_RANGE.fieldOf("y_factor").forGetter(blendedNoise -> blendedNoise.yFactor),
                Codec.doubleRange(1.0, 8.0).fieldOf("smear_scale_multiplier").forGetter(blendedNoise -> blendedNoise.smearScaleMultiplier)
            )
            .apply(instance, BlendedNoise::createUnseeded)
    );
    public static final KeyDispatchDataCodec<BlendedNoise> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
    private final PerlinNoise minLimitNoise;
    private final PerlinNoise maxLimitNoise;
    private final PerlinNoise mainNoise;
    private final double xzMultiplier;
    private final double yMultiplier;
    private final double xzFactor;
    private final double yFactor;
    private final double smearScaleMultiplier;
    private final double maxValue;
    private final double xzScale;
    private final double yScale;

    public static BlendedNoise createUnseeded(double xzScale, double yScale, double xzFactor, double yFactor, double smearScaleMultiplier) {
        return new BlendedNoise(new XoroshiroRandomSource(0L), xzScale, yScale, xzFactor, yFactor, smearScaleMultiplier);
    }

    private BlendedNoise(
        PerlinNoise minLimitNoise,
        PerlinNoise maxLimitNoise,
        PerlinNoise mainNoise,
        double xzScale,
        double yScale,
        double xzFactor,
        double yFactor,
        double smearScaleMultiplier
    ) {
        this.minLimitNoise = minLimitNoise;
        this.maxLimitNoise = maxLimitNoise;
        this.mainNoise = mainNoise;
        this.xzScale = xzScale;
        this.yScale = yScale;
        this.xzFactor = xzFactor;
        this.yFactor = yFactor;
        this.smearScaleMultiplier = smearScaleMultiplier;
        this.xzMultiplier = 684.412 * this.xzScale;
        this.yMultiplier = 684.412 * this.yScale;
        this.maxValue = minLimitNoise.maxBrokenValue(this.yMultiplier);
    }

    @VisibleForTesting
    public BlendedNoise(RandomSource random, double xzScale, double yScale, double xzFactor, double yFactor, double smearScaleMultiplier) {
        this(
            PerlinNoise.createLegacyForBlendedNoise(random, IntStream.rangeClosed(-15, 0)),
            PerlinNoise.createLegacyForBlendedNoise(random, IntStream.rangeClosed(-15, 0)),
            PerlinNoise.createLegacyForBlendedNoise(random, IntStream.rangeClosed(-7, 0)),
            xzScale,
            yScale,
            xzFactor,
            yFactor,
            smearScaleMultiplier
        );
    }

    public BlendedNoise withNewRandom(RandomSource random) {
        return new BlendedNoise(random, this.xzScale, this.yScale, this.xzFactor, this.yFactor, this.smearScaleMultiplier);
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        double d = context.blockX() * this.xzMultiplier;
        double d1 = context.blockY() * this.yMultiplier;
        double d2 = context.blockZ() * this.xzMultiplier;
        double d3 = d / this.xzFactor;
        double d4 = d1 / this.yFactor;
        double d5 = d2 / this.xzFactor;
        double d6 = this.yMultiplier * this.smearScaleMultiplier;
        double d7 = d6 / this.yFactor;
        double d8 = 0.0;
        double d9 = 0.0;
        double d10 = 0.0;
        boolean flag = true;
        double d11 = 1.0;

        for (int i = 0; i < 8; i++) {
            ImprovedNoise octaveNoise = this.mainNoise.getOctaveNoise(i);
            if (octaveNoise != null) {
                d10 += octaveNoise.noise(PerlinNoise.wrap(d3 * d11), PerlinNoise.wrap(d4 * d11), PerlinNoise.wrap(d5 * d11), d7 * d11, d4 * d11) / d11;
            }

            d11 /= 2.0;
        }

        double d12 = (d10 / 10.0 + 1.0) / 2.0;
        boolean flag1 = d12 >= 1.0;
        boolean flag2 = d12 <= 0.0;
        d11 = 1.0;

        for (int i1 = 0; i1 < 16; i1++) {
            double d13 = PerlinNoise.wrap(d * d11);
            double d14 = PerlinNoise.wrap(d1 * d11);
            double d15 = PerlinNoise.wrap(d2 * d11);
            double d16 = d6 * d11;
            if (!flag1) {
                ImprovedNoise octaveNoise1 = this.minLimitNoise.getOctaveNoise(i1);
                if (octaveNoise1 != null) {
                    d8 += octaveNoise1.noise(d13, d14, d15, d16, d1 * d11) / d11;
                }
            }

            if (!flag2) {
                ImprovedNoise octaveNoise1 = this.maxLimitNoise.getOctaveNoise(i1);
                if (octaveNoise1 != null) {
                    d9 += octaveNoise1.noise(d13, d14, d15, d16, d1 * d11) / d11;
                }
            }

            d11 /= 2.0;
        }

        return Mth.clampedLerp(d12, d8 / 512.0, d9 / 512.0) / 128.0;
    }

    @Override
    public double minValue() {
        return -this.maxValue();
    }

    @Override
    public double maxValue() {
        return this.maxValue;
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder builder) {
        builder.append("BlendedNoise{minLimitNoise=");
        this.minLimitNoise.parityConfigString(builder);
        builder.append(", maxLimitNoise=");
        this.maxLimitNoise.parityConfigString(builder);
        builder.append(", mainNoise=");
        this.mainNoise.parityConfigString(builder);
        builder.append(
                String.format(
                    Locale.ROOT,
                    ", xzScale=%.3f, yScale=%.3f, xzMainScale=%.3f, yMainScale=%.3f, cellWidth=4, cellHeight=8",
                    684.412,
                    684.412,
                    8.555150000000001,
                    4.277575000000001
                )
            )
            .append('}');
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
