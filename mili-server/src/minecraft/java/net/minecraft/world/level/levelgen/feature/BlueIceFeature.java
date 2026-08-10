package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class BlueIceFeature extends Feature<NoneFeatureConfiguration> {
    public BlueIceFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        if (blockPos.getY() > worldGenLevel.getSeaLevel() - 1) {
            return false;
        } else if (!worldGenLevel.getBlockState(blockPos).is(Blocks.WATER) && !worldGenLevel.getBlockState(blockPos.below()).is(Blocks.WATER)) {
            return false;
        } else {
            boolean flag = false;

            for (Direction direction : Direction.values()) {
                if (direction != Direction.DOWN && worldGenLevel.getBlockState(blockPos.relative(direction)).is(Blocks.PACKED_ICE)) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                return false;
            } else {
                worldGenLevel.setBlock(blockPos, Blocks.BLUE_ICE.defaultBlockState(), Block.UPDATE_CLIENTS);

                for (int i = 0; i < 200; i++) {
                    int i1 = randomSource.nextInt(5) - randomSource.nextInt(6);
                    int i2 = 3;
                    if (i1 < 2) {
                        i2 += i1 / 2;
                    }

                    if (i2 >= 1) {
                        BlockPos blockPos1 = blockPos.offset(
                            randomSource.nextInt(i2) - randomSource.nextInt(i2), i1, randomSource.nextInt(i2) - randomSource.nextInt(i2)
                        );
                        BlockState blockState = worldGenLevel.getBlockState(blockPos1);
                        if (blockState.isAir() || blockState.is(Blocks.WATER) || blockState.is(Blocks.PACKED_ICE) || blockState.is(Blocks.ICE)) {
                            for (Direction direction1 : Direction.values()) {
                                BlockState blockState1 = worldGenLevel.getBlockState(blockPos1.relative(direction1));
                                if (blockState1.is(Blocks.BLUE_ICE)) {
                                    worldGenLevel.setBlock(blockPos1, Blocks.BLUE_ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
                                    break;
                                }
                            }
                        }
                    }
                }

                return true;
            }
        }
    }
}
