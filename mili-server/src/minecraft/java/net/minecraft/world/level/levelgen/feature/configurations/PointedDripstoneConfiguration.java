package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class PointedDripstoneConfiguration implements FeatureConfiguration {
    public static final Codec<PointedDripstoneConfiguration> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Codec.floatRange(0.0F, 1.0F).fieldOf("chance_of_taller_dripstone").orElse(0.2F).forGetter(config -> config.chanceOfTallerDripstone),
                Codec.floatRange(0.0F, 1.0F).fieldOf("chance_of_directional_spread").orElse(0.7F).forGetter(config -> config.chanceOfDirectionalSpread),
                Codec.floatRange(0.0F, 1.0F).fieldOf("chance_of_spread_radius2").orElse(0.5F).forGetter(config -> config.chanceOfSpreadRadius2),
                Codec.floatRange(0.0F, 1.0F).fieldOf("chance_of_spread_radius3").orElse(0.5F).forGetter(config -> config.chanceOfSpreadRadius3)
            )
            .apply(instance, PointedDripstoneConfiguration::new)
    );
    public final float chanceOfTallerDripstone;
    public final float chanceOfDirectionalSpread;
    public final float chanceOfSpreadRadius2;
    public final float chanceOfSpreadRadius3;

    public PointedDripstoneConfiguration(
        float chanceOfTallerDripstone, float chanceOfDirectionalSpread, float chanceOfSpreadRadius2, float chanceOfSpreadRadius3
    ) {
        this.chanceOfTallerDripstone = chanceOfTallerDripstone;
        this.chanceOfDirectionalSpread = chanceOfDirectionalSpread;
        this.chanceOfSpreadRadius2 = chanceOfSpreadRadius2;
        this.chanceOfSpreadRadius3 = chanceOfSpreadRadius3;
    }
}
