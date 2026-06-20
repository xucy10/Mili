package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.BlockPileConfiguration;

public class BlockPileFeature extends Feature<BlockPileConfiguration> {
    public BlockPileFeature(Codec<BlockPileConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<BlockPileConfiguration> context) {
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        BlockPileConfiguration blockPileConfiguration = context.config();
        if (blockPos.getY() < worldGenLevel.getMinY() + 5) {
            return false;
        } else {
            int i = 2 + randomSource.nextInt(2);
            int i1 = 2 + randomSource.nextInt(2);

            for (BlockPos blockPos1 : BlockPos.betweenClosed(blockPos.offset(-i, 0, -i1), blockPos.offset(i, 1, i1))) {
                int i2 = blockPos.getX() - blockPos1.getX();
                int i3 = blockPos.getZ() - blockPos1.getZ();
                if (i2 * i2 + i3 * i3 <= randomSource.nextFloat() * 10.0F - randomSource.nextFloat() * 6.0F) {
                    this.tryPlaceBlock(worldGenLevel, blockPos1, randomSource, blockPileConfiguration);
                } else if (randomSource.nextFloat() < 0.031) {
                    this.tryPlaceBlock(worldGenLevel, blockPos1, randomSource, blockPileConfiguration);
                }
            }

            return true;
        }
    }

    private boolean mayPlaceOn(LevelAccessor level, BlockPos pos, RandomSource random) {
        BlockPos blockPos = pos.below();
        BlockState blockState = level.getBlockState(blockPos);
        return blockState.is(Blocks.DIRT_PATH) ? random.nextBoolean() : blockState.isFaceSturdy(level, blockPos, Direction.UP);
    }

    private void tryPlaceBlock(LevelAccessor level, BlockPos pos, RandomSource random, BlockPileConfiguration config) {
        if (level.isEmptyBlock(pos) && this.mayPlaceOn(level, pos, random)) {
            level.setBlock(pos, config.stateProvider.getState(random, pos), Block.UPDATE_NONE);
        }
    }
}
