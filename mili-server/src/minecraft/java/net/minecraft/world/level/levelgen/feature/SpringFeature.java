package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.SpringConfiguration;

public class SpringFeature extends Feature<SpringConfiguration> {
    public SpringFeature(Codec<SpringConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<SpringConfiguration> context) {
        SpringConfiguration springConfiguration = context.config();
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        if (!worldGenLevel.getBlockState(blockPos.above()).is(springConfiguration.validBlocks)) {
            return false;
        } else if (springConfiguration.requiresBlockBelow && !worldGenLevel.getBlockState(blockPos.below()).is(springConfiguration.validBlocks)) {
            return false;
        } else {
            BlockState blockState = worldGenLevel.getBlockState(blockPos);
            if (!blockState.isAir() && !blockState.is(springConfiguration.validBlocks)) {
                return false;
            } else {
                int i = 0;
                int i1 = 0;
                if (worldGenLevel.getBlockState(blockPos.west()).is(springConfiguration.validBlocks)) {
                    i1++;
                }

                if (worldGenLevel.getBlockState(blockPos.east()).is(springConfiguration.validBlocks)) {
                    i1++;
                }

                if (worldGenLevel.getBlockState(blockPos.north()).is(springConfiguration.validBlocks)) {
                    i1++;
                }

                if (worldGenLevel.getBlockState(blockPos.south()).is(springConfiguration.validBlocks)) {
                    i1++;
                }

                if (worldGenLevel.getBlockState(blockPos.below()).is(springConfiguration.validBlocks)) {
                    i1++;
                }

                int i2 = 0;
                if (worldGenLevel.isEmptyBlock(blockPos.west())) {
                    i2++;
                }

                if (worldGenLevel.isEmptyBlock(blockPos.east())) {
                    i2++;
                }

                if (worldGenLevel.isEmptyBlock(blockPos.north())) {
                    i2++;
                }

                if (worldGenLevel.isEmptyBlock(blockPos.south())) {
                    i2++;
                }

                if (worldGenLevel.isEmptyBlock(blockPos.below())) {
                    i2++;
                }

                if (i1 == springConfiguration.rockCount && i2 == springConfiguration.holeCount) {
                    worldGenLevel.setBlock(blockPos, springConfiguration.state.createLegacyBlock(), Block.UPDATE_CLIENTS);
                    worldGenLevel.scheduleTick(blockPos, springConfiguration.state.getType(), 0);
                    i++;
                }

                return i > 0;
            }
        }
    }
}
