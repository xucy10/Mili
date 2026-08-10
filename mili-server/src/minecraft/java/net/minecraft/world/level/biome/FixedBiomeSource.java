package net.minecraft.world.level.biome;

import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import org.jspecify.annotations.Nullable;

public class FixedBiomeSource extends BiomeSource implements BiomeManager.NoiseBiomeSource {
    public static final MapCodec<FixedBiomeSource> CODEC = Biome.CODEC.fieldOf("biome").xmap(FixedBiomeSource::new, fixed -> fixed.biome).stable();
    private final Holder<Biome> biome;

    public FixedBiomeSource(Holder<Biome> biome) {
        this.biome = biome;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(this.biome);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return this.biome;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        return this.biome;
    }

    @Override
    public @Nullable Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
        int x,
        int y,
        int z,
        int radius,
        int increment,
        Predicate<Holder<Biome>> biomePredicate,
        RandomSource random,
        boolean findClosest,
        Climate.Sampler sampler
    ) {
        if (biomePredicate.test(this.biome)) {
            return findClosest
                ? Pair.of(new BlockPos(x, y, z), this.biome)
                : Pair.of(new BlockPos(x - radius + random.nextInt(radius * 2 + 1), y, z - radius + random.nextInt(radius * 2 + 1)), this.biome);
        } else {
            return null;
        }
    }

    @Override
    public @Nullable Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
        BlockPos pos, int radius, int horizontalStep, int verticalStep, Predicate<Holder<Biome>> biomePredicate, Climate.Sampler sampler, LevelReader level
    ) {
        return biomePredicate.test(this.biome) ? Pair.of(pos.atY(Mth.clamp(pos.getY(), level.getMinY() + 1, level.getMaxY() + 1)), this.biome) : null;
    }

    @Override
    public Set<Holder<Biome>> getBiomesWithin(int x, int y, int z, int radius, Climate.Sampler sampler) {
        return Sets.newHashSet(Set.of(this.biome));
    }
}
