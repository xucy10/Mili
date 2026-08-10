package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.IntProvider;

public class ColumnFeatureConfiguration implements FeatureConfiguration {
    public static final Codec<ColumnFeatureConfiguration> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                IntProvider.codec(0, 3).fieldOf("reach").forGetter(config -> config.reach),
                IntProvider.codec(1, 10).fieldOf("height").forGetter(config -> config.height)
            )
            .apply(instance, ColumnFeatureConfiguration::new)
    );
    private final IntProvider reach;
    private final IntProvider height;

    public ColumnFeatureConfiguration(IntProvider reach, IntProvider height) {
        this.reach = reach;
        this.height = height;
    }

    public IntProvider reach() {
        return this.reach;
    }

    public IntProvider height() {
        return this.height;
    }
}
