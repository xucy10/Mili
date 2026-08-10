package net.minecraft.world.level.biome;

import net.minecraft.core.Holder;

public interface BiomeResolver {
    Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler);
}
