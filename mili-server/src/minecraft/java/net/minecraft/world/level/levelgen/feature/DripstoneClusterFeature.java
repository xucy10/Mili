package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ClampedNormalFloat;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Column;
import net.minecraft.world.level.levelgen.feature.configurations.DripstoneClusterConfiguration;

public class DripstoneClusterFeature extends Feature<DripstoneClusterConfiguration> {
    public DripstoneClusterFeature(Codec<DripstoneClusterConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<DripstoneClusterConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        DripstoneClusterConfiguration dripstoneClusterConfiguration = context.config();
        RandomSource randomSource = context.random();
        if (!DripstoneUtils.isEmptyOrWater(worldGenLevel, blockPos)) {
            return false;
        } else {
            int i = dripstoneClusterConfiguration.height.sample(randomSource);
            float f = dripstoneClusterConfiguration.wetness.sample(randomSource);
            float f1 = dripstoneClusterConfiguration.density.sample(randomSource);
            int i1 = dripstoneClusterConfiguration.radius.sample(randomSource);
            int i2 = dripstoneClusterConfiguration.radius.sample(randomSource);

            for (int i3 = -i1; i3 <= i1; i3++) {
                for (int i4 = -i2; i4 <= i2; i4++) {
                    double chanceOfStalagmiteOrStalactite = this.getChanceOfStalagmiteOrStalactite(i1, i2, i3, i4, dripstoneClusterConfiguration);
                    BlockPos blockPos1 = blockPos.offset(i3, 0, i4);
                    this.placeColumn(worldGenLevel, randomSource, blockPos1, i3, i4, f, chanceOfStalagmiteOrStalactite, i, f1, dripstoneClusterConfiguration);
                }
            }

            return true;
        }
    }

    private void placeColumn(
        WorldGenLevel level,
        RandomSource random,
        BlockPos pos,
        int x,
        int z,
        float wetness,
        double chance,
        int height,
        float density,
        DripstoneClusterConfiguration config
    ) {
        Optional<Column> optional = Column.scan(
            level, pos, config.floorToCeilingSearchRange, DripstoneUtils::isEmptyOrWater, DripstoneUtils::isNeitherEmptyNorWater
        );
        if (!optional.isEmpty()) {
            OptionalInt ceiling = optional.get().getCeiling();
            OptionalInt floor = optional.get().getFloor();
            if (!ceiling.isEmpty() || !floor.isEmpty()) {
                boolean flag = random.nextFloat() < wetness;
                Column column;
                if (flag && floor.isPresent() && this.canPlacePool(level, pos.atY(floor.getAsInt()))) {
                    int asInt = floor.getAsInt();
                    column = optional.get().withFloor(OptionalInt.of(asInt - 1));
                    level.setBlock(pos.atY(asInt), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                } else {
                    column = optional.get();
                }

                OptionalInt floor1 = column.getFloor();
                boolean flag1 = random.nextDouble() < chance;
                int dripstoneHeight;
                if (ceiling.isPresent() && flag1 && !this.isLava(level, pos.atY(ceiling.getAsInt()))) {
                    int i = config.dripstoneBlockLayerThickness.sample(random);
                    this.replaceBlocksWithDripstoneBlocks(level, pos.atY(ceiling.getAsInt()), i, Direction.UP);
                    int min;
                    if (floor1.isPresent()) {
                        min = Math.min(height, ceiling.getAsInt() - floor1.getAsInt());
                    } else {
                        min = height;
                    }

                    dripstoneHeight = this.getDripstoneHeight(random, x, z, density, min, config);
                } else {
                    dripstoneHeight = 0;
                }

                boolean flag2 = random.nextDouble() < chance;
                int i;
                if (floor1.isPresent() && flag2 && !this.isLava(level, pos.atY(floor1.getAsInt()))) {
                    int i1 = config.dripstoneBlockLayerThickness.sample(random);
                    this.replaceBlocksWithDripstoneBlocks(level, pos.atY(floor1.getAsInt()), i1, Direction.DOWN);
                    if (ceiling.isPresent()) {
                        i = Math.max(
                            0,
                            dripstoneHeight
                                + Mth.randomBetweenInclusive(random, -config.maxStalagmiteStalactiteHeightDiff, config.maxStalagmiteStalactiteHeightDiff)
                        );
                    } else {
                        i = this.getDripstoneHeight(random, x, z, density, height, config);
                    }
                } else {
                    i = 0;
                }

                int i4;
                int i1;
                if (ceiling.isPresent() && floor1.isPresent() && ceiling.getAsInt() - dripstoneHeight <= floor1.getAsInt() + i) {
                    int asInt1 = floor1.getAsInt();
                    int asInt2 = ceiling.getAsInt();
                    int max = Math.max(asInt2 - dripstoneHeight, asInt1 + 1);
                    int min1 = Math.min(asInt1 + i, asInt2 - 1);
                    int i2 = Mth.randomBetweenInclusive(random, max, min1 + 1);
                    int i3 = i2 - 1;
                    i1 = asInt2 - i2;
                    i4 = i3 - asInt1;
                } else {
                    i1 = dripstoneHeight;
                    i4 = i;
                }

                boolean flag3 = random.nextBoolean() && i1 > 0 && i4 > 0 && column.getHeight().isPresent() && i1 + i4 == column.getHeight().getAsInt();
                if (ceiling.isPresent()) {
                    DripstoneUtils.growPointedDripstone(level, pos.atY(ceiling.getAsInt() - 1), Direction.DOWN, i1, flag3);
                }

                if (floor1.isPresent()) {
                    DripstoneUtils.growPointedDripstone(level, pos.atY(floor1.getAsInt() + 1), Direction.UP, i4, flag3);
                }
            }
        }
    }

    private boolean isLava(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.LAVA);
    }

    private int getDripstoneHeight(RandomSource random, int x, int z, float chance, int height, DripstoneClusterConfiguration config) {
        if (random.nextFloat() > chance) {
            return 0;
        } else {
            int i = Math.abs(x) + Math.abs(z);
            float f = (float)Mth.clampedMap((double)i, 0.0, (double)config.maxDistanceFromCenterAffectingHeightBias, height / 2.0, 0.0);
            return (int)randomBetweenBiased(random, 0.0F, height, f, config.heightDeviation);
        }
    }

    private boolean canPlacePool(WorldGenLevel level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        if (!blockState.is(Blocks.WATER) && !blockState.is(Blocks.DRIPSTONE_BLOCK) && !blockState.is(Blocks.POINTED_DRIPSTONE)) {
            if (level.getBlockState(pos.above()).getFluidState().is(FluidTags.WATER)) {
                return false;
            } else {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    if (!this.canBeAdjacentToWater(level, pos.relative(direction))) {
                        return false;
                    }
                }

                return this.canBeAdjacentToWater(level, pos.below());
            }
        } else {
            return false;
        }
    }

    private boolean canBeAdjacentToWater(LevelAccessor level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return blockState.is(BlockTags.BASE_STONE_OVERWORLD) || blockState.getFluidState().is(FluidTags.WATER);
    }

    private void replaceBlocksWithDripstoneBlocks(WorldGenLevel level, BlockPos pos, int thickness, Direction direction) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        for (int i = 0; i < thickness; i++) {
            if (!DripstoneUtils.placeDripstoneBlockIfPossible(level, mutableBlockPos)) {
                return;
            }

            mutableBlockPos.move(direction);
        }
    }

    private double getChanceOfStalagmiteOrStalactite(int xRadius, int zRadius, int x, int z, DripstoneClusterConfiguration config) {
        int i = xRadius - Math.abs(x);
        int i1 = zRadius - Math.abs(z);
        int min = Math.min(i, i1);
        return Mth.clampedMap(
            (float)min, 0.0F, (float)config.maxDistanceFromEdgeAffectingChanceOfDripstoneColumn, config.chanceOfDripstoneColumnAtMaxDistanceFromCenter, 1.0F
        );
    }

    private static float randomBetweenBiased(RandomSource random, float min, float max, float mean, float deviation) {
        return ClampedNormalFloat.sample(random, mean, deviation, min, max);
    }
}
