package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.block.state.BlockState;

public class BlockStateConfiguration implements FeatureConfiguration {
    public static final Codec<BlockStateConfiguration> CODEC = BlockState.CODEC
        .fieldOf("state")
        .xmap(BlockStateConfiguration::new, config -> config.state)
        .codec();
    public final BlockState state;

    public BlockStateConfiguration(BlockState state) {
        this.state = state;
    }
}
