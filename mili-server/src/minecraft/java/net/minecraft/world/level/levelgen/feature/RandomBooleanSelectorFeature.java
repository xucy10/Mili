package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.RandomBooleanFeatureConfiguration;

public class RandomBooleanSelectorFeature extends Feature<RandomBooleanFeatureConfiguration> {
    public RandomBooleanSelectorFeature(Codec<RandomBooleanFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<RandomBooleanFeatureConfiguration> context) {
        RandomSource randomSource = context.random();
        RandomBooleanFeatureConfiguration randomBooleanFeatureConfiguration = context.config();
        WorldGenLevel worldGenLevel = context.level();
        ChunkGenerator chunkGenerator = context.chunkGenerator();
        BlockPos blockPos = context.origin();
        boolean randomBoolean = randomSource.nextBoolean();
        return (randomBoolean ? randomBooleanFeatureConfiguration.featureTrue : randomBooleanFeatureConfiguration.featureFalse)
            .value()
            .place(worldGenLevel, chunkGenerator, randomSource, blockPos);
    }
}
