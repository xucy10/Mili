package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NetherForestVegetationConfig;

public class NetherForestVegetationFeature extends Feature<NetherForestVegetationConfig> {
    public NetherForestVegetationFeature(Codec<NetherForestVegetationConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NetherForestVegetationConfig> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        BlockState blockState = worldGenLevel.getBlockState(blockPos.below());
        NetherForestVegetationConfig netherForestVegetationConfig = context.config();
        RandomSource randomSource = context.random();
        if (!blockState.is(BlockTags.NYLIUM)) {
            return false;
        } else {
            int y = blockPos.getY();
            if (y >= worldGenLevel.getMinY() + 1 && y + 1 <= worldGenLevel.getMaxY()) {
                int i = 0;

                for (int i1 = 0; i1 < netherForestVegetationConfig.spreadWidth * netherForestVegetationConfig.spreadWidth; i1++) {
                    BlockPos blockPos1 = blockPos.offset(
                        randomSource.nextInt(netherForestVegetationConfig.spreadWidth) - randomSource.nextInt(netherForestVegetationConfig.spreadWidth),
                        randomSource.nextInt(netherForestVegetationConfig.spreadHeight) - randomSource.nextInt(netherForestVegetationConfig.spreadHeight),
                        randomSource.nextInt(netherForestVegetationConfig.spreadWidth) - randomSource.nextInt(netherForestVegetationConfig.spreadWidth)
                    );
                    BlockState state = netherForestVegetationConfig.stateProvider.getState(randomSource, blockPos1);
                    if (worldGenLevel.isEmptyBlock(blockPos1) && blockPos1.getY() > worldGenLevel.getMinY() && state.canSurvive(worldGenLevel, blockPos1)) {
                        worldGenLevel.setBlock(blockPos1, state, Block.UPDATE_CLIENTS);
                        i++;
                    }
                }

                return i > 0;
            } else {
                return false;
            }
        }
    }
}
