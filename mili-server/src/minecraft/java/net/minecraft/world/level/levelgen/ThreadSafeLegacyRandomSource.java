package net.minecraft.world.level.levelgen;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.RandomSource;

@Deprecated
public class ThreadSafeLegacyRandomSource implements BitRandomSource {
    private static final int MODULUS_BITS = 48;
    private static final long MODULUS_MASK = 281474976710655L;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;
    private final AtomicLong seed = new AtomicLong();
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public ThreadSafeLegacyRandomSource(long seed) {
        this.setSeed(seed);
    }

    @Override
    public RandomSource fork() {
        return new ThreadSafeLegacyRandomSource(this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new LegacyRandomSource.LegacyPositionalRandomFactory(this.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.seed.set((seed ^ 25214903917L) & 281474976710655L);
    }

    @Override
    public int next(int size) {
        long l;
        long l1;
        do {
            l = this.seed.get();
            l1 = l * 25214903917L + 11L & 281474976710655L;
        } while (!this.seed.compareAndSet(l, l1));

        return (int)(l1 >>> 48 - size);
    }

    @Override
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }
}
