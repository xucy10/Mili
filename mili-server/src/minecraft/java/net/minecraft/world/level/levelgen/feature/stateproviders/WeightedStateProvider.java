package net.minecraft.world.level.levelgen.feature.stateproviders;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.state.BlockState;

public class WeightedStateProvider extends BlockStateProvider {
    public static final MapCodec<WeightedStateProvider> CODEC = WeightedList.nonEmptyCodec(BlockState.CODEC)
        .comapFlatMap(WeightedStateProvider::create, provider -> provider.weightedList)
        .fieldOf("entries");
    private final WeightedList<BlockState> weightedList;

    private static DataResult<WeightedStateProvider> create(WeightedList<BlockState> weightedList) {
        return weightedList.isEmpty()
            ? DataResult.error(() -> "WeightedStateProvider with no states")
            : DataResult.success(new WeightedStateProvider(weightedList));
    }

    public WeightedStateProvider(WeightedList<BlockState> weightedList) {
        this.weightedList = weightedList;
    }

    public WeightedStateProvider(WeightedList.Builder<BlockState> weightedList) {
        this(weightedList.build());
    }

    @Override
    protected BlockStateProviderType<?> type() {
        return BlockStateProviderType.WEIGHTED_STATE_PROVIDER;
    }

    @Override
    public BlockState getState(RandomSource random, BlockPos pos) {
        return this.weightedList.getRandomOrThrow(random);
    }
}
