package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CreakingHeartBlock;
import net.minecraft.world.level.block.state.properties.CreakingHeartState;

public class CreakingHeartDecorator extends TreeDecorator {
    public static final MapCodec<CreakingHeartDecorator> CODEC = Codec.floatRange(0.0F, 1.0F)
        .fieldOf("probability")
        .xmap(CreakingHeartDecorator::new, creakingHeartDecorator -> creakingHeartDecorator.probability);
    private final float probability;

    public CreakingHeartDecorator(float probability) {
        this.probability = probability;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.CREAKING_HEART;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        RandomSource randomSource = context.random();
        List<BlockPos> list = context.logs();
        if (!list.isEmpty()) {
            if (!(randomSource.nextFloat() >= this.probability)) {
                List<BlockPos> list1 = new ArrayList<>(list);
                Util.shuffle(list1, randomSource);
                Optional<BlockPos> optional = list1.stream().filter(pos -> {
                    for (Direction direction : Direction.values()) {
                        if (!context.checkBlock(pos.relative(direction), blockState -> blockState.is(BlockTags.LOGS))) {
                            return false;
                        }
                    }

                    return true;
                }).findFirst();
                if (!optional.isEmpty()) {
                    context.setBlock(
                        optional.get(),
                        Blocks.CREAKING_HEART
                            .defaultBlockState()
                            .setValue(CreakingHeartBlock.STATE, CreakingHeartState.DORMANT)
                            .setValue(CreakingHeartBlock.NATURAL, true)
                    );
                }
            }
        }
    }
}
