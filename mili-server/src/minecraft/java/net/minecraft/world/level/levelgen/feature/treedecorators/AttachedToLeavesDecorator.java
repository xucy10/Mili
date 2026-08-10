package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class AttachedToLeavesDecorator extends TreeDecorator {
    public static final MapCodec<AttachedToLeavesDecorator> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter(decorator -> decorator.probability),
                Codec.intRange(0, 16).fieldOf("exclusion_radius_xz").forGetter(decorator -> decorator.exclusionRadiusXZ),
                Codec.intRange(0, 16).fieldOf("exclusion_radius_y").forGetter(decorator -> decorator.exclusionRadiusY),
                BlockStateProvider.CODEC.fieldOf("block_provider").forGetter(decorator -> decorator.blockProvider),
                Codec.intRange(1, 16).fieldOf("required_empty_blocks").forGetter(decorator -> decorator.requiredEmptyBlocks),
                ExtraCodecs.nonEmptyList(Direction.CODEC.listOf()).fieldOf("directions").forGetter(decorator -> decorator.directions)
            )
            .apply(instance, AttachedToLeavesDecorator::new)
    );
    protected final float probability;
    protected final int exclusionRadiusXZ;
    protected final int exclusionRadiusY;
    protected final BlockStateProvider blockProvider;
    protected final int requiredEmptyBlocks;
    protected final List<Direction> directions;

    public AttachedToLeavesDecorator(
        float probability, int exclusionRadiusXZ, int exclusionRadiusY, BlockStateProvider blockProvider, int requiredEmptyBlocks, List<Direction> directions
    ) {
        this.probability = probability;
        this.exclusionRadiusXZ = exclusionRadiusXZ;
        this.exclusionRadiusY = exclusionRadiusY;
        this.blockProvider = blockProvider;
        this.requiredEmptyBlocks = requiredEmptyBlocks;
        this.directions = directions;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        Set<BlockPos> set = new HashSet<>();
        RandomSource randomSource = context.random();

        for (BlockPos blockPos : Util.shuffledCopy(context.leaves(), randomSource)) {
            Direction direction = Util.getRandom(this.directions, randomSource);
            BlockPos blockPos1 = blockPos.relative(direction);
            if (!set.contains(blockPos1) && randomSource.nextFloat() < this.probability && this.hasRequiredEmptyBlocks(context, blockPos, direction)) {
                BlockPos blockPos2 = blockPos1.offset(-this.exclusionRadiusXZ, -this.exclusionRadiusY, -this.exclusionRadiusXZ);
                BlockPos blockPos3 = blockPos1.offset(this.exclusionRadiusXZ, this.exclusionRadiusY, this.exclusionRadiusXZ);

                for (BlockPos blockPos4 : BlockPos.betweenClosed(blockPos2, blockPos3)) {
                    set.add(blockPos4.immutable());
                }

                context.setBlock(blockPos1, this.blockProvider.getState(randomSource, blockPos1));
            }
        }
    }

    private boolean hasRequiredEmptyBlocks(TreeDecorator.Context context, BlockPos pos, Direction direction) {
        for (int i = 1; i <= this.requiredEmptyBlocks; i++) {
            BlockPos blockPos = pos.relative(direction, i);
            if (!context.isAir(blockPos)) {
                return false;
            }
        }

        return true;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.ATTACHED_TO_LEAVES;
    }
}
