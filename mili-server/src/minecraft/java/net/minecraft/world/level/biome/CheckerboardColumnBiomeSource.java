package net.minecraft.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;

public class CheckerboardColumnBiomeSource extends BiomeSource {
    public static final MapCodec<CheckerboardColumnBiomeSource> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Biome.LIST_CODEC.fieldOf("biomes").forGetter(checker -> checker.allowedBiomes),
                Codec.intRange(0, 62).fieldOf("scale").orElse(2).forGetter(checker -> checker.size)
            )
            .apply(instance, CheckerboardColumnBiomeSource::new)
    );
    private final HolderSet<Biome> allowedBiomes;
    private final int bitShift;
    private final int size;

    public CheckerboardColumnBiomeSource(HolderSet<Biome> allowedBiomes, int size) {
        this.allowedBiomes = allowedBiomes;
        this.bitShift = size + 2;
        this.size = size;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return this.allowedBiomes.stream();
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return this.allowedBiomes.get(Math.floorMod((x >> this.bitShift) + (z >> this.bitShift), this.allowedBiomes.size()));
    }
}
