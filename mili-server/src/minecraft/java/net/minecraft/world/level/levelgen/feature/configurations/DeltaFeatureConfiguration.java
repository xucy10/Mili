package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.state.BlockState;

public class DeltaFeatureConfiguration implements FeatureConfiguration {
    public static final Codec<DeltaFeatureConfiguration> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                BlockState.CODEC.fieldOf("contents").forGetter(config -> config.contents),
                BlockState.CODEC.fieldOf("rim").forGetter(config -> config.rim),
                IntProvider.codec(0, 16).fieldOf("size").forGetter(config -> config.size),
                IntProvider.codec(0, 16).fieldOf("rim_size").forGetter(config -> config.rimSize)
            )
            .apply(instance, DeltaFeatureConfiguration::new)
    );
    private final BlockState contents;
    private final BlockState rim;
    private final IntProvider size;
    private final IntProvider rimSize;

    public DeltaFeatureConfiguration(BlockState contents, BlockState rim, IntProvider size, IntProvider rimSize) {
        this.contents = contents;
        this.rim = rim;
        this.size = size;
        this.rimSize = rimSize;
    }

    public BlockState contents() {
        return this.contents;
    }

    public BlockState rim() {
        return this.rim;
    }

    public IntProvider size() {
        return this.size;
    }

    public IntProvider rimSize() {
        return this.rimSize;
    }
}
