package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleRandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class SimpleRandomSelectorFeature extends Feature<SimpleRandomFeatureConfiguration> {
    public SimpleRandomSelectorFeature(Codec<SimpleRandomFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<SimpleRandomFeatureConfiguration> context) {
        RandomSource randomSource = context.random();
        SimpleRandomFeatureConfiguration simpleRandomFeatureConfiguration = context.config();
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        ChunkGenerator chunkGenerator = context.chunkGenerator();
        int randomInt = randomSource.nextInt(simpleRandomFeatureConfiguration.features.size());
        PlacedFeature placedFeature = simpleRandomFeatureConfiguration.features.get(randomInt).value();
        return placedFeature.place(worldGenLevel, chunkGenerator, randomSource, blockPos);
    }
}
