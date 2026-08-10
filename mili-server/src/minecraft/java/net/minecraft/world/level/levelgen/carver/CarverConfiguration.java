package net.minecraft.world.level.levelgen.carver;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;

public class CarverConfiguration extends ProbabilityFeatureConfiguration {
    public static final MapCodec<CarverConfiguration> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter(carverConfiguration -> carverConfiguration.probability),
                HeightProvider.CODEC.fieldOf("y").forGetter(carverConfiguration -> carverConfiguration.y),
                FloatProvider.CODEC.fieldOf("yScale").forGetter(carverConfiguration -> carverConfiguration.yScale),
                VerticalAnchor.CODEC.fieldOf("lava_level").forGetter(carverConfiguration -> carverConfiguration.lavaLevel),
                CarverDebugSettings.CODEC
                    .optionalFieldOf("debug_settings", CarverDebugSettings.DEFAULT)
                    .forGetter(carverConfiguration -> carverConfiguration.debugSettings),
                RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("replaceable").forGetter(carverConfiguration -> carverConfiguration.replaceable)
            )
            .apply(instance, CarverConfiguration::new)
    );
    public final HeightProvider y;
    public final FloatProvider yScale;
    public final VerticalAnchor lavaLevel;
    public final CarverDebugSettings debugSettings;
    public final HolderSet<Block> replaceable;

    public CarverConfiguration(
        float probability, HeightProvider y, FloatProvider yScale, VerticalAnchor lavaLevel, CarverDebugSettings debugSettings, HolderSet<Block> replaceable
    ) {
        super(probability);
        this.y = y;
        this.yScale = yScale;
        this.lavaLevel = lavaLevel;
        this.debugSettings = debugSettings;
        this.replaceable = replaceable;
    }
}
