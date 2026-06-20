package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;

public class HugeRedMushroomFeature extends AbstractHugeMushroomFeature {
    public HugeRedMushroomFeature(Codec<HugeMushroomFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    protected void makeCap(
        LevelAccessor level, RandomSource random, BlockPos pos, int treeHeight, BlockPos.MutableBlockPos mutablePos, HugeMushroomFeatureConfiguration config
    ) {
        for (int i = treeHeight - 3; i <= treeHeight; i++) {
            int i1 = i < treeHeight ? config.foliageRadius : config.foliageRadius - 1;
            int i2 = config.foliageRadius - 2;

            for (int i3 = -i1; i3 <= i1; i3++) {
                for (int i4 = -i1; i4 <= i1; i4++) {
                    boolean flag = i3 == -i1;
                    boolean flag1 = i3 == i1;
                    boolean flag2 = i4 == -i1;
                    boolean flag3 = i4 == i1;
                    boolean flag4 = flag || flag1;
                    boolean flag5 = flag2 || flag3;
                    if (i >= treeHeight || flag4 != flag5) {
                        mutablePos.setWithOffset(pos, i3, i, i4);
                        BlockState state = config.capProvider.getState(random, pos);
                        if (state.hasProperty(HugeMushroomBlock.WEST)
                            && state.hasProperty(HugeMushroomBlock.EAST)
                            && state.hasProperty(HugeMushroomBlock.NORTH)
                            && state.hasProperty(HugeMushroomBlock.SOUTH)
                            && state.hasProperty(HugeMushroomBlock.UP)) {
                            state = state.setValue(HugeMushroomBlock.UP, i >= treeHeight - 1)
                                .setValue(HugeMushroomBlock.WEST, i3 < -i2)
                                .setValue(HugeMushroomBlock.EAST, i3 > i2)
                                .setValue(HugeMushroomBlock.NORTH, i4 < -i2)
                                .setValue(HugeMushroomBlock.SOUTH, i4 > i2);
                        }

                        this.placeMushroomBlock(level, mutablePos, state);
                    }
                }
            }
        }
    }

    @Override
    protected int getTreeRadiusForHeight(int unused, int height, int foliageRadius, int y) {
        int i = 0;
        if (y < height && y >= height - 3) {
            i = foliageRadius;
        } else if (y == height) {
            i = foliageRadius;
        }

        return i;
    }
}
