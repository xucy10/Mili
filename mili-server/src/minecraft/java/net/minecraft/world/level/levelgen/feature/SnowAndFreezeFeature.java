package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class SnowAndFreezeFeature extends Feature<NoneFeatureConfiguration> {
    public SnowAndFreezeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos mutableBlockPos1 = new BlockPos.MutableBlockPos();

        for (int i = 0; i < 16; i++) {
            for (int i1 = 0; i1 < 16; i1++) {
                int i2 = blockPos.getX() + i;
                int i3 = blockPos.getZ() + i1;
                int height = worldGenLevel.getHeight(Heightmap.Types.MOTION_BLOCKING, i2, i3);
                mutableBlockPos.set(i2, height, i3);
                mutableBlockPos1.set(mutableBlockPos).move(Direction.DOWN, 1);
                Biome biome = worldGenLevel.getBiome(mutableBlockPos).value();
                if (biome.shouldFreeze(worldGenLevel, mutableBlockPos1, false)) {
                    worldGenLevel.setBlock(mutableBlockPos1, Blocks.ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }

                if (biome.shouldSnow(worldGenLevel, mutableBlockPos)) {
                    worldGenLevel.setBlock(mutableBlockPos, Blocks.SNOW.defaultBlockState(), Block.UPDATE_CLIENTS);
                    BlockState blockState = worldGenLevel.getBlockState(mutableBlockPos1);
                    if (blockState.hasProperty(SnowyDirtBlock.SNOWY)) {
                        worldGenLevel.setBlock(mutableBlockPos1, blockState.setValue(SnowyDirtBlock.SNOWY, true), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }

        return true;
    }
}
