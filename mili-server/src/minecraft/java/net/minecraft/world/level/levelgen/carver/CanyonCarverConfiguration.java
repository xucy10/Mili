package net.minecraft.world.level.levelgen.carver;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;

public class CanyonCarverConfiguration extends CarverConfiguration {
    public static final Codec<CanyonCarverConfiguration> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                CarverConfiguration.CODEC.forGetter(canyonCarverConfiguration -> canyonCarverConfiguration),
                FloatProvider.CODEC.fieldOf("vertical_rotation").forGetter(canyonCarverConfiguration -> canyonCarverConfiguration.verticalRotation),
                CanyonCarverConfiguration.CanyonShapeConfiguration.CODEC
                    .fieldOf("shape")
                    .forGetter(canyonCarverConfiguration -> canyonCarverConfiguration.shape)
            )
            .apply(instance, CanyonCarverConfiguration::new)
    );
    public final FloatProvider verticalRotation;
    public final CanyonCarverConfiguration.CanyonShapeConfiguration shape;

    public CanyonCarverConfiguration(
        float probability,
        HeightProvider y,
        FloatProvider yScale,
        VerticalAnchor lavaLevel,
        CarverDebugSettings debugSettings,
        HolderSet<Block> replaceable,
        FloatProvider verticalRotation,
        CanyonCarverConfiguration.CanyonShapeConfiguration shape
    ) {
        super(probability, y, yScale, lavaLevel, debugSettings, replaceable);
        this.verticalRotation = verticalRotation;
        this.shape = shape;
    }

    public CanyonCarverConfiguration(CarverConfiguration config, FloatProvider verticalRotation, CanyonCarverConfiguration.CanyonShapeConfiguration shape) {
        this(config.probability, config.y, config.yScale, config.lavaLevel, config.debugSettings, config.replaceable, verticalRotation, shape);
    }

    public static class CanyonShapeConfiguration {
        public static final Codec<CanyonCarverConfiguration.CanyonShapeConfiguration> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    FloatProvider.CODEC.fieldOf("distance_factor").forGetter(canyonShapeConfiguration -> canyonShapeConfiguration.distanceFactor),
                    FloatProvider.CODEC.fieldOf("thickness").forGetter(canyonShapeConfiguration -> canyonShapeConfiguration.thickness),
                    ExtraCodecs.POSITIVE_INT.fieldOf("width_smoothness").forGetter(canyonShapeConfiguration -> canyonShapeConfiguration.widthSmoothness),
                    FloatProvider.CODEC
                        .fieldOf("horizontal_radius_factor")
                        .forGetter(canyonShapeConfiguration -> canyonShapeConfiguration.horizontalRadiusFactor),
                    Codec.FLOAT
                        .fieldOf("vertical_radius_default_factor")
                        .forGetter(canyonShapeConfiguration -> canyonShapeConfiguration.verticalRadiusDefaultFactor),
                    Codec.FLOAT
                        .fieldOf("vertical_radius_center_factor")
                        .forGetter(canyonShapeConfiguration -> canyonShapeConfiguration.verticalRadiusCenterFactor)
                )
                .apply(instance, CanyonCarverConfiguration.CanyonShapeConfiguration::new)
        );
        public final FloatProvider distanceFactor;
        public final FloatProvider thickness;
        public final int widthSmoothness;
        public final FloatProvider horizontalRadiusFactor;
        public final float verticalRadiusDefaultFactor;
        public final float verticalRadiusCenterFactor;

        public CanyonShapeConfiguration(
            FloatProvider distanceFactor,
            FloatProvider thickness,
            int widthSmoothness,
            FloatProvider horizontalRadiusFactor,
            float verticalRadiusDefaultFactor,
            float verticalRadiusCenterFactor
        ) {
            this.widthSmoothness = widthSmoothness;
            this.horizontalRadiusFactor = horizontalRadiusFactor;
            this.verticalRadiusDefaultFactor = verticalRadiusDefaultFactor;
            this.verticalRadiusCenterFactor = verticalRadiusCenterFactor;
            this.distanceFactor = distanceFactor;
            this.thickness = thickness;
        }
    }
}
