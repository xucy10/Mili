package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.ThreadingDetector;

public class LegacyRandomSource implements BitRandomSource {
    private static final int MODULUS_BITS = 48;
    private static final long MODULUS_MASK = 281474976710655L;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;
    private final AtomicLong seed = new AtomicLong();
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public LegacyRandomSource(long seed) {
        this.setSeed(seed);
    }

    @Override
    public RandomSource fork() {
        return new LegacyRandomSource(this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new LegacyRandomSource.LegacyPositionalRandomFactory(this.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        if (!this.seed.compareAndSet(this.seed.get(), (seed ^ 25214903917L) & 281474976710655L)) {
            throw ThreadingDetector.makeThreadingException("LegacyRandomSource", null);
        } else {
            this.gaussianSource.reset();
        }
    }

    @Override
    public int next(int size) {
        long l = this.seed.get();
        long l1 = l * 25214903917L + 11L & 281474976710655L;
        if (!this.seed.compareAndSet(l, l1)) {
            throw ThreadingDetector.makeThreadingException("LegacyRandomSource", null);
        } else {
            return (int)(l1 >> 48 - size);
        }
    }

    @Override
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }

    public static class LegacyPositionalRandomFactory implements PositionalRandomFactory {
        private final long seed;

        public LegacyPositionalRandomFactory(long seed) {
            this.seed = seed;
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            long seed = Mth.getSeed(x, y, z);
            long l = seed ^ this.seed;
            return new LegacyRandomSource(l);
        }

        @Override
        public RandomSource fromHashOf(String name) {
            int hashCode = name.hashCode();
            return new LegacyRandomSource(hashCode ^ this.seed);
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new LegacyRandomSource(seed);
        }

        @VisibleForTesting
        @Override
        public void parityConfigString(StringBuilder builder) {
            builder.append("LegacyPositionalRandomFactory{").append(this.seed).append("}");
        }
    }
}
