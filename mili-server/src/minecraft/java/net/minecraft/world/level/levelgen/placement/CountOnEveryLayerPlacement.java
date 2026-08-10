package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

@Deprecated
public class CountOnEveryLayerPlacement extends PlacementModifier {
    public static final MapCodec<CountOnEveryLayerPlacement> CODEC = IntProvider.codec(0, 256)
        .fieldOf("count")
        .xmap(CountOnEveryLayerPlacement::new, placement -> placement.count);
    private final IntProvider count;

    private CountOnEveryLayerPlacement(IntProvider count) {
        this.count = count;
    }

    public static CountOnEveryLayerPlacement of(IntProvider count) {
        return new CountOnEveryLayerPlacement(count);
    }

    public static CountOnEveryLayerPlacement of(int count) {
        return of(ConstantInt.of(count));
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        Builder<BlockPos> builder = Stream.builder();
        int i = 0;

        boolean flag;
        do {
            flag = false;

            for (int i1 = 0; i1 < this.count.sample(random); i1++) {
                int i2 = random.nextInt(16) + pos.getX();
                int i3 = random.nextInt(16) + pos.getZ();
                int height = context.getHeight(Heightmap.Types.MOTION_BLOCKING, i2, i3);
                int i4 = findOnGroundYPosition(context, i2, height, i3, i);
                if (i4 != Integer.MAX_VALUE) {
                    builder.add(new BlockPos(i2, i4, i3));
                    flag = true;
                }
            }

            i++;
        } while (flag);

        return builder.build();
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.COUNT_ON_EVERY_LAYER;
    }

    private static int findOnGroundYPosition(PlacementContext context, int x, int y, int z, int count) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(x, y, z);
        int i = 0;
        BlockState blockState = context.getBlockState(mutableBlockPos);

        for (int i1 = y; i1 >= context.getMinY() + 1; i1--) {
            mutableBlockPos.setY(i1 - 1);
            BlockState blockState1 = context.getBlockState(mutableBlockPos);
            if (!isEmpty(blockState1) && isEmpty(blockState) && !blockState1.is(Blocks.BEDROCK)) {
                if (i == count) {
                    return mutableBlockPos.getY() + 1;
                }

                i++;
            }

            blockState = blockState1;
        }

        return Integer.MAX_VALUE;
    }

    private static boolean isEmpty(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.LAVA);
    }
}
