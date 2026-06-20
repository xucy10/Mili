package net.minecraft.world.level.levelgen.feature;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.DeltaFeatureConfiguration;

public class DeltaFeature extends Feature<DeltaFeatureConfiguration> {
    private static final ImmutableList<Block> CANNOT_REPLACE = ImmutableList.of(
        Blocks.BEDROCK, Blocks.NETHER_BRICKS, Blocks.NETHER_BRICK_FENCE, Blocks.NETHER_BRICK_STAIRS, Blocks.NETHER_WART, Blocks.CHEST, Blocks.SPAWNER
    );
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final double RIM_SPAWN_CHANCE = 0.9;

    public DeltaFeature(Codec<DeltaFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<DeltaFeatureConfiguration> context) {
        boolean flag = false;
        RandomSource randomSource = context.random();
        WorldGenLevel worldGenLevel = context.level();
        DeltaFeatureConfiguration deltaFeatureConfiguration = context.config();
        BlockPos blockPos = context.origin();
        boolean flag1 = randomSource.nextDouble() < 0.9;
        int i = flag1 ? deltaFeatureConfiguration.rimSize().sample(randomSource) : 0;
        int i1 = flag1 ? deltaFeatureConfiguration.rimSize().sample(randomSource) : 0;
        boolean flag2 = flag1 && i != 0 && i1 != 0;
        int i2 = deltaFeatureConfiguration.size().sample(randomSource);
        int i3 = deltaFeatureConfiguration.size().sample(randomSource);
        int max = Math.max(i2, i3);

        for (BlockPos blockPos1 : BlockPos.withinManhattan(blockPos, i2, 0, i3)) {
            if (blockPos1.distManhattan(blockPos) > max) {
                break;
            }

            if (isClear(worldGenLevel, blockPos1, deltaFeatureConfiguration)) {
                if (flag2) {
                    flag = true;
                    this.setBlock(worldGenLevel, blockPos1, deltaFeatureConfiguration.rim());
                }

                BlockPos blockPos2 = blockPos1.offset(i, 0, i1);
                if (isClear(worldGenLevel, blockPos2, deltaFeatureConfiguration)) {
                    flag = true;
                    this.setBlock(worldGenLevel, blockPos2, deltaFeatureConfiguration.contents());
                }
            }
        }

        return flag;
    }

    private static boolean isClear(LevelAccessor level, BlockPos pos, DeltaFeatureConfiguration config) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.is(config.contents().getBlock())) {
            return false;
        } else if (CANNOT_REPLACE.contains(blockState.getBlock())) {
            return false;
        } else {
            for (Direction direction : DIRECTIONS) {
                boolean isAir = level.getBlockState(pos.relative(direction)).isAir();
                if (isAir && direction != Direction.UP || !isAir && direction == Direction.UP) {
                    return false;
                }
            }

            return true;
        }
    }
}
