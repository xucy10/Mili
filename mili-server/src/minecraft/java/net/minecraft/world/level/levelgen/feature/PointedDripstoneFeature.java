package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.feature.configurations.PointedDripstoneConfiguration;

public class PointedDripstoneFeature extends Feature<PointedDripstoneConfiguration> {
    public PointedDripstoneFeature(Codec<PointedDripstoneConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<PointedDripstoneConfiguration> context) {
        LevelAccessor levelAccessor = context.level();
        BlockPos blockPos = context.origin();
        RandomSource randomSource = context.random();
        PointedDripstoneConfiguration pointedDripstoneConfiguration = context.config();
        Optional<Direction> tipDirection = getTipDirection(levelAccessor, blockPos, randomSource);
        if (tipDirection.isEmpty()) {
            return false;
        } else {
            BlockPos blockPos1 = blockPos.relative(tipDirection.get().getOpposite());
            createPatchOfDripstoneBlocks(levelAccessor, randomSource, blockPos1, pointedDripstoneConfiguration);
            int i = randomSource.nextFloat() < pointedDripstoneConfiguration.chanceOfTallerDripstone
                    && DripstoneUtils.isEmptyOrWater(levelAccessor.getBlockState(blockPos.relative(tipDirection.get())))
                ? 2
                : 1;
            DripstoneUtils.growPointedDripstone(levelAccessor, blockPos, tipDirection.get(), i, false);
            return true;
        }
    }

    private static Optional<Direction> getTipDirection(LevelAccessor level, BlockPos pos, RandomSource random) {
        boolean isDripstoneBase = DripstoneUtils.isDripstoneBase(level.getBlockState(pos.above()));
        boolean isDripstoneBase1 = DripstoneUtils.isDripstoneBase(level.getBlockState(pos.below()));
        if (isDripstoneBase && isDripstoneBase1) {
            return Optional.of(random.nextBoolean() ? Direction.DOWN : Direction.UP);
        } else if (isDripstoneBase) {
            return Optional.of(Direction.DOWN);
        } else {
            return isDripstoneBase1 ? Optional.of(Direction.UP) : Optional.empty();
        }
    }

    private static void createPatchOfDripstoneBlocks(LevelAccessor level, RandomSource random, BlockPos pos, PointedDripstoneConfiguration config) {
        DripstoneUtils.placeDripstoneBlockIfPossible(level, pos);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!(random.nextFloat() > config.chanceOfDirectionalSpread)) {
                BlockPos blockPos = pos.relative(direction);
                DripstoneUtils.placeDripstoneBlockIfPossible(level, blockPos);
                if (!(random.nextFloat() > config.chanceOfSpreadRadius2)) {
                    BlockPos blockPos1 = blockPos.relative(Direction.getRandom(random));
                    DripstoneUtils.placeDripstoneBlockIfPossible(level, blockPos1);
                    if (!(random.nextFloat() > config.chanceOfSpreadRadius3)) {
                        BlockPos blockPos2 = blockPos1.relative(Direction.getRandom(random));
                        DripstoneUtils.placeDripstoneBlockIfPossible(level, blockPos2);
                    }
                }
            }
        }
    }
}
