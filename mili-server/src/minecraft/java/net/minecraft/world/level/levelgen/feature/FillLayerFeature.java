package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.configurations.LayerConfiguration;

public class FillLayerFeature extends Feature<LayerConfiguration> {
    public FillLayerFeature(Codec<LayerConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<LayerConfiguration> context) {
        BlockPos blockPos = context.origin();
        LayerConfiguration layerConfiguration = context.config();
        WorldGenLevel worldGenLevel = context.level();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < 16; i++) {
            for (int i1 = 0; i1 < 16; i1++) {
                int i2 = blockPos.getX() + i;
                int i3 = blockPos.getZ() + i1;
                int i4 = worldGenLevel.getMinY() + layerConfiguration.height;
                mutableBlockPos.set(i2, i4, i3);
                if (worldGenLevel.getBlockState(mutableBlockPos).isAir()) {
                    worldGenLevel.setBlock(mutableBlockPos, layerConfiguration.state, Block.UPDATE_CLIENTS);
                }
            }
        }

        return true;
    }
}
