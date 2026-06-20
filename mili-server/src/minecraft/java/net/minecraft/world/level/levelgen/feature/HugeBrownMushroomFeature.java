package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;

public class HugeBrownMushroomFeature extends AbstractHugeMushroomFeature {
    public HugeBrownMushroomFeature(Codec<HugeMushroomFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    protected void makeCap(
        LevelAccessor level, RandomSource random, BlockPos pos, int treeHeight, BlockPos.MutableBlockPos mutablePos, HugeMushroomFeatureConfiguration config
    ) {
        int i = config.foliageRadius;

        for (int i1 = -i; i1 <= i; i1++) {
            for (int i2 = -i; i2 <= i; i2++) {
                boolean flag = i1 == -i;
                boolean flag1 = i1 == i;
                boolean flag2 = i2 == -i;
                boolean flag3 = i2 == i;
                boolean flag4 = flag || flag1;
                boolean flag5 = flag2 || flag3;
                if (!flag4 || !flag5) {
                    mutablePos.setWithOffset(pos, i1, treeHeight, i2);
                    boolean flag6 = flag || flag5 && i1 == 1 - i;
                    boolean flag7 = flag1 || flag5 && i1 == i - 1;
                    boolean flag8 = flag2 || flag4 && i2 == 1 - i;
                    boolean flag9 = flag3 || flag4 && i2 == i - 1;
                    BlockState state = config.capProvider.getState(random, pos);
                    if (state.hasProperty(HugeMushroomBlock.WEST)
                        && state.hasProperty(HugeMushroomBlock.EAST)
                        && state.hasProperty(HugeMushroomBlock.NORTH)
                        && state.hasProperty(HugeMushroomBlock.SOUTH)) {
                        state = state.setValue(HugeMushroomBlock.WEST, flag6)
                            .setValue(HugeMushroomBlock.EAST, flag7)
                            .setValue(HugeMushroomBlock.NORTH, flag8)
                            .setValue(HugeMushroomBlock.SOUTH, flag9);
                    }

                    this.placeMushroomBlock(level, mutablePos, state);
                }
            }
        }
    }

    @Override
    protected int getTreeRadiusForHeight(int unused, int height, int foliageRadius, int y) {
        return y <= 3 ? 0 : foliageRadius;
    }
}
