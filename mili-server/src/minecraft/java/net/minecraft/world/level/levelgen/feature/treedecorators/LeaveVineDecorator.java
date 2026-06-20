package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class LeaveVineDecorator extends TreeDecorator {
    public static final MapCodec<LeaveVineDecorator> CODEC = Codec.floatRange(0.0F, 1.0F)
        .fieldOf("probability")
        .xmap(LeaveVineDecorator::new, decorator -> decorator.probability);
    private final float probability;

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.LEAVE_VINE;
    }

    public LeaveVineDecorator(float probability) {
        this.probability = probability;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        RandomSource randomSource = context.random();
        context.leaves().forEach(blockPos -> {
            if (randomSource.nextFloat() < this.probability) {
                BlockPos blockPos1 = blockPos.west();
                if (context.isAir(blockPos1)) {
                    addHangingVine(blockPos1, VineBlock.EAST, context);
                }
            }

            if (randomSource.nextFloat() < this.probability) {
                BlockPos blockPos1 = blockPos.east();
                if (context.isAir(blockPos1)) {
                    addHangingVine(blockPos1, VineBlock.WEST, context);
                }
            }

            if (randomSource.nextFloat() < this.probability) {
                BlockPos blockPos1 = blockPos.north();
                if (context.isAir(blockPos1)) {
                    addHangingVine(blockPos1, VineBlock.SOUTH, context);
                }
            }

            if (randomSource.nextFloat() < this.probability) {
                BlockPos blockPos1 = blockPos.south();
                if (context.isAir(blockPos1)) {
                    addHangingVine(blockPos1, VineBlock.NORTH, context);
                }
            }
        });
    }

    private static void addHangingVine(BlockPos pos, BooleanProperty sideProperty, TreeDecorator.Context context) {
        context.placeVine(pos, sideProperty);
        int i = 4;

        for (BlockPos var4 = pos.below(); context.isAir(var4) && i > 0; i--) {
            context.placeVine(var4, sideProperty);
            var4 = var4.below();
        }
    }
}
