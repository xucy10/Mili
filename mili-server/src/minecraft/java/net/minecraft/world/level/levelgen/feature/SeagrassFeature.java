package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration;

public class SeagrassFeature extends Feature<ProbabilityFeatureConfiguration> {
    public SeagrassFeature(Codec<ProbabilityFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<ProbabilityFeatureConfiguration> context) {
        boolean flag = false;
        RandomSource randomSource = context.random();
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        ProbabilityFeatureConfiguration probabilityFeatureConfiguration = context.config();
        int i = randomSource.nextInt(8) - randomSource.nextInt(8);
        int i1 = randomSource.nextInt(8) - randomSource.nextInt(8);
        int height = worldGenLevel.getHeight(Heightmap.Types.OCEAN_FLOOR, blockPos.getX() + i, blockPos.getZ() + i1);
        BlockPos blockPos1 = new BlockPos(blockPos.getX() + i, height, blockPos.getZ() + i1);
        if (worldGenLevel.getBlockState(blockPos1).is(Blocks.WATER)) {
            boolean flag1 = randomSource.nextDouble() < probabilityFeatureConfiguration.probability;
            BlockState blockState = flag1 ? Blocks.TALL_SEAGRASS.defaultBlockState() : Blocks.SEAGRASS.defaultBlockState();
            if (blockState.canSurvive(worldGenLevel, blockPos1)) {
                if (flag1) {
                    BlockState blockState1 = blockState.setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER);
                    BlockPos blockPos2 = blockPos1.above();
                    if (worldGenLevel.getBlockState(blockPos2).is(Blocks.WATER)) {
                        worldGenLevel.setBlock(blockPos1, blockState, Block.UPDATE_CLIENTS);
                        worldGenLevel.setBlock(blockPos2, blockState1, Block.UPDATE_CLIENTS);
                    }
                } else {
                    worldGenLevel.setBlock(blockPos1, blockState, Block.UPDATE_CLIENTS);
                }

                flag = true;
            }
        }

        return flag;
    }
}
