package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class NetherForestVegetationConfig extends BlockPileConfiguration {
    public static final Codec<NetherForestVegetationConfig> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                BlockStateProvider.CODEC.fieldOf("state_provider").forGetter(config -> config.stateProvider),
                ExtraCodecs.POSITIVE_INT.fieldOf("spread_width").forGetter(config -> config.spreadWidth),
                ExtraCodecs.POSITIVE_INT.fieldOf("spread_height").forGetter(config -> config.spreadHeight)
            )
            .apply(instance, NetherForestVegetationConfig::new)
    );
    public final int spreadWidth;
    public final int spreadHeight;

    public NetherForestVegetationConfig(BlockStateProvider stateProvider, int spreadWidth, int spreadHeight) {
        super(stateProvider);
        this.spreadWidth = spreadWidth;
        this.spreadHeight = spreadHeight;
    }
}
