package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;

public class RarityFilter extends PlacementFilter {
    public static final MapCodec<RarityFilter> CODEC = ExtraCodecs.POSITIVE_INT.fieldOf("chance").xmap(RarityFilter::new, filter -> filter.chance);
    private final int chance;

    private RarityFilter(int chance) {
        this.chance = chance;
    }

    public static RarityFilter onAverageOnceEvery(int chance) {
        return new RarityFilter(chance);
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        return random.nextFloat() < 1.0F / this.chance;
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.RARITY_FILTER;
    }
}
