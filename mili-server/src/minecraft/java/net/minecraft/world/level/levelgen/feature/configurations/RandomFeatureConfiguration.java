package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.WeightedPlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class RandomFeatureConfiguration implements FeatureConfiguration {
    public static final Codec<RandomFeatureConfiguration> CODEC = RecordCodecBuilder.create(
        instance -> instance.apply2(
            RandomFeatureConfiguration::new,
            WeightedPlacedFeature.CODEC.listOf().fieldOf("features").forGetter(config -> config.features),
            PlacedFeature.CODEC.fieldOf("default").forGetter(config -> config.defaultFeature)
        )
    );
    public final List<WeightedPlacedFeature> features;
    public final Holder<PlacedFeature> defaultFeature;

    public RandomFeatureConfiguration(List<WeightedPlacedFeature> features, Holder<PlacedFeature> defaultFeature) {
        this.features = features;
        this.defaultFeature = defaultFeature;
    }

    @Override
    public Stream<ConfiguredFeature<?, ?>> getFeatures() {
        return Stream.concat(
            this.features.stream().flatMap(weightedPlacedFeature -> weightedPlacedFeature.feature.value().getFeatures()),
            this.defaultFeature.value().getFeatures()
        );
    }
}
