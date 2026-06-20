package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class IceSpikeFeature extends Feature<NoneFeatureConfiguration> {
    public IceSpikeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos blockPos = context.origin();
        RandomSource randomSource = context.random();
        WorldGenLevel worldGenLevel = context.level();

        while (worldGenLevel.isEmptyBlock(blockPos) && blockPos.getY() > worldGenLevel.getMinY() + 2) {
            blockPos = blockPos.below();
        }

        if (!worldGenLevel.getBlockState(blockPos).is(Blocks.SNOW_BLOCK)) {
            return false;
        } else {
            blockPos = blockPos.above(randomSource.nextInt(4));
            int i = randomSource.nextInt(4) + 7;
            int i1 = i / 4 + randomSource.nextInt(2);
            if (i1 > 1 && randomSource.nextInt(60) == 0) {
                blockPos = blockPos.above(10 + randomSource.nextInt(30));
            }

            for (int i2 = 0; i2 < i; i2++) {
                float f = (1.0F - (float)i2 / i) * i1;
                int ceil = Mth.ceil(f);

                for (int i3 = -ceil; i3 <= ceil; i3++) {
                    float f1 = Mth.abs(i3) - 0.25F;

                    for (int i4 = -ceil; i4 <= ceil; i4++) {
                        float f2 = Mth.abs(i4) - 0.25F;
                        if ((i3 == 0 && i4 == 0 || !(f1 * f1 + f2 * f2 > f * f))
                            && (i3 != -ceil && i3 != ceil && i4 != -ceil && i4 != ceil || !(randomSource.nextFloat() > 0.75F))) {
                            BlockState blockState = worldGenLevel.getBlockState(blockPos.offset(i3, i2, i4));
                            if (blockState.isAir() || isDirt(blockState) || blockState.is(Blocks.SNOW_BLOCK) || blockState.is(Blocks.ICE)) {
                                this.setBlock(worldGenLevel, blockPos.offset(i3, i2, i4), Blocks.PACKED_ICE.defaultBlockState());
                            }

                            if (i2 != 0 && ceil > 1) {
                                blockState = worldGenLevel.getBlockState(blockPos.offset(i3, -i2, i4));
                                if (blockState.isAir() || isDirt(blockState) || blockState.is(Blocks.SNOW_BLOCK) || blockState.is(Blocks.ICE)) {
                                    this.setBlock(worldGenLevel, blockPos.offset(i3, -i2, i4), Blocks.PACKED_ICE.defaultBlockState());
                                }
                            }
                        }
                    }
                }
            }

            int i2 = i1 - 1;
            if (i2 < 0) {
                i2 = 0;
            } else if (i2 > 1) {
                i2 = 1;
            }

            for (int i5 = -i2; i5 <= i2; i5++) {
                for (int ceil = -i2; ceil <= i2; ceil++) {
                    BlockPos blockPos1 = blockPos.offset(i5, -1, ceil);
                    int i6 = 50;
                    if (Math.abs(i5) == 1 && Math.abs(ceil) == 1) {
                        i6 = randomSource.nextInt(5);
                    }

                    while (blockPos1.getY() > 50) {
                        BlockState blockState1 = worldGenLevel.getBlockState(blockPos1);
                        if (!blockState1.isAir()
                            && !isDirt(blockState1)
                            && !blockState1.is(Blocks.SNOW_BLOCK)
                            && !blockState1.is(Blocks.ICE)
                            && !blockState1.is(Blocks.PACKED_ICE)) {
                            break;
                        }

                        this.setBlock(worldGenLevel, blockPos1, Blocks.PACKED_ICE.defaultBlockState());
                        blockPos1 = blockPos1.below();
                        if (--i6 <= 0) {
                            blockPos1 = blockPos1.below(randomSource.nextInt(5) + 1);
                            i6 = randomSource.nextInt(5);
                        }
                    }
                }
            }

            return true;
        }
    }
}
