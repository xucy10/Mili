package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;

public class BlockBlobFeature extends Feature<BlockStateConfiguration> {
    public BlockBlobFeature(Codec<BlockStateConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<BlockStateConfiguration> context) {
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();

        BlockStateConfiguration blockStateConfiguration;
        for (blockStateConfiguration = context.config(); blockPos.getY() > worldGenLevel.getMinY() + 3; blockPos = blockPos.below()) {
            if (!worldGenLevel.isEmptyBlock(blockPos.below())) {
                BlockState blockState = worldGenLevel.getBlockState(blockPos.below());
                if (isDirt(blockState) || isStone(blockState)) {
                    break;
                }
            }
        }

        if (blockPos.getY() <= worldGenLevel.getMinY() + 3) {
            return false;
        } else {
            for (int i = 0; i < 3; i++) {
                int randomInt = randomSource.nextInt(2);
                int randomInt1 = randomSource.nextInt(2);
                int randomInt2 = randomSource.nextInt(2);
                float f = (randomInt + randomInt1 + randomInt2) * 0.333F + 0.5F;

                for (BlockPos blockPos1 : BlockPos.betweenClosed(
                    blockPos.offset(-randomInt, -randomInt1, -randomInt2), blockPos.offset(randomInt, randomInt1, randomInt2)
                )) {
                    if (blockPos1.distSqr(blockPos) <= f * f) {
                        worldGenLevel.setBlock(blockPos1, blockStateConfiguration.state, Block.UPDATE_ALL);
                    }
                }

                blockPos = blockPos.offset(-1 + randomSource.nextInt(2), -randomSource.nextInt(2), -1 + randomSource.nextInt(2));
            }

            return true;
        }
    }
}
