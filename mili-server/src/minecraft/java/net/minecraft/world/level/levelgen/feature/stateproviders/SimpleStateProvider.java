package net.minecraft.world.level.levelgen.feature.stateproviders;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public class SimpleStateProvider extends BlockStateProvider {
    public static final MapCodec<SimpleStateProvider> CODEC = BlockState.CODEC.fieldOf("state").xmap(SimpleStateProvider::new, provider -> provider.state);
    private final BlockState state;

    protected SimpleStateProvider(BlockState state) {
        this.state = state;
    }

    @Override
    protected BlockStateProviderType<?> type() {
        return BlockStateProviderType.SIMPLE_STATE_PROVIDER;
    }

    @Override
    public BlockState getState(RandomSource random, BlockPos pos) {
        return this.state;
    }
}
