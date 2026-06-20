package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;

public class RandomOffsetPlacement extends PlacementModifier {
    public static final MapCodec<RandomOffsetPlacement> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                IntProvider.codec(-16, 16).fieldOf("xz_spread").forGetter(placement -> placement.xzSpread),
                IntProvider.codec(-16, 16).fieldOf("y_spread").forGetter(placement -> placement.ySpread)
            )
            .apply(instance, RandomOffsetPlacement::new)
    );
    private final IntProvider xzSpread;
    private final IntProvider ySpread;

    public static RandomOffsetPlacement of(IntProvider xzSpread, IntProvider ySpread) {
        return new RandomOffsetPlacement(xzSpread, ySpread);
    }

    public static RandomOffsetPlacement vertical(IntProvider ySpread) {
        return new RandomOffsetPlacement(ConstantInt.of(0), ySpread);
    }

    public static RandomOffsetPlacement horizontal(IntProvider xzSpread) {
        return new RandomOffsetPlacement(xzSpread, ConstantInt.of(0));
    }

    private RandomOffsetPlacement(IntProvider xzSpread, IntProvider ySpread) {
        this.xzSpread = xzSpread;
        this.ySpread = ySpread;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int i = pos.getX() + this.xzSpread.sample(random);
        int i1 = pos.getY() + this.ySpread.sample(random);
        int i2 = pos.getZ() + this.xzSpread.sample(random);
        return Stream.of(new BlockPos(i, i1, i2));
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.RANDOM_OFFSET;
    }
}
