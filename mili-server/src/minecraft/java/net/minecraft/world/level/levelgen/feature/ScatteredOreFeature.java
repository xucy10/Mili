package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public class ScatteredOreFeature extends Feature<OreConfiguration> {
    private static final int MAX_DIST_FROM_ORIGIN = 7;

    ScatteredOreFeature(Codec<OreConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<OreConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        OreConfiguration oreConfiguration = context.config();
        BlockPos blockPos = context.origin();
        int randomInt = randomSource.nextInt(oreConfiguration.size + 1);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < randomInt; i++) {
            this.offsetTargetPos(mutableBlockPos, randomSource, blockPos, Math.min(i, 7));
            BlockState blockState = worldGenLevel.getBlockState(mutableBlockPos);

            for (OreConfiguration.TargetBlockState targetBlockState : oreConfiguration.targetStates) {
                if (OreFeature.canPlaceOre(blockState, worldGenLevel::getBlockState, randomSource, oreConfiguration, targetBlockState, mutableBlockPos)) {
                    worldGenLevel.setBlock(mutableBlockPos, targetBlockState.state, Block.UPDATE_CLIENTS);
                    break;
                }
            }
        }

        return true;
    }

    private void offsetTargetPos(BlockPos.MutableBlockPos mutablePos, RandomSource random, BlockPos pos, int magnitude) {
        int randomPlacementInOneAxisRelativeToOrigin = this.getRandomPlacementInOneAxisRelativeToOrigin(random, magnitude);
        int randomPlacementInOneAxisRelativeToOrigin1 = this.getRandomPlacementInOneAxisRelativeToOrigin(random, magnitude);
        int randomPlacementInOneAxisRelativeToOrigin2 = this.getRandomPlacementInOneAxisRelativeToOrigin(random, magnitude);
        mutablePos.setWithOffset(
            pos, randomPlacementInOneAxisRelativeToOrigin, randomPlacementInOneAxisRelativeToOrigin1, randomPlacementInOneAxisRelativeToOrigin2
        );
    }

    private int getRandomPlacementInOneAxisRelativeToOrigin(RandomSource random, int magnitude) {
        return Math.round((random.nextFloat() - random.nextFloat()) * magnitude);
    }
}
