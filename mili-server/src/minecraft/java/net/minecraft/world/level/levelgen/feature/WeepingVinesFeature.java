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
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class WeepingVinesFeature extends Feature<NoneFeatureConfiguration> {
    private static final Direction[] DIRECTIONS = Direction.values();

    public WeepingVinesFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        RandomSource randomSource = context.random();
        if (!worldGenLevel.isEmptyBlock(blockPos)) {
            return false;
        } else {
            BlockState blockState = worldGenLevel.getBlockState(blockPos.above());
            if (!blockState.is(Blocks.NETHERRACK) && !blockState.is(Blocks.NETHER_WART_BLOCK)) {
                return false;
            } else {
                this.placeRoofNetherWart(worldGenLevel, randomSource, blockPos);
                this.placeRoofWeepingVines(worldGenLevel, randomSource, blockPos);
                return true;
            }
        }
    }

    private void placeRoofNetherWart(LevelAccessor level, RandomSource random, BlockPos pos) {
        level.setBlock(pos, Blocks.NETHER_WART_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos mutableBlockPos1 = new BlockPos.MutableBlockPos();

        for (int i = 0; i < 200; i++) {
            mutableBlockPos.setWithOffset(
                pos, random.nextInt(6) - random.nextInt(6), random.nextInt(2) - random.nextInt(5), random.nextInt(6) - random.nextInt(6)
            );
            if (level.isEmptyBlock(mutableBlockPos)) {
                int i1 = 0;

                for (Direction direction : DIRECTIONS) {
                    BlockState blockState = level.getBlockState(mutableBlockPos1.setWithOffset(mutableBlockPos, direction));
                    if (blockState.is(Blocks.NETHERRACK) || blockState.is(Blocks.NETHER_WART_BLOCK)) {
                        i1++;
                    }

                    if (i1 > 1) {
                        break;
                    }
                }

                if (i1 == 1) {
                    level.setBlock(mutableBlockPos, Blocks.NETHER_WART_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private void placeRoofWeepingVines(LevelAccessor level, RandomSource random, BlockPos pos) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < 100; i++) {
            mutableBlockPos.setWithOffset(
                pos, random.nextInt(8) - random.nextInt(8), random.nextInt(2) - random.nextInt(7), random.nextInt(8) - random.nextInt(8)
            );
            if (level.isEmptyBlock(mutableBlockPos)) {
                BlockState blockState = level.getBlockState(mutableBlockPos.above());
                if (blockState.is(Blocks.NETHERRACK) || blockState.is(Blocks.NETHER_WART_BLOCK)) {
                    int randomInt = Mth.nextInt(random, 1, 8);
                    if (random.nextInt(6) == 0) {
                        randomInt *= 2;
                    }

                    if (random.nextInt(5) == 0) {
                        randomInt = 1;
                    }

                    int i1 = 17;
                    int i2 = 25;
                    placeWeepingVinesColumn(level, random, mutableBlockPos, randomInt, 17, 25);
                }
            }
        }
    }

    public static void placeWeepingVinesColumn(LevelAccessor level, RandomSource random, BlockPos.MutableBlockPos pos, int height, int minAge, int maxAge) {
        for (int i = 0; i <= height; i++) {
            if (level.isEmptyBlock(pos)) {
                if (i == height || !level.isEmptyBlock(pos.below())) {
                    level.setBlock(
                        pos,
                        Blocks.WEEPING_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, Mth.nextInt(random, minAge, maxAge)),
                        Block.UPDATE_CLIENTS
                    );
                    break;
                }

                level.setBlock(pos, Blocks.WEEPING_VINES_PLANT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }

            pos.move(Direction.DOWN);
        }
    }
}
