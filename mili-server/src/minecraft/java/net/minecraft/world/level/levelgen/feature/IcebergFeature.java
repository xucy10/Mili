package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;

public class IcebergFeature extends Feature<BlockStateConfiguration> {
    public IcebergFeature(Codec<BlockStateConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<BlockStateConfiguration> context) {
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        blockPos = new BlockPos(blockPos.getX(), context.chunkGenerator().getSeaLevel(), blockPos.getZ());
        RandomSource randomSource = context.random();
        boolean flag = randomSource.nextDouble() > 0.7;
        BlockState blockState = context.config().state;
        double d = randomSource.nextDouble() * 2.0 * Math.PI;
        int i = 11 - randomSource.nextInt(5);
        int i1 = 3 + randomSource.nextInt(3);
        boolean flag1 = randomSource.nextDouble() > 0.7;
        int i2 = 11;
        int i3 = flag1 ? randomSource.nextInt(6) + 6 : randomSource.nextInt(15) + 3;
        if (!flag1 && randomSource.nextDouble() > 0.9) {
            i3 += randomSource.nextInt(19) + 7;
        }

        int min = Math.min(i3 + randomSource.nextInt(11), 18);
        int min1 = Math.min(i3 + randomSource.nextInt(7) - randomSource.nextInt(5), 11);
        int i4 = flag1 ? i : 11;

        for (int i5 = -i4; i5 < i4; i5++) {
            for (int i6 = -i4; i6 < i4; i6++) {
                for (int i7 = 0; i7 < i3; i7++) {
                    int i8 = flag1 ? this.heightDependentRadiusEllipse(i7, i3, min1) : this.heightDependentRadiusRound(randomSource, i7, i3, min1);
                    if (flag1 || i5 < i8) {
                        this.generateIcebergBlock(worldGenLevel, randomSource, blockPos, i3, i5, i7, i6, i8, i4, flag1, i1, d, flag, blockState);
                    }
                }
            }
        }

        this.smooth(worldGenLevel, blockPos, min1, i3, flag1, i);

        for (int i5 = -i4; i5 < i4; i5++) {
            for (int i6 = -i4; i6 < i4; i6++) {
                for (int i7x = -1; i7x > -min; i7x--) {
                    int i8 = flag1 ? Mth.ceil(i4 * (1.0F - (float)Math.pow(i7x, 2.0) / (min * 8.0F))) : i4;
                    int i9 = this.heightDependentRadiusSteep(randomSource, -i7x, min, min1);
                    if (i5 < i9) {
                        this.generateIcebergBlock(worldGenLevel, randomSource, blockPos, min, i5, i7x, i6, i9, i8, flag1, i1, d, flag, blockState);
                    }
                }
            }
        }

        boolean flag2 = flag1 ? randomSource.nextDouble() > 0.1 : randomSource.nextDouble() > 0.7;
        if (flag2) {
            this.generateCutOut(randomSource, worldGenLevel, min1, i3, blockPos, flag1, i, d, i1);
        }

        return true;
    }

    private void generateCutOut(
        RandomSource random, LevelAccessor level, int majorAxis, int height, BlockPos pos, boolean elliptical, int ellipseRadius, double angle, int minorAxis
    ) {
        int i = random.nextBoolean() ? -1 : 1;
        int i1 = random.nextBoolean() ? -1 : 1;
        int randomInt = random.nextInt(Math.max(majorAxis / 2 - 2, 1));
        if (random.nextBoolean()) {
            randomInt = majorAxis / 2 + 1 - random.nextInt(Math.max(majorAxis - majorAxis / 2 - 1, 1));
        }

        int randomInt1 = random.nextInt(Math.max(majorAxis / 2 - 2, 1));
        if (random.nextBoolean()) {
            randomInt1 = majorAxis / 2 + 1 - random.nextInt(Math.max(majorAxis - majorAxis / 2 - 1, 1));
        }

        if (elliptical) {
            randomInt = randomInt1 = random.nextInt(Math.max(ellipseRadius - 5, 1));
        }

        BlockPos blockPos = new BlockPos(i * randomInt, 0, i1 * randomInt1);
        double d = elliptical ? angle + (Math.PI / 2) : random.nextDouble() * 2.0 * Math.PI;

        for (int i2 = 0; i2 < height - 3; i2++) {
            int i3 = this.heightDependentRadiusRound(random, i2, height, majorAxis);
            this.carve(i3, i2, pos, level, false, d, blockPos, ellipseRadius, minorAxis);
        }

        for (int i2 = -1; i2 > -height + random.nextInt(5); i2--) {
            int i3 = this.heightDependentRadiusSteep(random, -i2, height, majorAxis);
            this.carve(i3, i2, pos, level, true, d, blockPos, ellipseRadius, minorAxis);
        }
    }

    private void carve(
        int radius,
        int localY,
        BlockPos pos,
        LevelAccessor level,
        boolean placeWater,
        double perpendicularAngle,
        BlockPos ellipseOrigin,
        int majorRadius,
        int minorRadius
    ) {
        int i = radius + 1 + majorRadius / 3;
        int i1 = Math.min(radius - 3, 3) + minorRadius / 2 - 1;

        for (int i2 = -i; i2 < i; i2++) {
            for (int i3 = -i; i3 < i; i3++) {
                double d = this.signedDistanceEllipse(i2, i3, ellipseOrigin, i, i1, perpendicularAngle);
                if (d < 0.0) {
                    BlockPos blockPos = pos.offset(i2, localY, i3);
                    BlockState blockState = level.getBlockState(blockPos);
                    if (isIcebergState(blockState) || blockState.is(Blocks.SNOW_BLOCK)) {
                        if (placeWater) {
                            this.setBlock(level, blockPos, Blocks.WATER.defaultBlockState());
                        } else {
                            this.setBlock(level, blockPos, Blocks.AIR.defaultBlockState());
                            this.removeFloatingSnowLayer(level, blockPos);
                        }
                    }
                }
            }
        }
    }

    private void removeFloatingSnowLayer(LevelAccessor level, BlockPos pos) {
        if (level.getBlockState(pos.above()).is(Blocks.SNOW)) {
            this.setBlock(level, pos.above(), Blocks.AIR.defaultBlockState());
        }
    }

    private void generateIcebergBlock(
        LevelAccessor level,
        RandomSource random,
        BlockPos pos,
        int height,
        int localX,
        int localY,
        int localZ,
        int radius,
        int majorRadius,
        boolean elliptical,
        int minorRadius,
        double angle,
        boolean placeSnow,
        BlockState state
    ) {
        double d = elliptical
            ? this.signedDistanceEllipse(localX, localZ, BlockPos.ZERO, majorRadius, this.getEllipseC(localY, height, minorRadius), angle)
            : this.signedDistanceCircle(localX, localZ, BlockPos.ZERO, radius, random);
        if (d < 0.0) {
            BlockPos blockPos = pos.offset(localX, localY, localZ);
            double d1 = elliptical ? -0.5 : -6 - random.nextInt(3);
            if (d > d1 && random.nextDouble() > 0.9) {
                return;
            }

            this.setIcebergBlock(blockPos, level, random, height - localY, height, elliptical, placeSnow, state);
        }
    }

    private void setIcebergBlock(
        BlockPos pos, LevelAccessor level, RandomSource random, int heightRemaining, int height, boolean elliptical, boolean placeSnow, BlockState state
    ) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.isAir() || blockState.is(Blocks.SNOW_BLOCK) || blockState.is(Blocks.ICE) || blockState.is(Blocks.WATER)) {
            boolean flag = !elliptical || random.nextDouble() > 0.05;
            int i = elliptical ? 3 : 2;
            if (placeSnow && !blockState.is(Blocks.WATER) && heightRemaining <= random.nextInt(Math.max(1, height / i)) + height * 0.6 && flag) {
                this.setBlock(level, pos, Blocks.SNOW_BLOCK.defaultBlockState());
            } else {
                this.setBlock(level, pos, state);
            }
        }
    }

    private int getEllipseC(int y, int height, int minorAxis) {
        int i = minorAxis;
        if (y > 0 && height - y <= 3) {
            i = minorAxis - (4 - (height - y));
        }

        return i;
    }

    private double signedDistanceCircle(int x, int z, BlockPos center, int radius, RandomSource random) {
        float f = 10.0F * Mth.clamp(random.nextFloat(), 0.2F, 0.8F) / radius;
        return f + Math.pow(x - center.getX(), 2.0) + Math.pow(z - center.getZ(), 2.0) - Math.pow(radius, 2.0);
    }

    private double signedDistanceEllipse(int x, int z, BlockPos center, int majorRadius, int minorRadius, double angle) {
        return Math.pow(((x - center.getX()) * Math.cos(angle) - (z - center.getZ()) * Math.sin(angle)) / majorRadius, 2.0)
            + Math.pow(((x - center.getX()) * Math.sin(angle) + (z - center.getZ()) * Math.cos(angle)) / minorRadius, 2.0)
            - 1.0;
    }

    private int heightDependentRadiusRound(RandomSource random, int y, int height, int majorAxis) {
        float f = 3.5F - random.nextFloat();
        float f1 = (1.0F - (float)Math.pow(y, 2.0) / (height * f)) * majorAxis;
        if (height > 15 + random.nextInt(5)) {
            int i = y < 3 + random.nextInt(6) ? y / 2 : y;
            f1 = (1.0F - i / (height * f * 0.4F)) * majorAxis;
        }

        return Mth.ceil(f1 / 2.0F);
    }

    private int heightDependentRadiusEllipse(int y, int height, int maxRadius) {
        float f = 1.0F;
        float f1 = (1.0F - (float)Math.pow(y, 2.0) / (height * 1.0F)) * maxRadius;
        return Mth.ceil(f1 / 2.0F);
    }

    private int heightDependentRadiusSteep(RandomSource random, int y, int height, int maxRadius) {
        float f = 1.0F + random.nextFloat() / 2.0F;
        float f1 = (1.0F - y / (height * f)) * maxRadius;
        return Mth.ceil(f1 / 2.0F);
    }

    private static boolean isIcebergState(BlockState state) {
        return state.is(Blocks.PACKED_ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.BLUE_ICE);
    }

    private boolean belowIsAir(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.below()).isAir();
    }

    private void smooth(LevelAccessor level, BlockPos pos, int majorRadius, int height, boolean elliptical, int minorRadius) {
        int i = elliptical ? minorRadius : majorRadius / 2;

        for (int i1 = -i; i1 <= i; i1++) {
            for (int i2 = -i; i2 <= i; i2++) {
                for (int i3 = 0; i3 <= height; i3++) {
                    BlockPos blockPos = pos.offset(i1, i3, i2);
                    BlockState blockState = level.getBlockState(blockPos);
                    if (isIcebergState(blockState) || blockState.is(Blocks.SNOW)) {
                        if (this.belowIsAir(level, blockPos)) {
                            this.setBlock(level, blockPos, Blocks.AIR.defaultBlockState());
                            this.setBlock(level, blockPos.above(), Blocks.AIR.defaultBlockState());
                        } else if (isIcebergState(blockState)) {
                            BlockState[] blockStates = new BlockState[]{
                                level.getBlockState(blockPos.west()),
                                level.getBlockState(blockPos.east()),
                                level.getBlockState(blockPos.north()),
                                level.getBlockState(blockPos.south())
                            };
                            int i4 = 0;

                            for (BlockState blockState1 : blockStates) {
                                if (!isIcebergState(blockState1)) {
                                    i4++;
                                }
                            }

                            if (i4 >= 3) {
                                this.setBlock(level, blockPos, Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                }
            }
        }
    }
}
