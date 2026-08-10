package net.minecraft.world.level.levelgen.synth;

import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.jspecify.annotations.Nullable;

public class PerlinSimplexNoise {
    private final @Nullable SimplexNoise[] noiseLevels;
    private final double highestFreqValueFactor;
    private final double highestFreqInputFactor;

    public PerlinSimplexNoise(RandomSource random, List<Integer> octaves) {
        this(random, new IntRBTreeSet(octaves));
    }

    private PerlinSimplexNoise(RandomSource random, IntSortedSet octaves) {
        if (octaves.isEmpty()) {
            throw new IllegalArgumentException("Need some octaves!");
        } else {
            int i = -octaves.firstInt();
            int i1 = octaves.lastInt();
            int i2 = i + i1 + 1;
            if (i2 < 1) {
                throw new IllegalArgumentException("Total number of octaves needs to be >= 1");
            } else {
                SimplexNoise simplexNoise = new SimplexNoise(random);
                int i3 = i1;
                this.noiseLevels = new SimplexNoise[i2];
                if (i1 >= 0 && i1 < i2 && octaves.contains(0)) {
                    this.noiseLevels[i1] = simplexNoise;
                }

                for (int i4 = i1 + 1; i4 < i2; i4++) {
                    if (i4 >= 0 && octaves.contains(i3 - i4)) {
                        this.noiseLevels[i4] = new SimplexNoise(random);
                    } else {
                        random.consumeCount(262);
                    }
                }

                if (i1 > 0) {
                    long l = (long)(simplexNoise.getValue(simplexNoise.xo, simplexNoise.yo, simplexNoise.zo) * 9.223372E18F);
                    RandomSource randomSource = new WorldgenRandom(new LegacyRandomSource(l));

                    for (int i5 = i3 - 1; i5 >= 0; i5--) {
                        if (i5 < i2 && octaves.contains(i3 - i5)) {
                            this.noiseLevels[i5] = new SimplexNoise(randomSource);
                        } else {
                            randomSource.consumeCount(262);
                        }
                    }
                }

                this.highestFreqInputFactor = Math.pow(2.0, i1);
                this.highestFreqValueFactor = 1.0 / (Math.pow(2.0, i2) - 1.0);
            }
        }
    }

    public double getValue(double x, double y, boolean useNoiseOffsets) {
        double d = 0.0;
        double d1 = this.highestFreqInputFactor;
        double d2 = this.highestFreqValueFactor;

        for (SimplexNoise simplexNoise : this.noiseLevels) {
            if (simplexNoise != null) {
                d += simplexNoise.getValue(x * d1 + (useNoiseOffsets ? simplexNoise.xo : 0.0), y * d1 + (useNoiseOffsets ? simplexNoise.yo : 0.0)) * d2;
            }

            d1 /= 2.0;
            d2 *= 2.0;
        }

        return d;
    }
}
