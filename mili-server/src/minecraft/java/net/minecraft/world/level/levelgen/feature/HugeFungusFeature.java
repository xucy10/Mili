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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

public class HugeFungusFeature extends Feature<HugeFungusConfiguration> {
    private static final float HUGE_PROBABILITY = 0.06F;

    public HugeFungusFeature(Codec<HugeFungusConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<HugeFungusConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        RandomSource randomSource = context.random();
        ChunkGenerator chunkGenerator = context.chunkGenerator();
        HugeFungusConfiguration hugeFungusConfiguration = context.config();
        Block block = hugeFungusConfiguration.validBaseState.getBlock();
        BlockPos blockPos1 = null;
        BlockState blockState = worldGenLevel.getBlockState(blockPos.below());
        if (blockState.is(block)) {
            blockPos1 = blockPos;
        }

        if (blockPos1 == null) {
            return false;
        } else {
            int randomInt = Mth.nextInt(randomSource, 4, 13);
            if (randomSource.nextInt(12) == 0) {
                randomInt *= 2;
            }

            if (!hugeFungusConfiguration.planted) {
                int genDepth = chunkGenerator.getGenDepth();
                if (blockPos1.getY() + randomInt + 1 >= genDepth) {
                    return false;
                }
            }

            boolean flag = !hugeFungusConfiguration.planted && randomSource.nextFloat() < 0.06F;
            worldGenLevel.setBlock(blockPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE);
            this.placeStem(worldGenLevel, randomSource, hugeFungusConfiguration, blockPos1, randomInt, flag);
            this.placeHat(worldGenLevel, randomSource, hugeFungusConfiguration, blockPos1, randomInt, flag);
            return true;
        }
    }

    private static boolean isReplaceable(WorldGenLevel level, BlockPos pos, HugeFungusConfiguration config, boolean checkConfig) {
        return level.isStateAtPosition(pos, BlockBehaviour.BlockStateBase::canBeReplaced) || checkConfig && config.replaceableBlocks.test(level, pos);
    }

    private void placeStem(WorldGenLevel level, RandomSource random, HugeFungusConfiguration config, BlockPos pos, int height, boolean huge) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        BlockState blockState = config.stemState;
        int i = huge ? 1 : 0;

        for (int i1 = -i; i1 <= i; i1++) {
            for (int i2 = -i; i2 <= i; i2++) {
                boolean flag = huge && Mth.abs(i1) == i && Mth.abs(i2) == i;

                for (int i3 = 0; i3 < height; i3++) {
                    mutableBlockPos.setWithOffset(pos, i1, i3, i2);
                    if (isReplaceable(level, mutableBlockPos, config, true)) {
                        if (config.planted) {
                            if (!level.getBlockState(mutableBlockPos.below()).isAir()) {
                                level.destroyBlock(mutableBlockPos, true);
                            }

                            level.setBlock(mutableBlockPos, blockState, Block.UPDATE_ALL);
                        } else if (flag) {
                            if (random.nextFloat() < 0.1F) {
                                this.setBlock(level, mutableBlockPos, blockState);
                            }
                        } else {
                            this.setBlock(level, mutableBlockPos, blockState);
                        }
                    }
                }
            }
        }
    }

    private void placeHat(WorldGenLevel level, RandomSource random, HugeFungusConfiguration config, BlockPos pos, int height, boolean huge) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        boolean isNetherWartBlock = config.hatState.is(Blocks.NETHER_WART_BLOCK);
        int min = Math.min(random.nextInt(1 + height / 3) + 5, height);
        int i = height - min;

        for (int i1 = i; i1 <= height; i1++) {
            int i2 = i1 < height - random.nextInt(3) ? 2 : 1;
            if (min > 8 && i1 < i + 4) {
                i2 = 3;
            }

            if (huge) {
                i2++;
            }

            for (int i3 = -i2; i3 <= i2; i3++) {
                for (int i4 = -i2; i4 <= i2; i4++) {
                    boolean flag = i3 == -i2 || i3 == i2;
                    boolean flag1 = i4 == -i2 || i4 == i2;
                    boolean flag2 = !flag && !flag1 && i1 != height;
                    boolean flag3 = flag && flag1;
                    boolean flag4 = i1 < i + 3;
                    mutableBlockPos.setWithOffset(pos, i3, i1, i4);
                    if (isReplaceable(level, mutableBlockPos, config, false)) {
                        if (config.planted && !level.getBlockState(mutableBlockPos.below()).isAir()) {
                            level.destroyBlock(mutableBlockPos, true);
                        }

                        if (flag4) {
                            if (!flag2) {
                                this.placeHatDropBlock(level, random, mutableBlockPos, config.hatState, isNetherWartBlock);
                            }
                        } else if (flag2) {
                            this.placeHatBlock(level, random, config, mutableBlockPos, 0.1F, 0.2F, isNetherWartBlock ? 0.1F : 0.0F);
                        } else if (flag3) {
                            this.placeHatBlock(level, random, config, mutableBlockPos, 0.01F, 0.7F, isNetherWartBlock ? 0.083F : 0.0F);
                        } else {
                            this.placeHatBlock(level, random, config, mutableBlockPos, 5.0E-4F, 0.98F, isNetherWartBlock ? 0.07F : 0.0F);
                        }
                    }
                }
            }
        }
    }

    private void placeHatBlock(
        LevelAccessor level,
        RandomSource random,
        HugeFungusConfiguration config,
        BlockPos.MutableBlockPos pos,
        float decorationChance,
        float hatChance,
        float weepingVineChance
    ) {
        if (random.nextFloat() < decorationChance) {
            this.setBlock(level, pos, config.decorState);
        } else if (random.nextFloat() < hatChance) {
            this.setBlock(level, pos, config.hatState);
            if (random.nextFloat() < weepingVineChance) {
                tryPlaceWeepingVines(pos, level, random);
            }
        }
    }

    private void placeHatDropBlock(LevelAccessor level, RandomSource random, BlockPos pos, BlockState state, boolean weepingVines) {
        if (level.getBlockState(pos.below()).is(state.getBlock())) {
            this.setBlock(level, pos, state);
        } else if (random.nextFloat() < 0.15) {
            this.setBlock(level, pos, state);
            if (weepingVines && random.nextInt(11) == 0) {
                tryPlaceWeepingVines(pos, level, random);
            }
        }
    }

    private static void tryPlaceWeepingVines(BlockPos pos, LevelAccessor level, RandomSource random) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable().move(Direction.DOWN);
        if (level.isEmptyBlock(mutableBlockPos)) {
            int randomInt = Mth.nextInt(random, 1, 5);
            if (random.nextInt(7) == 0) {
                randomInt *= 2;
            }

            int i = 23;
            int i1 = 25;
            WeepingVinesFeature.placeWeepingVinesColumn(level, random, mutableBlockPos, randomInt, 23, 25);
        }
    }
}
