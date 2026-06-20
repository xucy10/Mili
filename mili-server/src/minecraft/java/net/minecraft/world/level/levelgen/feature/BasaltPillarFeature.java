package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class BasaltPillarFeature extends Feature<NoneFeatureConfiguration> {
    public BasaltPillarFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        if (worldGenLevel.isEmptyBlock(blockPos) && !worldGenLevel.isEmptyBlock(blockPos.above())) {
            BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable();
            BlockPos.MutableBlockPos mutableBlockPos1 = blockPos.mutable();
            boolean flag = true;
            boolean flag1 = true;
            boolean flag2 = true;
            boolean flag3 = true;

            while (worldGenLevel.isEmptyBlock(mutableBlockPos)) {
                if (worldGenLevel.isOutsideBuildHeight(mutableBlockPos)) {
                    return true;
                }

                worldGenLevel.setBlock(mutableBlockPos, Blocks.BASALT.defaultBlockState(), Block.UPDATE_CLIENTS);
                flag = flag && this.placeHangOff(worldGenLevel, randomSource, mutableBlockPos1.setWithOffset(mutableBlockPos, Direction.NORTH));
                flag1 = flag1 && this.placeHangOff(worldGenLevel, randomSource, mutableBlockPos1.setWithOffset(mutableBlockPos, Direction.SOUTH));
                flag2 = flag2 && this.placeHangOff(worldGenLevel, randomSource, mutableBlockPos1.setWithOffset(mutableBlockPos, Direction.WEST));
                flag3 = flag3 && this.placeHangOff(worldGenLevel, randomSource, mutableBlockPos1.setWithOffset(mutableBlockPos, Direction.EAST));
                mutableBlockPos.move(Direction.DOWN);
            }

            mutableBlockPos.move(Direction.UP);
            this.placeBaseHangOff(worldGenLevel, randomSource, mutableBlockPos1.setWithOffset(mutableBlockPos, Direction.NORTH));
            this.placeBaseHangOff(worldGenLevel, randomSource, mutableBlockPos1.setWithOffset(mutableBlockPos, Direction.SOUTH));
            this.placeBaseHangOff(worldGenLevel, randomSource, mutableBlockPos1.setWithOffset(mutableBlockPos, Direction.WEST));
            this.placeBaseHangOff(worldGenLevel, randomSource, mutableBlockPos1.setWithOffset(mutableBlockPos, Direction.EAST));
            mutableBlockPos.move(Direction.DOWN);
            BlockPos.MutableBlockPos mutableBlockPos2 = new BlockPos.MutableBlockPos();

            for (int i = -3; i < 4; i++) {
                for (int i1 = -3; i1 < 4; i1++) {
                    int i2 = Mth.abs(i) * Mth.abs(i1);
                    if (randomSource.nextInt(10) < 10 - i2) {
                        mutableBlockPos2.set(mutableBlockPos.offset(i, 0, i1));
                        int i3 = 3;

                        while (worldGenLevel.isEmptyBlock(mutableBlockPos1.setWithOffset(mutableBlockPos2, Direction.DOWN))) {
                            mutableBlockPos2.move(Direction.DOWN);
                            if (--i3 <= 0) {
                                break;
                            }
                        }

                        if (!worldGenLevel.isEmptyBlock(mutableBlockPos1.setWithOffset(mutableBlockPos2, Direction.DOWN))) {
                            worldGenLevel.setBlock(mutableBlockPos2, Blocks.BASALT.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private void placeBaseHangOff(LevelAccessor level, RandomSource random, BlockPos pos) {
        if (random.nextBoolean()) {
            level.setBlock(pos, Blocks.BASALT.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private boolean placeHangOff(LevelAccessor level, RandomSource random, BlockPos pos) {
        if (random.nextInt(10) != 0) {
            level.setBlock(pos, Blocks.BASALT.defaultBlockState(), Block.UPDATE_CLIENTS);
            return true;
        } else {
            return false;
        }
    }
}
