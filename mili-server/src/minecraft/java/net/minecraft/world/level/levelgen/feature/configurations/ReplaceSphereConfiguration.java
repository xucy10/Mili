package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.state.BlockState;

public class ReplaceSphereConfiguration implements FeatureConfiguration {
    public static final Codec<ReplaceSphereConfiguration> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                BlockState.CODEC.fieldOf("target").forGetter(config -> config.targetState),
                BlockState.CODEC.fieldOf("state").forGetter(config -> config.replaceState),
                IntProvider.codec(0, 12).fieldOf("radius").forGetter(config -> config.radius)
            )
            .apply(instance, ReplaceSphereConfiguration::new)
    );
    public final BlockState targetState;
    public final BlockState replaceState;
    private final IntProvider radius;

    public ReplaceSphereConfiguration(BlockState targetState, BlockState replaceState, IntProvider radius) {
        this.targetState = targetState;
        this.replaceState = replaceState;
        this.radius = radius;
    }

    public IntProvider radius() {
        return this.radius;
    }
}
