package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.VineBlock;

public class TrunkVineDecorator extends TreeDecorator {
    public static final MapCodec<TrunkVineDecorator> CODEC = MapCodec.unit(() -> TrunkVineDecorator.INSTANCE);
    public static final TrunkVineDecorator INSTANCE = new TrunkVineDecorator();

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.TRUNK_VINE;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        RandomSource randomSource = context.random();
        context.logs().forEach(blockPos -> {
            if (randomSource.nextInt(3) > 0) {
                BlockPos blockPos1 = blockPos.west();
                if (context.isAir(blockPos1)) {
                    context.placeVine(blockPos1, VineBlock.EAST);
                }
            }

            if (randomSource.nextInt(3) > 0) {
                BlockPos blockPos1 = blockPos.east();
                if (context.isAir(blockPos1)) {
                    context.placeVine(blockPos1, VineBlock.WEST);
                }
            }

            if (randomSource.nextInt(3) > 0) {
                BlockPos blockPos1 = blockPos.north();
                if (context.isAir(blockPos1)) {
                    context.placeVine(blockPos1, VineBlock.SOUTH);
                }
            }

            if (randomSource.nextInt(3) > 0) {
                BlockPos blockPos1 = blockPos.south();
                if (context.isAir(blockPos1)) {
                    context.placeVine(blockPos1, VineBlock.NORTH);
                }
            }
        });
    }
}
