package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public abstract class CoralFeature extends Feature<NoneFeatureConfiguration> {
    public CoralFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        RandomSource randomSource = context.random();
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        Optional<Block> optional = BuiltInRegistries.BLOCK.getRandomElementOf(BlockTags.CORAL_BLOCKS, randomSource).map(Holder::value);
        return !optional.isEmpty() && this.placeFeature(worldGenLevel, randomSource, blockPos, optional.get().defaultBlockState());
    }

    protected abstract boolean placeFeature(LevelAccessor level, RandomSource random, BlockPos pos, BlockState state);

    protected boolean placeCoralBlock(LevelAccessor level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos blockPos = pos.above();
        BlockState blockState = level.getBlockState(pos);
        if ((blockState.is(Blocks.WATER) || blockState.is(BlockTags.CORALS)) && level.getBlockState(blockPos).is(Blocks.WATER)) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            if (random.nextFloat() < 0.25F) {
                BuiltInRegistries.BLOCK
                    .getRandomElementOf(BlockTags.CORALS, random)
                    .map(Holder::value)
                    .ifPresent(block -> level.setBlock(blockPos, block.defaultBlockState(), Block.UPDATE_CLIENTS));
            } else if (random.nextFloat() < 0.05F) {
                level.setBlock(blockPos, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, random.nextInt(4) + 1), Block.UPDATE_CLIENTS);
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (random.nextFloat() < 0.2F) {
                    BlockPos blockPos1 = pos.relative(direction);
                    if (level.getBlockState(blockPos1).is(Blocks.WATER)) {
                        BuiltInRegistries.BLOCK.getRandomElementOf(BlockTags.WALL_CORALS, random).map(Holder::value).ifPresent(block -> {
                            BlockState blockState1 = block.defaultBlockState();
                            if (blockState1.hasProperty(BaseCoralWallFanBlock.FACING)) {
                                blockState1 = blockState1.setValue(BaseCoralWallFanBlock.FACING, direction);
                            }

                            level.setBlock(blockPos1, blockState1, Block.UPDATE_CLIENTS);
                        });
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }
}
