package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.CountConfiguration;

public class SeaPickleFeature extends Feature<CountConfiguration> {
    public SeaPickleFeature(Codec<CountConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<CountConfiguration> context) {
        int i = 0;
        RandomSource randomSource = context.random();
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        int i1 = context.config().count().sample(randomSource);

        for (int i2 = 0; i2 < i1; i2++) {
            int i3 = randomSource.nextInt(8) - randomSource.nextInt(8);
            int i4 = randomSource.nextInt(8) - randomSource.nextInt(8);
            int height = worldGenLevel.getHeight(Heightmap.Types.OCEAN_FLOOR, blockPos.getX() + i3, blockPos.getZ() + i4);
            BlockPos blockPos1 = new BlockPos(blockPos.getX() + i3, height, blockPos.getZ() + i4);
            BlockState blockState = Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, randomSource.nextInt(4) + 1);
            if (worldGenLevel.getBlockState(blockPos1).is(Blocks.WATER) && blockState.canSurvive(worldGenLevel, blockPos1)) {
                worldGenLevel.setBlock(blockPos1, blockState, Block.UPDATE_CLIENTS);
                i++;
            }
        }

        return i > 0;
    }
}
