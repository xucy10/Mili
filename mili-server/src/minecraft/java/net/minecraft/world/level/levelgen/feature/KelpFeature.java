package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class KelpFeature extends Feature<NoneFeatureConfiguration> {
    public KelpFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        int i = 0;
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        RandomSource randomSource = context.random();
        int height = worldGenLevel.getHeight(Heightmap.Types.OCEAN_FLOOR, blockPos.getX(), blockPos.getZ());
        BlockPos blockPos1 = new BlockPos(blockPos.getX(), height, blockPos.getZ());
        if (worldGenLevel.getBlockState(blockPos1).is(Blocks.WATER)) {
            BlockState blockState = Blocks.KELP.defaultBlockState();
            BlockState blockState1 = Blocks.KELP_PLANT.defaultBlockState();
            int i1 = 1 + randomSource.nextInt(10);

            for (int i2 = 0; i2 <= i1; i2++) {
                if (worldGenLevel.getBlockState(blockPos1).is(Blocks.WATER)
                    && worldGenLevel.getBlockState(blockPos1.above()).is(Blocks.WATER)
                    && blockState1.canSurvive(worldGenLevel, blockPos1)) {
                    if (i2 == i1) {
                        worldGenLevel.setBlock(blockPos1, blockState.setValue(KelpBlock.AGE, randomSource.nextInt(4) + 20), Block.UPDATE_CLIENTS);
                        i++;
                    } else {
                        worldGenLevel.setBlock(blockPos1, blockState1, Block.UPDATE_CLIENTS);
                    }
                } else if (i2 > 0) {
                    BlockPos blockPos2 = blockPos1.below();
                    if (blockState.canSurvive(worldGenLevel, blockPos2) && !worldGenLevel.getBlockState(blockPos2.below()).is(Blocks.KELP)) {
                        worldGenLevel.setBlock(blockPos2, blockState.setValue(KelpBlock.AGE, randomSource.nextInt(4) + 20), Block.UPDATE_CLIENTS);
                        i++;
                    }
                    break;
                }

                blockPos1 = blockPos1.above();
            }
        }

        return i > 0;
    }
}
