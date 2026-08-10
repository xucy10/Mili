package net.minecraft.world.level.levelgen.placement;

import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

public abstract class PlacementFilter extends PlacementModifier {
    @Override
    public final Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        return this.shouldPlace(context, random, pos) ? Stream.of(pos) : Stream.of();
    }

    protected abstract boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos);
}
