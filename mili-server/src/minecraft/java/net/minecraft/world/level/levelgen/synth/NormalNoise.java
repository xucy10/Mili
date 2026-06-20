package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleListIterator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;

public class NormalNoise {
    private static final double INPUT_FACTOR = 1.0181268882175227;
    private static final double TARGET_DEVIATION = 0.3333333333333333;
    private final double valueFactor;
    private final PerlinNoise first;
    private final PerlinNoise second;
    private final double maxValue;
    private final NormalNoise.NoiseParameters parameters;

    @Deprecated
    public static NormalNoise createLegacyNetherBiome(RandomSource random, NormalNoise.NoiseParameters parameters) {
        return new NormalNoise(random, parameters, false);
    }

    public static NormalNoise create(RandomSource random, int firstOctave, double... amplitudes) {
        return create(random, new NormalNoise.NoiseParameters(firstOctave, new DoubleArrayList(amplitudes)));
    }

    public static NormalNoise create(RandomSource random, NormalNoise.NoiseParameters parameters) {
        return new NormalNoise(random, parameters, true);
    }

    private NormalNoise(RandomSource random, NormalNoise.NoiseParameters parameters, boolean useNewFactory) {
        int i = parameters.firstOctave;
        DoubleList list = parameters.amplitudes;
        this.parameters = parameters;
        if (useNewFactory) {
            this.first = PerlinNoise.create(random, i, list);
            this.second = PerlinNoise.create(random, i, list);
        } else {
            this.first = PerlinNoise.createLegacyForLegacyNetherBiome(random, i, list);
            this.second = PerlinNoise.createLegacyForLegacyNetherBiome(random, i, list);
        }

        int i1 = Integer.MAX_VALUE;
        int i2 = Integer.MIN_VALUE;
        DoubleListIterator doubleListIterator = list.iterator();

        while (doubleListIterator.hasNext()) {
            int i3 = doubleListIterator.nextIndex();
            double d = doubleListIterator.nextDouble();
            if (d != 0.0) {
                i1 = Math.min(i1, i3);
                i2 = Math.max(i2, i3);
            }
        }

        this.valueFactor = 0.16666666666666666 / expectedDeviation(i2 - i1);
        this.maxValue = (this.first.maxValue() + this.second.maxValue()) * this.valueFactor;
    }

    public double maxValue() {
        return this.maxValue;
    }

    private static double expectedDeviation(int octaves) {
        return 0.1 * (1.0 + 1.0 / (octaves + 1));
    }

    public double getValue(double x, double y, double z) {
        double d = x * 1.0181268882175227;
        double d1 = y * 1.0181268882175227;
        double d2 = z * 1.0181268882175227;
        return (this.first.getValue(x, y, z) + this.second.getValue(d, d1, d2)) * this.valueFactor;
    }

    public NormalNoise.NoiseParameters parameters() {
        return this.parameters;
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder builder) {
        builder.append("NormalNoise {");
        builder.append("first: ");
        this.first.parityConfigString(builder);
        builder.append(", second: ");
        this.second.parityConfigString(builder);
        builder.append("}");
    }

    public record NoiseParameters(int firstOctave, DoubleList amplitudes) {
        public static final Codec<NormalNoise.NoiseParameters> DIRECT_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.INT.fieldOf("firstOctave").forGetter(NormalNoise.NoiseParameters::firstOctave),
                    Codec.DOUBLE.listOf().fieldOf("amplitudes").forGetter(NormalNoise.NoiseParameters::amplitudes)
                )
                .apply(instance, NormalNoise.NoiseParameters::new)
        );
        public static final Codec<Holder<NormalNoise.NoiseParameters>> CODEC = RegistryFileCodec.create(Registries.NOISE, DIRECT_CODEC);

        public NoiseParameters(int firstOctave, List<Double> amplitudes) {
            this(firstOctave, new DoubleArrayList(amplitudes));
        }

        public NoiseParameters(int firstOctave, double amplitude, double... otherAmplitudes) {
            this(firstOctave, Util.make(new DoubleArrayList(otherAmplitudes), list -> list.add(0, amplitude)));
        }
    }
}
