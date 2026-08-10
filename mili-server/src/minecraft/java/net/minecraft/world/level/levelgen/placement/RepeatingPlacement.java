package net.minecraft.world.level.levelgen.placement;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

public abstract class RepeatingPlacement extends PlacementModifier {
    protected abstract int count(RandomSource random, BlockPos pos);

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        return IntStream.range(0, this.count(random, pos)).mapToObj(i -> pos);
    }
}
