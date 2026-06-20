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
import net.minecraft.world.level.levelgen.feature.configurations.TwistingVinesConfig;

public class TwistingVinesFeature extends Feature<TwistingVinesConfig> {
    public TwistingVinesFeature(Codec<TwistingVinesConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<TwistingVinesConfig> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        if (isInvalidPlacementLocation(worldGenLevel, blockPos)) {
            return false;
        } else {
            RandomSource randomSource = context.random();
            TwistingVinesConfig twistingVinesConfig = context.config();
            int spreadWidth = twistingVinesConfig.spreadWidth();
            int spreadHeight = twistingVinesConfig.spreadHeight();
            int maxHeight = twistingVinesConfig.maxHeight();
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int i = 0; i < spreadWidth * spreadWidth; i++) {
                mutableBlockPos.set(blockPos)
                    .move(
                        Mth.nextInt(randomSource, -spreadWidth, spreadWidth),
                        Mth.nextInt(randomSource, -spreadHeight, spreadHeight),
                        Mth.nextInt(randomSource, -spreadWidth, spreadWidth)
                    );
                if (findFirstAirBlockAboveGround(worldGenLevel, mutableBlockPos) && !isInvalidPlacementLocation(worldGenLevel, mutableBlockPos)) {
                    int randomInt = Mth.nextInt(randomSource, 1, maxHeight);
                    if (randomSource.nextInt(6) == 0) {
                        randomInt *= 2;
                    }

                    if (randomSource.nextInt(5) == 0) {
                        randomInt = 1;
                    }

                    int i1 = 17;
                    int i2 = 25;
                    placeWeepingVinesColumn(worldGenLevel, randomSource, mutableBlockPos, randomInt, 17, 25);
                }
            }

            return true;
        }
    }

    private static boolean findFirstAirBlockAboveGround(LevelAccessor level, BlockPos.MutableBlockPos pos) {
        do {
            pos.move(0, -1, 0);
            if (level.isOutsideBuildHeight(pos)) {
                return false;
            }
        } while (level.getBlockState(pos).isAir());

        pos.move(0, 1, 0);
        return true;
    }

    public static void placeWeepingVinesColumn(LevelAccessor level, RandomSource random, BlockPos.MutableBlockPos pos, int length, int minAge, int maxAge) {
        for (int i = 1; i <= length; i++) {
            if (level.isEmptyBlock(pos)) {
                if (i == length || !level.isEmptyBlock(pos.above())) {
                    level.setBlock(
                        pos,
                        Blocks.TWISTING_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, Mth.nextInt(random, minAge, maxAge)),
                        Block.UPDATE_CLIENTS
                    );
                    break;
                }

                level.setBlock(pos, Blocks.TWISTING_VINES_PLANT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }

            pos.move(Direction.UP);
        }
    }

    private static boolean isInvalidPlacementLocation(LevelAccessor level, BlockPos pos) {
        if (!level.isEmptyBlock(pos)) {
            return true;
        } else {
            BlockState blockState = level.getBlockState(pos.below());
            return !blockState.is(Blocks.NETHERRACK) && !blockState.is(Blocks.WARPED_NYLIUM) && !blockState.is(Blocks.WARPED_WART_BLOCK);
        }
    }
}
