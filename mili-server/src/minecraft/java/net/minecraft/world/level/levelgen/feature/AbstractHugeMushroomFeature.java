package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;

public abstract class AbstractHugeMushroomFeature extends Feature<HugeMushroomFeatureConfiguration> {
    public AbstractHugeMushroomFeature(Codec<HugeMushroomFeatureConfiguration> codec) {
        super(codec);
    }

    protected void placeTrunk(
        LevelAccessor level, RandomSource random, BlockPos pos, HugeMushroomFeatureConfiguration config, int maxHeight, BlockPos.MutableBlockPos mutablePos
    ) {
        for (int i = 0; i < maxHeight; i++) {
            mutablePos.set(pos).move(Direction.UP, i);
            this.placeMushroomBlock(level, mutablePos, config.stemProvider.getState(random, pos));
        }
    }

    protected void placeMushroomBlock(LevelAccessor level, BlockPos.MutableBlockPos mutablePos, BlockState state) {
        BlockState blockState = level.getBlockState(mutablePos);
        if (blockState.isAir() || blockState.is(BlockTags.REPLACEABLE_BY_MUSHROOMS)) {
            this.setBlock(level, mutablePos, state);
        }
    }

    protected int getTreeHeight(RandomSource random) {
        int i = random.nextInt(3) + 4;
        if (random.nextInt(12) == 0) {
            i *= 2;
        }

        return i;
    }

    protected boolean isValidPosition(
        LevelAccessor level, BlockPos pos, int maxHeight, BlockPos.MutableBlockPos mutablePos, HugeMushroomFeatureConfiguration config
    ) {
        int y = pos.getY();
        if (y >= level.getMinY() + 1 && y + maxHeight + 1 <= level.getMaxY()) {
            BlockState blockState = level.getBlockState(pos.below());
            if (!isDirt(blockState) && !blockState.is(BlockTags.MUSHROOM_GROW_BLOCK)) {
                return false;
            } else {
                for (int i = 0; i <= maxHeight; i++) {
                    int treeRadiusForHeight = this.getTreeRadiusForHeight(-1, -1, config.foliageRadius, i);

                    for (int i1 = -treeRadiusForHeight; i1 <= treeRadiusForHeight; i1++) {
                        for (int i2 = -treeRadiusForHeight; i2 <= treeRadiusForHeight; i2++) {
                            BlockState blockState1 = level.getBlockState(mutablePos.setWithOffset(pos, i1, i, i2));
                            if (!blockState1.isAir() && !blockState1.is(BlockTags.LEAVES)) {
                                return false;
                            }
                        }
                    }
                }

                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<HugeMushroomFeatureConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        RandomSource randomSource = context.random();
        HugeMushroomFeatureConfiguration hugeMushroomFeatureConfiguration = context.config();
        int treeHeight = this.getTreeHeight(randomSource);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        if (!this.isValidPosition(worldGenLevel, blockPos, treeHeight, mutableBlockPos, hugeMushroomFeatureConfiguration)) {
            return false;
        } else {
            this.makeCap(worldGenLevel, randomSource, blockPos, treeHeight, mutableBlockPos, hugeMushroomFeatureConfiguration);
            this.placeTrunk(worldGenLevel, randomSource, blockPos, hugeMushroomFeatureConfiguration, treeHeight, mutableBlockPos);
            return true;
        }
    }

    protected abstract int getTreeRadiusForHeight(int unused, int height, int foliageRadius, int y);

    protected abstract void makeCap(
        LevelAccessor level, RandomSource random, BlockPos pos, int treeHeight, BlockPos.MutableBlockPos mutablePos, HugeMushroomFeatureConfiguration config
    );
}
