package net.minecraft.world.level.levelgen.carver;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;

public class CaveCarverConfiguration extends CarverConfiguration {
    public static final Codec<CaveCarverConfiguration> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                CarverConfiguration.CODEC.forGetter(caveCarverConfiguration -> caveCarverConfiguration),
                FloatProvider.CODEC
                    .fieldOf("horizontal_radius_multiplier")
                    .forGetter(caveCarverConfiguration -> caveCarverConfiguration.horizontalRadiusMultiplier),
                FloatProvider.CODEC
                    .fieldOf("vertical_radius_multiplier")
                    .forGetter(caveCarverConfiguration -> caveCarverConfiguration.verticalRadiusMultiplier),
                FloatProvider.codec(-1.0F, 1.0F).fieldOf("floor_level").forGetter(caveCarverConfiguration -> caveCarverConfiguration.floorLevel)
            )
            .apply(instance, CaveCarverConfiguration::new)
    );
    public final FloatProvider horizontalRadiusMultiplier;
    public final FloatProvider verticalRadiusMultiplier;
    final FloatProvider floorLevel;

    public CaveCarverConfiguration(
        float probability,
        HeightProvider y,
        FloatProvider yScale,
        VerticalAnchor lavaLevel,
        CarverDebugSettings debugSettings,
        HolderSet<Block> replaceable,
        FloatProvider horizontalRadiusMultiplier,
        FloatProvider verticalRadiusMultiplier,
        FloatProvider floorLevel
    ) {
        super(probability, y, yScale, lavaLevel, debugSettings, replaceable);
        this.horizontalRadiusMultiplier = horizontalRadiusMultiplier;
        this.verticalRadiusMultiplier = verticalRadiusMultiplier;
        this.floorLevel = floorLevel;
    }

    public CaveCarverConfiguration(
        float probability,
        HeightProvider y,
        FloatProvider yScale,
        VerticalAnchor lavaLevel,
        HolderSet<Block> replaceable,
        FloatProvider horizontalRadiusMultiplier,
        FloatProvider verticalRadiusMultiplier,
        FloatProvider floorLevel
    ) {
        this(probability, y, yScale, lavaLevel, CarverDebugSettings.DEFAULT, replaceable, horizontalRadiusMultiplier, verticalRadiusMultiplier, floorLevel);
    }

    public CaveCarverConfiguration(
        CarverConfiguration config, FloatProvider horizontalRadiusMultiplier, FloatProvider verticalRadiusMultiplier, FloatProvider floorLevel
    ) {
        this(
            config.probability,
            config.y,
            config.yScale,
            config.lavaLevel,
            config.debugSettings,
            config.replaceable,
            horizontalRadiusMultiplier,
            verticalRadiusMultiplier,
            floorLevel
        );
    }
}
