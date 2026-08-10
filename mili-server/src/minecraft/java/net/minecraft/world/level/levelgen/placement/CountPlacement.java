package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;

public class CountPlacement extends RepeatingPlacement {
    public static final MapCodec<CountPlacement> CODEC = IntProvider.codec(0, 256).fieldOf("count").xmap(CountPlacement::new, placement -> placement.count);
    private final IntProvider count;

    private CountPlacement(IntProvider count) {
        this.count = count;
    }

    public static CountPlacement of(IntProvider count) {
        return new CountPlacement(count);
    }

    public static CountPlacement of(int count) {
        return of(ConstantInt.of(count));
    }

    @Override
    protected int count(RandomSource random, BlockPos pos) {
        return this.count.sample(random);
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.COUNT;
    }
}
