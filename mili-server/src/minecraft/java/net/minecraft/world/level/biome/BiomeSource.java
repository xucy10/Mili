package net.minecraft.world.level.biome;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import org.jspecify.annotations.Nullable;

public abstract class BiomeSource implements BiomeResolver {
    public static final Codec<BiomeSource> CODEC = BuiltInRegistries.BIOME_SOURCE.byNameCodec().dispatchStable(BiomeSource::codec, Function.identity());
    private final Supplier<Set<Holder<Biome>>> possibleBiomes = Suppliers.memoize(
        () -> this.collectPossibleBiomes().distinct().collect(ImmutableSet.toImmutableSet())
    );

    protected BiomeSource() {
    }

    protected abstract MapCodec<? extends BiomeSource> codec();

    protected abstract Stream<Holder<Biome>> collectPossibleBiomes();

    public Set<Holder<Biome>> possibleBiomes() {
        return this.possibleBiomes.get();
    }

    public Set<Holder<Biome>> getBiomesWithin(int x, int y, int z, int radius, Climate.Sampler sampler) {
        int quartPosCoord = QuartPos.fromBlock(x - radius);
        int quartPosCoord1 = QuartPos.fromBlock(y - radius);
        int quartPosCoord2 = QuartPos.fromBlock(z - radius);
        int quartPosCoord3 = QuartPos.fromBlock(x + radius);
        int quartPosCoord4 = QuartPos.fromBlock(y + radius);
        int quartPosCoord5 = QuartPos.fromBlock(z + radius);
        int i = quartPosCoord3 - quartPosCoord + 1;
        int i1 = quartPosCoord4 - quartPosCoord1 + 1;
        int i2 = quartPosCoord5 - quartPosCoord2 + 1;
        Set<Holder<Biome>> set = Sets.newHashSet();

        for (int i3 = 0; i3 < i2; i3++) {
            for (int i4 = 0; i4 < i; i4++) {
                for (int i5 = 0; i5 < i1; i5++) {
                    int i6 = quartPosCoord + i4;
                    int i7 = quartPosCoord1 + i5;
                    int i8 = quartPosCoord2 + i3;
                    set.add(this.getNoiseBiome(i6, i7, i8, sampler));
                }
            }
        }

        return set;
    }

    public @Nullable Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
        int x, int y, int z, int radius, Predicate<Holder<Biome>> biomePredicate, RandomSource random, Climate.Sampler sampler
    ) {
        return this.findBiomeHorizontal(x, y, z, radius, 1, biomePredicate, random, false, sampler);
    }

    public @Nullable Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
        BlockPos pos, int radius, int horizontalStep, int verticalStep, Predicate<Holder<Biome>> biomePredicate, Climate.Sampler sampler, LevelReader level
    ) {
        Set<Holder<Biome>> set = this.possibleBiomes().stream().filter(biomePredicate).collect(Collectors.toUnmodifiableSet());
        if (set.isEmpty()) {
            return null;
        } else {
            int i = Math.floorDiv(radius, horizontalStep);
            int[] ints = Mth.outFromOrigin(pos.getY(), level.getMinY() + 1, level.getMaxY() + 1, verticalStep).toArray();

            for (BlockPos.MutableBlockPos mutableBlockPos : BlockPos.spiralAround(BlockPos.ZERO, i, Direction.EAST, Direction.SOUTH)) {
                int i1 = pos.getX() + mutableBlockPos.getX() * horizontalStep;
                int i2 = pos.getZ() + mutableBlockPos.getZ() * horizontalStep;
                int quartPosCoord = QuartPos.fromBlock(i1);
                int quartPosCoord1 = QuartPos.fromBlock(i2);

                for (int i3 : ints) {
                    int quartPosCoord2 = QuartPos.fromBlock(i3);
                    Holder<Biome> noiseBiome = this.getNoiseBiome(quartPosCoord, quartPosCoord2, quartPosCoord1, sampler);
                    if (set.contains(noiseBiome)) {
                        return Pair.of(new BlockPos(i1, i3, i2), noiseBiome);
                    }
                }
            }

            return null;
        }
    }

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
        int quartPosX = QuartPos.fromBlock(x);
        int quartPosZ = QuartPos.fromBlock(z);
        int quartPosCoord = QuartPos.fromBlock(radius);
        int quartPosY = QuartPos.fromBlock(y);
        Pair<BlockPos, Holder<Biome>> pair = null;
        int i = 0;
        int i1 = findClosest ? 0 : quartPosCoord;
        int i2 = i1;

        while (i2 <= quartPosCoord) {
            for (int i3 = !SharedConstants.DEBUG_ONLY_GENERATE_HALF_THE_WORLD && !SharedConstants.debugGenerateSquareTerrainWithoutNoise ? -i2 : 0;
                i3 <= i2;
                i3 += increment
            ) {
                boolean flag = Math.abs(i3) == i2;

                for (int i4 = -i2; i4 <= i2; i4 += increment) {
                    if (findClosest) {
                        boolean flag1 = Math.abs(i4) == i2;
                        if (!flag1 && !flag) {
                            continue;
                        }
                    }

                    int i5 = quartPosX + i4;
                    int i6 = quartPosZ + i3;
                    Holder<Biome> noiseBiome = this.getNoiseBiome(i5, quartPosY, i6, sampler);
                    if (biomePredicate.test(noiseBiome)) {
                        if (pair == null || random.nextInt(i + 1) == 0) {
                            BlockPos blockPos = new BlockPos(QuartPos.toBlock(i5), y, QuartPos.toBlock(i6));
                            if (findClosest) {
                                return Pair.of(blockPos, noiseBiome);
                            }

                            pair = Pair.of(blockPos, noiseBiome);
                        }

                        i++;
                    }
                }
            }

            i2 += increment;
        }

        return pair;
    }

    @Override
    public abstract Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler);

    public void addDebugInfo(List<String> info, BlockPos pos, Climate.Sampler sampler) {
    }
}
