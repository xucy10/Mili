package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.IntStream;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.jspecify.annotations.Nullable;

public class PerlinNoise {
    private static final int ROUND_OFF = 33554432;
    private final @Nullable ImprovedNoise[] noiseLevels;
    private final int firstOctave;
    private final DoubleList amplitudes;
    private final double lowestFreqValueFactor;
    private final double lowestFreqInputFactor;
    private final double maxValue;
    // Gale start - C2ME - optimize noise generation
    private final int octaveSamplersCount;
    private final double [] amplitudesArray;
    // Gale end - C2ME - optimize noise generation

    @Deprecated
    public static PerlinNoise createLegacyForBlendedNoise(RandomSource random, IntStream octaves) {
        return new PerlinNoise(random, makeAmplitudes(new IntRBTreeSet(octaves.boxed().collect(ImmutableList.toImmutableList()))), false);
    }

    @Deprecated
    public static PerlinNoise createLegacyForLegacyNetherBiome(RandomSource random, int firstOctave, DoubleList amplitudes) {
        return new PerlinNoise(random, Pair.of(firstOctave, amplitudes), false);
    }

    public static PerlinNoise create(RandomSource random, IntStream octaves) {
        return create(random, octaves.boxed().collect(ImmutableList.toImmutableList()));
    }

    public static PerlinNoise create(RandomSource random, List<Integer> octaves) {
        return new PerlinNoise(random, makeAmplitudes(new IntRBTreeSet(octaves)), true);
    }

    public static PerlinNoise create(RandomSource random, int firstOctave, double firstAmplitude, double... amplitudes) {
        DoubleArrayList list = new DoubleArrayList(amplitudes);
        list.add(0, firstAmplitude);
        return new PerlinNoise(random, Pair.of(firstOctave, list), true);
    }

    public static PerlinNoise create(RandomSource random, int firstOctave, DoubleList amplitudes) {
        return new PerlinNoise(random, Pair.of(firstOctave, amplitudes), true);
    }

    private static Pair<Integer, DoubleList> makeAmplitudes(IntSortedSet octaves) {
        if (octaves.isEmpty()) {
            throw new IllegalArgumentException("Need some octaves!");
        } else {
            int i = -octaves.firstInt();
            int i1 = octaves.lastInt();
            int i2 = i + i1 + 1;
            if (i2 < 1) {
                throw new IllegalArgumentException("Total number of octaves needs to be >= 1");
            } else {
                DoubleList list = new DoubleArrayList(new double[i2]);
                IntBidirectionalIterator intBidirectionalIterator = octaves.iterator();

                while (intBidirectionalIterator.hasNext()) {
                    int i3 = intBidirectionalIterator.nextInt();
                    list.set(i3 + i, 1.0);
                }

                return Pair.of(-i, list);
            }
        }
    }

    protected PerlinNoise(RandomSource random, Pair<Integer, DoubleList> octavesAndAmplitudes, boolean useNewFactory) {
        this.firstOctave = octavesAndAmplitudes.getFirst();
        this.amplitudes = octavesAndAmplitudes.getSecond();
        int size = this.amplitudes.size();
        int i = -this.firstOctave;
        this.noiseLevels = new ImprovedNoise[size];
        if (useNewFactory) {
            PositionalRandomFactory positionalRandomFactory = random.forkPositional();

            for (int i1 = 0; i1 < size; i1++) {
                if (this.amplitudes.getDouble(i1) != 0.0) {
                    int i2 = this.firstOctave + i1;
                    this.noiseLevels[i1] = new ImprovedNoise(positionalRandomFactory.fromHashOf("octave_" + i2));
                }
            }
        } else {
            ImprovedNoise improvedNoise = new ImprovedNoise(random);
            if (i >= 0 && i < size) {
                double _double = this.amplitudes.getDouble(i);
                if (_double != 0.0) {
                    this.noiseLevels[i] = improvedNoise;
                }
            }

            for (int i1x = i - 1; i1x >= 0; i1x--) {
                if (i1x < size) {
                    double _double1 = this.amplitudes.getDouble(i1x);
                    if (_double1 != 0.0) {
                        this.noiseLevels[i1x] = new ImprovedNoise(random);
                    } else {
                        skipOctave(random);
                    }
                } else {
                    skipOctave(random);
                }
            }

            if (Arrays.stream(this.noiseLevels).filter(Objects::nonNull).count() != this.amplitudes.stream().filter(amplitude -> amplitude != 0.0).count()) {
                throw new IllegalStateException("Failed to create correct number of noise levels for given non-zero amplitudes");
            }

            if (i < size - 1) {
                throw new IllegalArgumentException("Positive octaves are temporarily disabled");
            }
        }

        this.lowestFreqInputFactor = Math.pow(2.0, -i);
        this.lowestFreqValueFactor = Math.pow(2.0, size - 1) / (Math.pow(2.0, size) - 1.0);
        this.maxValue = this.edgeValue(2.0);
        // Gale start - C2ME - optimize noise generation
        this.octaveSamplersCount = this.noiseLevels.length;
        this.amplitudesArray = this.amplitudes.toDoubleArray();
        // Gale end - C2ME - optimize noise generation
    }

    protected double maxValue() {
        return this.maxValue;
    }

    private static void skipOctave(RandomSource random) {
        random.consumeCount(262);
    }

    public double getValue(double x, double y, double z) {
        // Gale start - C2ME - optimize noise generation - optimize for common cases
        double d = 0.0;
        double e = this.lowestFreqInputFactor;
        double f = this.lowestFreqValueFactor;

        for (int i = 0; i < this.octaveSamplersCount; ++i) {
            ImprovedNoise perlinNoiseSampler = this.noiseLevels[i];
            if (perlinNoiseSampler != null) {
                @SuppressWarnings("deprecation")
                double g = perlinNoiseSampler.noise(
                    wrap(x * e), wrap(y * e), wrap(z * e), 0.0, 0.0
                );
                d += this.amplitudesArray[i] * g * f;
            }

            e *= 2.0;
            f /= 2.0;
        }

        return d;
        // Gale end - C2ME - optimize noise generation - optimize for common cases
    }

    @Deprecated
    public double getValue(double x, double y, double z, double yScale, double yMax, boolean useFixedY) {
        double d = 0.0;
        double d1 = this.lowestFreqInputFactor;
        double d2 = this.lowestFreqValueFactor;

        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise improvedNoise = this.noiseLevels[i];
            if (improvedNoise != null) {
                double d3 = improvedNoise.noise(wrap(x * d1), useFixedY ? -improvedNoise.yo : wrap(y * d1), wrap(z * d1), yScale * d1, yMax * d1);
                d += this.amplitudes.getDouble(i) * d3 * d2;
            }

            d1 *= 2.0;
            d2 /= 2.0;
        }

        return d;
    }

    public double maxBrokenValue(double yMultiplier) {
        return this.edgeValue(yMultiplier + 2.0);
    }

    private double edgeValue(double multiplier) {
        double d = 0.0;
        double d1 = this.lowestFreqValueFactor;

        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise improvedNoise = this.noiseLevels[i];
            if (improvedNoise != null) {
                d += this.amplitudes.getDouble(i) * multiplier * d1;
            }

            d1 /= 2.0;
        }

        return d;
    }

    public @Nullable ImprovedNoise getOctaveNoise(int octave) {
        return this.noiseLevels[this.noiseLevels.length - 1 - octave];
    }

    public static double wrap(double value) {
        return value - Mth.lfloor(value / 3.3554432E7 + 0.5) * 3.3554432E7;
    }

    protected int firstOctave() {
        return this.firstOctave;
    }

    protected DoubleList amplitudes() {
        return this.amplitudes;
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder builder) {
        builder.append("PerlinNoise{");
        List<String> list = this.amplitudes.stream().map(amplitude -> String.format(Locale.ROOT, "%.2f", amplitude)).toList();
        builder.append("first octave: ").append(this.firstOctave).append(", amplitudes: ").append(list).append(", noise levels: [");

        for (int i = 0; i < this.noiseLevels.length; i++) {
            builder.append(i).append(": ");
            ImprovedNoise improvedNoise = this.noiseLevels[i];
            if (improvedNoise == null) {
                builder.append("null");
            } else {
                improvedNoise.parityConfigString(builder);
            }

            builder.append(", ");
        }

        builder.append("]");
        builder.append("}");
    }
}
