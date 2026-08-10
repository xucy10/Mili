package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class AttachedToLogsDecorator extends TreeDecorator {
    public static final MapCodec<AttachedToLogsDecorator> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter(attachedToLogsDecorator -> attachedToLogsDecorator.probability),
                BlockStateProvider.CODEC.fieldOf("block_provider").forGetter(attachedToLogsDecorator -> attachedToLogsDecorator.blockProvider),
                ExtraCodecs.nonEmptyList(Direction.CODEC.listOf())
                    .fieldOf("directions")
                    .forGetter(attachedToLogsDecorator -> attachedToLogsDecorator.directions)
            )
            .apply(instance, AttachedToLogsDecorator::new)
    );
    private final float probability;
    private final BlockStateProvider blockProvider;
    private final List<Direction> directions;

    public AttachedToLogsDecorator(float probability, BlockStateProvider blockProvider, List<Direction> directions) {
        this.probability = probability;
        this.blockProvider = blockProvider;
        this.directions = directions;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        RandomSource randomSource = context.random();

        for (BlockPos blockPos : Util.shuffledCopy(context.logs(), randomSource)) {
            Direction direction = Util.getRandom(this.directions, randomSource);
            BlockPos blockPos1 = blockPos.relative(direction);
            if (randomSource.nextFloat() <= this.probability && context.isAir(blockPos1)) {
                context.setBlock(blockPos1, this.blockProvider.getState(randomSource, blockPos1));
            }
        }
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.ATTACHED_TO_LOGS;
    }
}
