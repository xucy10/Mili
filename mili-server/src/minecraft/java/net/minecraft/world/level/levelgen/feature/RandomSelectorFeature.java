package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.RandomFeatureConfiguration;

public class RandomSelectorFeature extends Feature<RandomFeatureConfiguration> {
    public RandomSelectorFeature(Codec<RandomFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<RandomFeatureConfiguration> context) {
        RandomFeatureConfiguration randomFeatureConfiguration = context.config();
        RandomSource randomSource = context.random();
        WorldGenLevel worldGenLevel = context.level();
        ChunkGenerator chunkGenerator = context.chunkGenerator();
        BlockPos blockPos = context.origin();

        for (WeightedPlacedFeature weightedPlacedFeature : randomFeatureConfiguration.features) {
            if (randomSource.nextFloat() < weightedPlacedFeature.chance) {
                return weightedPlacedFeature.place(worldGenLevel, chunkGenerator, randomSource, blockPos);
            }
        }

        return randomFeatureConfiguration.defaultFeature.value().place(worldGenLevel, chunkGenerator, randomSource, blockPos);
    }
}
