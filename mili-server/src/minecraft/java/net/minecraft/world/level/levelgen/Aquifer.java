package net.minecraft.world.level.levelgen;

import java.util.Arrays;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.jspecify.annotations.Nullable;

public interface Aquifer {
    static Aquifer create(
        NoiseChunk chunk,
        ChunkPos chunkPos,
        NoiseRouter noiseRouter,
        PositionalRandomFactory positionalRandomFactory,
        int minY,
        int height,
        Aquifer.FluidPicker globalFluidPicker
    ) {
        return new Aquifer.NoiseBasedAquifer(chunk, chunkPos, noiseRouter, positionalRandomFactory, minY, height, globalFluidPicker);
    }

    static Aquifer createDisabled(final Aquifer.FluidPicker defaultFluid) {
        return new Aquifer() {
            @Override
            public @Nullable BlockState computeSubstance(DensityFunction.FunctionContext context, double substance) {
                return substance > 0.0 ? null : defaultFluid.computeFluid(context.blockX(), context.blockY(), context.blockZ()).at(context.blockY());
            }

            @Override
            public boolean shouldScheduleFluidUpdate() {
                return false;
            }
        };
    }

    @Nullable BlockState computeSubstance(DensityFunction.FunctionContext context, double substance);

    boolean shouldScheduleFluidUpdate();

    public interface FluidPicker {
        Aquifer.FluidStatus computeFluid(int x, int y, int z);
    }

    public record FluidStatus(int fluidLevel, BlockState fluidType) {
        public BlockState at(int y) {
            return y < this.fluidLevel ? this.fluidType : Blocks.AIR.defaultBlockState();
        }
    }

    public static class NoiseBasedAquifer implements Aquifer {
        private static final int X_RANGE = 10;
        private static final int Y_RANGE = 9;
        private static final int Z_RANGE = 10;
        private static final int X_SEPARATION = 6;
        private static final int Y_SEPARATION = 3;
        private static final int Z_SEPARATION = 6;
        private static final int X_SPACING = 16;
        private static final int Y_SPACING = 12;
        private static final int Z_SPACING = 16;
        private static final int X_SPACING_SHIFT = 4;
        private static final int Z_SPACING_SHIFT = 4;
        private static final int MAX_REASONABLE_DISTANCE_TO_AQUIFER_CENTER = 11;
        private static final double FLOWING_UPDATE_SIMULARITY = similarity(Mth.square(10), Mth.square(12));
        private static final int SAMPLE_OFFSET_X = -5;
        private static final int SAMPLE_OFFSET_Y = 1;
        private static final int SAMPLE_OFFSET_Z = -5;
        private static final int MIN_CELL_SAMPLE_X = 0;
        private static final int MIN_CELL_SAMPLE_Y = -1;
        private static final int MIN_CELL_SAMPLE_Z = 0;
        private static final int MAX_CELL_SAMPLE_X = 1;
        private static final int MAX_CELL_SAMPLE_Y = 1;
        private static final int MAX_CELL_SAMPLE_Z = 1;
        private final NoiseChunk noiseChunk;
        private final DensityFunction barrierNoise;
        private final DensityFunction fluidLevelFloodednessNoise;
        private final DensityFunction fluidLevelSpreadNoise;
        private final DensityFunction lavaNoise;
        private final PositionalRandomFactory positionalRandomFactory;
        private final Aquifer.@Nullable FluidStatus[] aquiferCache;
        private final long[] aquiferLocationCache;
        private final Aquifer.FluidPicker globalFluidPicker;
        private final DensityFunction erosion;
        private final DensityFunction depth;
        private boolean shouldScheduleFluidUpdate;
        private final int skipSamplingAboveY;
        private final int minGridX;
        private final int minGridY;
        private final int minGridZ;
        private final int gridSizeX;
        private final int gridSizeZ;
        private static final int[][] SURFACE_SAMPLING_OFFSETS_IN_CHUNKS = new int[][]{
            {0, 0}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {-3, 0}, {-2, 0}, {-1, 0}, {1, 0}, {-2, 1}, {-1, 1}, {0, 1}, {1, 1}
        };

        NoiseBasedAquifer(
            NoiseChunk noiseChunk,
            ChunkPos chunkPos,
            NoiseRouter noiseRouter,
            PositionalRandomFactory positionalRandomFactory,
            int minY,
            int height,
            Aquifer.FluidPicker globalFluidPicker
        ) {
            this.noiseChunk = noiseChunk;
            this.barrierNoise = noiseRouter.barrierNoise();
            this.fluidLevelFloodednessNoise = noiseRouter.fluidLevelFloodednessNoise();
            this.fluidLevelSpreadNoise = noiseRouter.fluidLevelSpreadNoise();
            this.lavaNoise = noiseRouter.lavaNoise();
            this.erosion = noiseRouter.erosion();
            this.depth = noiseRouter.depth();
            this.positionalRandomFactory = positionalRandomFactory;
            this.minGridX = gridX(chunkPos.getMinBlockX() + -5) + 0;
            this.globalFluidPicker = globalFluidPicker;
            int i = gridX(chunkPos.getMaxBlockX() + -5) + 1;
            this.gridSizeX = i - this.minGridX + 1;
            this.minGridY = gridY(minY + 1) + -1;
            int i1 = gridY(minY + height + 1) + 1;
            int i2 = i1 - this.minGridY + 1;
            this.minGridZ = gridZ(chunkPos.getMinBlockZ() + -5) + 0;
            int i3 = gridZ(chunkPos.getMaxBlockZ() + -5) + 1;
            this.gridSizeZ = i3 - this.minGridZ + 1;
            int i4 = this.gridSizeX * i2 * this.gridSizeZ;
            this.aquiferCache = new Aquifer.FluidStatus[i4];
            this.aquiferLocationCache = new long[i4];
            Arrays.fill(this.aquiferLocationCache, Long.MAX_VALUE);
            int i5 = this.adjustSurfaceLevel(
                noiseChunk.maxPreliminarySurfaceLevel(fromGridX(this.minGridX, 0), fromGridZ(this.minGridZ, 0), fromGridX(i, 9), fromGridZ(i3, 9))
            );
            int i6 = gridY(i5 + 12) - -1;
            this.skipSamplingAboveY = fromGridY(i6, 11) - 1;
        }

        private int getIndex(int gridX, int gridY, int gridZ) {
            int i = gridX - this.minGridX;
            int i1 = gridY - this.minGridY;
            int i2 = gridZ - this.minGridZ;
            return (i1 * this.gridSizeZ + i2) * this.gridSizeX + i;
        }

        @Override
        public @Nullable BlockState computeSubstance(DensityFunction.FunctionContext context, double substance) {
            if (substance > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            } else {
                int i = context.blockX();
                int i1 = context.blockY();
                int i2 = context.blockZ();
                Aquifer.FluidStatus fluidStatus = this.globalFluidPicker.computeFluid(i, i1, i2);
                if (i1 > this.skipSamplingAboveY) {
                    this.shouldScheduleFluidUpdate = false;
                    return fluidStatus.at(i1);
                } else if (fluidStatus.at(i1).is(Blocks.LAVA)) {
                    this.shouldScheduleFluidUpdate = false;
                    return SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? Blocks.AIR.defaultBlockState() : Blocks.LAVA.defaultBlockState();
                } else {
                    int i3 = gridX(i + -5);
                    int i4 = gridY(i1 + 1);
                    int i5 = gridZ(i2 + -5);
                    int i6 = Integer.MAX_VALUE;
                    int i7 = Integer.MAX_VALUE;
                    int i8 = Integer.MAX_VALUE;
                    int i9 = Integer.MAX_VALUE;
                    int i10 = 0;
                    int i11 = 0;
                    int i12 = 0;
                    int i13 = 0;

                    for (int i14 = 0; i14 <= 1; i14++) {
                        for (int i15 = -1; i15 <= 1; i15++) {
                            for (int i16 = 0; i16 <= 1; i16++) {
                                int i17 = i3 + i14;
                                int i18 = i4 + i15;
                                int i19 = i5 + i16;
                                int index = this.getIndex(i17, i18, i19);
                                long l = this.aquiferLocationCache[index];
                                long l1;
                                if (l != Long.MAX_VALUE) {
                                    l1 = l;
                                } else {
                                    RandomSource randomSource = this.positionalRandomFactory.at(i17, i18, i19);
                                    l1 = BlockPos.asLong(
                                        fromGridX(i17, randomSource.nextInt(10)),
                                        fromGridY(i18, randomSource.nextInt(9)),
                                        fromGridZ(i19, randomSource.nextInt(10))
                                    );
                                    this.aquiferLocationCache[index] = l1;
                                }

                                int i20 = BlockPos.getX(l1) - i;
                                int i21 = BlockPos.getY(l1) - i1;
                                int i22 = BlockPos.getZ(l1) - i2;
                                int i23 = i20 * i20 + i21 * i21 + i22 * i22;
                                if (i6 >= i23) {
                                    i13 = i12;
                                    i12 = i11;
                                    i11 = i10;
                                    i10 = index;
                                    i9 = i8;
                                    i8 = i7;
                                    i7 = i6;
                                    i6 = i23;
                                } else if (i7 >= i23) {
                                    i13 = i12;
                                    i12 = i11;
                                    i11 = index;
                                    i9 = i8;
                                    i8 = i7;
                                    i7 = i23;
                                } else if (i8 >= i23) {
                                    i13 = i12;
                                    i12 = index;
                                    i9 = i8;
                                    i8 = i23;
                                } else if (i9 >= i23) {
                                    i13 = index;
                                    i9 = i23;
                                }
                            }
                        }
                    }

                    Aquifer.FluidStatus aquiferStatus = this.getAquiferStatus(i10);
                    double d = similarity(i6, i7);
                    BlockState blockState = aquiferStatus.at(i1);
                    BlockState blockState1 = SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? Blocks.AIR.defaultBlockState() : blockState;
                    if (d <= 0.0) {
                        if (d >= FLOWING_UPDATE_SIMULARITY) {
                            Aquifer.FluidStatus aquiferStatus1 = this.getAquiferStatus(i11);
                            this.shouldScheduleFluidUpdate = !aquiferStatus.equals(aquiferStatus1);
                        } else {
                            this.shouldScheduleFluidUpdate = false;
                        }

                        return blockState1;
                    } else if (blockState.is(Blocks.WATER) && this.globalFluidPicker.computeFluid(i, i1 - 1, i2).at(i1 - 1).is(Blocks.LAVA)) {
                        this.shouldScheduleFluidUpdate = true;
                        return blockState1;
                    } else {
                        MutableDouble mutableDouble = new MutableDouble(Double.NaN);
                        Aquifer.FluidStatus aquiferStatus2 = this.getAquiferStatus(i11);
                        double d1 = d * this.calculatePressure(context, mutableDouble, aquiferStatus, aquiferStatus2);
                        if (substance + d1 > 0.0) {
                            this.shouldScheduleFluidUpdate = false;
                            return null;
                        } else {
                            Aquifer.FluidStatus aquiferStatus3 = this.getAquiferStatus(i12);
                            double d2 = similarity(i6, i8);
                            if (d2 > 0.0) {
                                double d3 = d * d2 * this.calculatePressure(context, mutableDouble, aquiferStatus, aquiferStatus3);
                                if (substance + d3 > 0.0) {
                                    this.shouldScheduleFluidUpdate = false;
                                    return null;
                                }
                            }

                            double d3 = similarity(i7, i8);
                            if (d3 > 0.0) {
                                double d4 = d * d3 * this.calculatePressure(context, mutableDouble, aquiferStatus2, aquiferStatus3);
                                if (substance + d4 > 0.0) {
                                    this.shouldScheduleFluidUpdate = false;
                                    return null;
                                }
                            }

                            boolean flag = !aquiferStatus.equals(aquiferStatus2);
                            boolean flag1 = d3 >= FLOWING_UPDATE_SIMULARITY && !aquiferStatus2.equals(aquiferStatus3);
                            boolean flag2 = d2 >= FLOWING_UPDATE_SIMULARITY && !aquiferStatus.equals(aquiferStatus3);
                            if (!flag && !flag1 && !flag2) {
                                this.shouldScheduleFluidUpdate = d2 >= FLOWING_UPDATE_SIMULARITY
                                    && similarity(i6, i9) >= FLOWING_UPDATE_SIMULARITY
                                    && !aquiferStatus.equals(this.getAquiferStatus(i13));
                            } else {
                                this.shouldScheduleFluidUpdate = true;
                            }

                            return blockState1;
                        }
                    }
                }
            }
        }

        @Override
        public boolean shouldScheduleFluidUpdate() {
            return this.shouldScheduleFluidUpdate;
        }

        private static double similarity(int firstDistance, int secondDistance) {
            double d = 25.0;
            return 1.0 - (secondDistance - firstDistance) / 25.0;
        }

        private double calculatePressure(
            DensityFunction.FunctionContext context, MutableDouble substance, Aquifer.FluidStatus firstFluid, Aquifer.FluidStatus secondFluid
        ) {
            int i = context.blockY();
            BlockState blockState = firstFluid.at(i);
            BlockState blockState1 = secondFluid.at(i);
            if ((!blockState.is(Blocks.LAVA) || !blockState1.is(Blocks.WATER)) && (!blockState.is(Blocks.WATER) || !blockState1.is(Blocks.LAVA))) {
                int abs = Math.abs(firstFluid.fluidLevel - secondFluid.fluidLevel);
                if (abs == 0) {
                    return 0.0;
                } else {
                    double d = 0.5 * (firstFluid.fluidLevel + secondFluid.fluidLevel);
                    double d1 = i + 0.5 - d;
                    double d2 = abs / 2.0;
                    double d3 = 0.0;
                    double d4 = 2.5;
                    double d5 = 1.5;
                    double d6 = 3.0;
                    double d7 = 10.0;
                    double d8 = 3.0;
                    double d9 = d2 - Math.abs(d1);
                    double d11;
                    if (d1 > 0.0) {
                        double d10 = 0.0 + d9;
                        if (d10 > 0.0) {
                            d11 = d10 / 1.5;
                        } else {
                            d11 = d10 / 2.5;
                        }
                    } else {
                        double d10 = 3.0 + d9;
                        if (d10 > 0.0) {
                            d11 = d10 / 3.0;
                        } else {
                            d11 = d10 / 10.0;
                        }
                    }

                    double d10x = 2.0;
                    double d12;
                    if (!(d11 < -2.0) && !(d11 > 2.0)) {
                        double d13 = substance.doubleValue();
                        if (Double.isNaN(d13)) {
                            double d14 = this.barrierNoise.compute(context);
                            substance.setValue(d14);
                            d12 = d14;
                        } else {
                            d12 = d13;
                        }
                    } else {
                        d12 = 0.0;
                    }

                    return 2.0 * (d12 + d11);
                }
            } else {
                return 2.0;
            }
        }

        private static int gridX(int x) {
            return x >> 4;
        }

        private static int fromGridX(int gridX, int offset) {
            return (gridX << 4) + offset;
        }

        private static int gridY(int y) {
            return Math.floorDiv(y, 12);
        }

        private static int fromGridY(int gridY, int offset) {
            return gridY * 12 + offset;
        }

        private static int gridZ(int z) {
            return z >> 4;
        }

        private static int fromGridZ(int gridZ, int offset) {
            return (gridZ << 4) + offset;
        }

        private Aquifer.FluidStatus getAquiferStatus(int packedPos) {
            Aquifer.FluidStatus fluidStatus = this.aquiferCache[packedPos];
            if (fluidStatus != null) {
                return fluidStatus;
            } else {
                long l = this.aquiferLocationCache[packedPos];
                Aquifer.FluidStatus fluidStatus1 = this.computeFluid(BlockPos.getX(l), BlockPos.getY(l), BlockPos.getZ(l));
                this.aquiferCache[packedPos] = fluidStatus1;
                return fluidStatus1;
            }
        }

        private Aquifer.FluidStatus computeFluid(int x, int y, int z) {
            Aquifer.FluidStatus fluidStatus = this.globalFluidPicker.computeFluid(x, y, z);
            int i = Integer.MAX_VALUE;
            int i1 = y + 12;
            int i2 = y - 12;
            boolean flag = false;

            for (int[] ints : SURFACE_SAMPLING_OFFSETS_IN_CHUNKS) {
                int i3 = x + SectionPos.sectionToBlockCoord(ints[0]);
                int i4 = z + SectionPos.sectionToBlockCoord(ints[1]);
                int i5 = this.noiseChunk.preliminarySurfaceLevel(i3, i4);
                int i6 = this.adjustSurfaceLevel(i5);
                boolean flag1 = ints[0] == 0 && ints[1] == 0;
                if (flag1 && i2 > i6) {
                    return fluidStatus;
                }

                boolean flag2 = i1 > i6;
                if (flag2 || flag1) {
                    Aquifer.FluidStatus fluidStatus1 = this.globalFluidPicker.computeFluid(i3, i6, i4);
                    if (!fluidStatus1.at(i6).isAir()) {
                        if (flag1) {
                            flag = true;
                        }

                        if (flag2) {
                            return fluidStatus1;
                        }
                    }
                }

                i = Math.min(i, i5);
            }

            int i7 = this.computeSurfaceLevel(x, y, z, fluidStatus, i, flag);
            return new Aquifer.FluidStatus(i7, this.computeFluidType(x, y, z, fluidStatus, i7));
        }

        private int adjustSurfaceLevel(int level) {
            return level + 8;
        }

        private int computeSurfaceLevel(int x, int y, int z, Aquifer.FluidStatus fluidStatus, int maxSurfaceLevel, boolean fluidPresent) {
            DensityFunction.SinglePointContext singlePointContext = new DensityFunction.SinglePointContext(x, y, z);
            double d;
            double d1;
            if (OverworldBiomeBuilder.isDeepDarkRegion(this.erosion, this.depth, singlePointContext)) {
                d = -1.0;
                d1 = -1.0;
            } else {
                int i = maxSurfaceLevel + 8 - y;
                int i1 = 64;
                double d2 = fluidPresent ? Mth.clampedMap((double)i, 0.0, 64.0, 1.0, 0.0) : 0.0;
                double d3 = Mth.clamp(this.fluidLevelFloodednessNoise.compute(singlePointContext), -1.0, 1.0);
                double d4 = Mth.map(d2, 1.0, 0.0, -0.3, 0.8);
                double d5 = Mth.map(d2, 1.0, 0.0, -0.8, 0.4);
                d = d3 - d5;
                d1 = d3 - d4;
            }

            int i;
            if (d1 > 0.0) {
                i = fluidStatus.fluidLevel;
            } else if (d > 0.0) {
                i = this.computeRandomizedFluidSurfaceLevel(x, y, z, maxSurfaceLevel);
            } else {
                i = DimensionType.WAY_BELOW_MIN_Y;
            }

            return i;
        }

        private int computeRandomizedFluidSurfaceLevel(int x, int y, int z, int maxSurfaceLevel) {
            int i = 16;
            int i1 = 40;
            int i2 = Math.floorDiv(x, 16);
            int i3 = Math.floorDiv(y, 40);
            int i4 = Math.floorDiv(z, 16);
            int i5 = i3 * 40 + 20;
            int i6 = 10;
            double d = this.fluidLevelSpreadNoise.compute(new DensityFunction.SinglePointContext(i2, i3, i4)) * 10.0;
            int i7 = Mth.quantize(d, 3);
            int i8 = i5 + i7;
            return Math.min(maxSurfaceLevel, i8);
        }

        private BlockState computeFluidType(int x, int y, int z, Aquifer.FluidStatus fluidStatus, int surfaceLevel) {
            BlockState blockState = fluidStatus.fluidType;
            if (surfaceLevel <= -10 && surfaceLevel != DimensionType.WAY_BELOW_MIN_Y && fluidStatus.fluidType != Blocks.LAVA.defaultBlockState()) {
                int i = 64;
                int i1 = 40;
                int i2 = Math.floorDiv(x, 64);
                int i3 = Math.floorDiv(y, 40);
                int i4 = Math.floorDiv(z, 64);
                double d = this.lavaNoise.compute(new DensityFunction.SinglePointContext(i2, i3, i4));
                if (Math.abs(d) > 0.3) {
                    blockState = Blocks.LAVA.defaultBlockState();
                }
            }

            return blockState;
        }
    }
}
