package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

public class InSquarePlacement extends PlacementModifier {
    private static final InSquarePlacement INSTANCE = new InSquarePlacement();
    public static final MapCodec<InSquarePlacement> CODEC = MapCodec.unit(() -> INSTANCE);

    public static InSquarePlacement spread() {
        return INSTANCE;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int i = random.nextInt(16) + pos.getX();
        int i1 = random.nextInt(16) + pos.getZ();
        return Stream.of(new BlockPos(i, pos.getY(), i1));
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.IN_SQUARE;
    }
}
