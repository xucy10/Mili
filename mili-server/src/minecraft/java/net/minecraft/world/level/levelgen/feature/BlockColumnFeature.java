package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.configurations.BlockColumnConfiguration;

public class BlockColumnFeature extends Feature<BlockColumnConfiguration> {
    public BlockColumnFeature(Codec<BlockColumnConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<BlockColumnConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockColumnConfiguration blockColumnConfiguration = context.config();
        RandomSource randomSource = context.random();
        int size = blockColumnConfiguration.layers().size();
        int[] ints = new int[size];
        int i = 0;

        for (int i1 = 0; i1 < size; i1++) {
            ints[i1] = blockColumnConfiguration.layers().get(i1).height().sample(randomSource);
            i += ints[i1];
        }

        if (i == 0) {
            return false;
        } else {
            BlockPos.MutableBlockPos mutableBlockPos = context.origin().mutable();
            BlockPos.MutableBlockPos mutableBlockPos1 = mutableBlockPos.mutable().move(blockColumnConfiguration.direction());

            for (int i2 = 0; i2 < i; i2++) {
                if (!blockColumnConfiguration.allowedPlacement().test(worldGenLevel, mutableBlockPos1)) {
                    truncate(ints, i, i2, blockColumnConfiguration.prioritizeTip());
                    break;
                }

                mutableBlockPos1.move(blockColumnConfiguration.direction());
            }

            for (int i2 = 0; i2 < size; i2++) {
                int i3 = ints[i2];
                if (i3 != 0) {
                    BlockColumnConfiguration.Layer layer = blockColumnConfiguration.layers().get(i2);

                    for (int i4 = 0; i4 < i3; i4++) {
                        worldGenLevel.setBlock(mutableBlockPos, layer.state().getState(randomSource, mutableBlockPos), Block.UPDATE_CLIENTS);
                        mutableBlockPos.move(blockColumnConfiguration.direction());
                    }
                }
            }

            return true;
        }
    }

    private static void truncate(int[] layerHeights, int totalHeight, int currentHeight, boolean prioritizeTip) {
        int i = totalHeight - currentHeight;
        int i1 = prioritizeTip ? 1 : -1;
        int i2 = prioritizeTip ? 0 : layerHeights.length - 1;
        int i3 = prioritizeTip ? layerHeights.length : -1;

        for (int i4 = i2; i4 != i3 && i > 0; i4 += i1) {
            int i5 = layerHeights[i4];
            int min = Math.min(i5, i);
            i -= min;
            layerHeights[i4] -= min;
        }
    }
}
