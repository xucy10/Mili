package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;

public class CocoaDecorator extends TreeDecorator {
    public static final MapCodec<CocoaDecorator> CODEC = Codec.floatRange(0.0F, 1.0F)
        .fieldOf("probability")
        .xmap(CocoaDecorator::new, decorator -> decorator.probability);
    private final float probability;

    public CocoaDecorator(float probability) {
        this.probability = probability;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.COCOA;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        if (context.logs().isEmpty()) return; // Paper - Fix crash when trying to generate without logs
        RandomSource randomSource = context.random();
        if (!(randomSource.nextFloat() >= this.probability)) {
            List<BlockPos> list = context.logs();
            if (!list.isEmpty()) {
                int y = list.getFirst().getY();
                list.stream()
                    .filter(pos -> pos.getY() - y <= 2)
                    .forEach(
                        blockPos -> {
                            for (Direction direction : Direction.Plane.HORIZONTAL) {
                                if (randomSource.nextFloat() <= 0.25F) {
                                    Direction opposite = direction.getOpposite();
                                    BlockPos blockPos1 = blockPos.offset(opposite.getStepX(), 0, opposite.getStepZ());
                                    if (context.isAir(blockPos1)) {
                                        context.setBlock(
                                            blockPos1,
                                            Blocks.COCOA
                                                .defaultBlockState()
                                                .setValue(CocoaBlock.AGE, randomSource.nextInt(3))
                                                .setValue(CocoaBlock.FACING, direction)
                                        );
                                    }
                                }
                            }
                        }
                    );
            }
        }
    }
}
