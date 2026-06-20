package net.minecraft.world.level.levelgen;

import java.util.function.LongFunction;
import net.minecraft.util.RandomSource;

public class WorldgenRandom extends LegacyRandomSource {
    private final RandomSource randomSource;
    private int count;

    public WorldgenRandom(RandomSource randomSource) {
        super(0L);
        this.randomSource = randomSource;
    }

    public int getCount() {
        return this.count;
    }

    @Override
    public RandomSource fork() {
        return this.randomSource.fork();
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return this.randomSource.forkPositional();
    }

    @Override
    public int next(int bits) {
        this.count++;
        return this.randomSource instanceof LegacyRandomSource legacyRandomSource
            ? legacyRandomSource.next(bits)
            : (int)(this.randomSource.nextLong() >>> 64 - bits);
    }

    @Override
    public synchronized void setSeed(long seed) {
        if (this.randomSource != null) {
            this.randomSource.setSeed(seed);
        }
    }

    public long setDecorationSeed(long levelSeed, int minChunkBlockX, int minChunkBlockZ) {
        this.setSeed(levelSeed);
        long l = this.nextLong() | 1L;
        long l1 = this.nextLong() | 1L;
        long l2 = minChunkBlockX * l + minChunkBlockZ * l1 ^ levelSeed;
        this.setSeed(l2);
        return l2;
    }

    public void setFeatureSeed(long decorationSeed, int index, int decorationStep) {
        long l = decorationSeed + index + 10000 * decorationStep;
        this.setSeed(l);
    }

    public void setLargeFeatureSeed(long baseSeed, int chunkX, int chunkZ) {
        this.setSeed(baseSeed);
        long randomLong = this.nextLong();
        long randomLong1 = this.nextLong();
        long l = chunkX * randomLong ^ chunkZ * randomLong1 ^ baseSeed;
        this.setSeed(l);
    }

    public void setLargeFeatureWithSalt(long levelSeed, int regionX, int regionZ, int salt) {
        long l = regionX * 341873128712L + regionZ * 132897987541L + levelSeed + salt;
        this.setSeed(l);
    }

    public static RandomSource seedSlimeChunk(int chunkX, int chunkZ, long levelSeed, long salt) {
        return RandomSource.create(levelSeed + chunkX * chunkX * 4987142 + chunkX * 5947611 + chunkZ * chunkZ * 4392871L + chunkZ * 389711 ^ salt);
    }

    public static enum Algorithm {
        LEGACY(LegacyRandomSource::new),
        XOROSHIRO(XoroshiroRandomSource::new);

        private final LongFunction<RandomSource> constructor;

        private Algorithm(final LongFunction<RandomSource> constructor) {
            this.constructor = constructor;
        }

        public RandomSource newInstance(long seed) {
            return this.constructor.apply(seed);
        }
    }
}
