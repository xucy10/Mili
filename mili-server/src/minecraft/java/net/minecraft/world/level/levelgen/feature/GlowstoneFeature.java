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

public class GlowstoneFeature extends Feature<NoneFeatureConfiguration> {
    public GlowstoneFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        RandomSource randomSource = context.random();
        if (!worldGenLevel.isEmptyBlock(blockPos)) {
            return false;
        } else {
            BlockState blockState = worldGenLevel.getBlockState(blockPos.above());
            if (!blockState.is(Blocks.NETHERRACK) && !blockState.is(Blocks.BASALT) && !blockState.is(Blocks.BLACKSTONE)) {
                return false;
            } else {
                worldGenLevel.setBlock(blockPos, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);

                for (int i = 0; i < 1500; i++) {
                    BlockPos blockPos1 = blockPos.offset(
                        randomSource.nextInt(8) - randomSource.nextInt(8), -randomSource.nextInt(12), randomSource.nextInt(8) - randomSource.nextInt(8)
                    );
                    if (worldGenLevel.getBlockState(blockPos1).isAir()) {
                        int i1 = 0;

                        for (Direction direction : Direction.values()) {
                            if (worldGenLevel.getBlockState(blockPos1.relative(direction)).is(Blocks.GLOWSTONE)) {
                                i1++;
                            }

                            if (i1 > 1) {
                                break;
                            }
                        }

                        if (i1 == 1) {
                            worldGenLevel.setBlock(blockPos1, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }

                return true;
            }
        }
    }
}
