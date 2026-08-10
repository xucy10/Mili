package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public record SimpleBlockConfiguration(BlockStateProvider toPlace, boolean scheduleTick) implements FeatureConfiguration {
    public static final Codec<SimpleBlockConfiguration> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                BlockStateProvider.CODEC.fieldOf("to_place").forGetter(config -> config.toPlace),
                Codec.BOOL.optionalFieldOf("schedule_tick", false).forGetter(simpleBlockConfiguration -> simpleBlockConfiguration.scheduleTick)
            )
            .apply(instance, SimpleBlockConfiguration::new)
    );

    public SimpleBlockConfiguration(BlockStateProvider toPlace) {
        this(toPlace, false);
    }
}
