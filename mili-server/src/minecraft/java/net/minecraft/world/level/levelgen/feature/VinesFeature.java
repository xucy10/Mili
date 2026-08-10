package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class VinesFeature extends Feature<NoneFeatureConfiguration> {
    public VinesFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        context.config();
        if (!worldGenLevel.isEmptyBlock(blockPos)) {
            return false;
        } else {
            for (Direction direction : Direction.values()) {
                if (direction != Direction.DOWN && VineBlock.isAcceptableNeighbour(worldGenLevel, blockPos.relative(direction), direction)) {
                    worldGenLevel.setBlock(
                        blockPos, Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(direction), true), Block.UPDATE_CLIENTS
                    );
                    return true;
                }
            }

            return false;
        }
    }
}
