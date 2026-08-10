package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.BitSet;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public class OreFeature extends Feature<OreConfiguration> {
    public OreFeature(Codec<OreConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<OreConfiguration> context) {
        RandomSource randomSource = context.random();
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        OreConfiguration oreConfiguration = context.config();
        float f = randomSource.nextFloat() * (float) Math.PI;
        float f1 = oreConfiguration.size / 8.0F;
        int ceil = Mth.ceil((oreConfiguration.size / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d = blockPos.getX() + Math.sin(f) * f1;
        double d1 = blockPos.getX() - Math.sin(f) * f1;
        double d2 = blockPos.getZ() + Math.cos(f) * f1;
        double d3 = blockPos.getZ() - Math.cos(f) * f1;
        int i = 2;
        double d4 = blockPos.getY() + randomSource.nextInt(3) - 2;
        double d5 = blockPos.getY() + randomSource.nextInt(3) - 2;
        int i1 = blockPos.getX() - Mth.ceil(f1) - ceil;
        int i2 = blockPos.getY() - 2 - ceil;
        int i3 = blockPos.getZ() - Mth.ceil(f1) - ceil;
        int i4 = 2 * (Mth.ceil(f1) + ceil);
        int i5 = 2 * (2 + ceil);

        for (int i6 = i1; i6 <= i1 + i4; i6++) {
            for (int i7 = i3; i7 <= i3 + i4; i7++) {
                if (i2 <= worldGenLevel.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, i6, i7)) {
                    return this.doPlace(worldGenLevel, randomSource, oreConfiguration, d, d1, d2, d3, d4, d5, i1, i2, i3, i4, i5);
                }
            }
        }

        return false;
    }

    protected boolean doPlace(
        WorldGenLevel level,
        RandomSource random,
        OreConfiguration config,
        double minX,
        double maxX,
        double minZ,
        double maxZ,
        double minY,
        double maxY,
        int x,
        int y,
        int z,
        int width,
        int height
    ) {
        int i = 0;
        BitSet bitSet = new BitSet(width * height * width);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int i1 = config.size;
        double[] doubles = new double[i1 * 4];

        for (int i2 = 0; i2 < i1; i2++) {
            float f = (float)i2 / i1;
            double d = Mth.lerp((double)f, minX, maxX);
            double d1 = Mth.lerp((double)f, minY, maxY);
            double d2 = Mth.lerp((double)f, minZ, maxZ);
            double d3 = random.nextDouble() * i1 / 16.0;
            double d4 = ((Mth.sin((float) Math.PI * f) + 1.0F) * d3 + 1.0) / 2.0;
            doubles[i2 * 4 + 0] = d;
            doubles[i2 * 4 + 1] = d1;
            doubles[i2 * 4 + 2] = d2;
            doubles[i2 * 4 + 3] = d4;
        }

        for (int i2 = 0; i2 < i1 - 1; i2++) {
            if (!(doubles[i2 * 4 + 3] <= 0.0)) {
                for (int i3 = i2 + 1; i3 < i1; i3++) {
                    if (!(doubles[i3 * 4 + 3] <= 0.0)) {
                        double d = doubles[i2 * 4 + 0] - doubles[i3 * 4 + 0];
                        double d1 = doubles[i2 * 4 + 1] - doubles[i3 * 4 + 1];
                        double d2 = doubles[i2 * 4 + 2] - doubles[i3 * 4 + 2];
                        double d3 = doubles[i2 * 4 + 3] - doubles[i3 * 4 + 3];
                        if (d3 * d3 > d * d + d1 * d1 + d2 * d2) {
                            if (d3 > 0.0) {
                                doubles[i3 * 4 + 3] = -1.0;
                            } else {
                                doubles[i2 * 4 + 3] = -1.0;
                            }
                        }
                    }
                }
            }
        }

        try (BulkSectionAccess bulkSectionAccess = new BulkSectionAccess(level)) {
            for (int i3x = 0; i3x < i1; i3x++) {
                double d = doubles[i3x * 4 + 3];
                if (!(d < 0.0)) {
                    double d1 = doubles[i3x * 4 + 0];
                    double d2 = doubles[i3x * 4 + 1];
                    double d3 = doubles[i3x * 4 + 2];
                    int max = Math.max(Mth.floor(d1 - d), x);
                    int max1 = Math.max(Mth.floor(d2 - d), y);
                    int max2 = Math.max(Mth.floor(d3 - d), z);
                    int max3 = Math.max(Mth.floor(d1 + d), max);
                    int max4 = Math.max(Mth.floor(d2 + d), max1);
                    int max5 = Math.max(Mth.floor(d3 + d), max2);

                    for (int i4 = max; i4 <= max3; i4++) {
                        double d5 = (i4 + 0.5 - d1) / d;
                        if (d5 * d5 < 1.0) {
                            for (int i5 = max1; i5 <= max4; i5++) {
                                double d6 = (i5 + 0.5 - d2) / d;
                                if (d5 * d5 + d6 * d6 < 1.0) {
                                    for (int i6 = max2; i6 <= max5; i6++) {
                                        double d7 = (i6 + 0.5 - d3) / d;
                                        if (d5 * d5 + d6 * d6 + d7 * d7 < 1.0 && !level.isOutsideBuildHeight(i5)) {
                                            int i7 = i4 - x + (i5 - y) * width + (i6 - z) * width * height;
                                            if (!bitSet.get(i7)) {
                                                bitSet.set(i7);
                                                mutableBlockPos.set(i4, i5, i6);
                                                if (level.ensureCanWrite(mutableBlockPos)) {
                                                    LevelChunkSection section = bulkSectionAccess.getSection(mutableBlockPos);
                                                    if (section != null) {
                                                        int relativeBlockPosCoord = SectionPos.sectionRelative(i4);
                                                        int relativeBlockPosCoord1 = SectionPos.sectionRelative(i5);
                                                        int relativeBlockPosCoord2 = SectionPos.sectionRelative(i6);
                                                        BlockState blockState = section.getBlockState(
                                                            relativeBlockPosCoord, relativeBlockPosCoord1, relativeBlockPosCoord2
                                                        );

                                                        for (OreConfiguration.TargetBlockState targetBlockState : config.targetStates) {
                                                            if (canPlaceOre(
                                                                blockState, bulkSectionAccess::getBlockState, random, config, targetBlockState, mutableBlockPos
                                                            )) {
                                                                section.setBlockState(
                                                                    relativeBlockPosCoord,
                                                                    relativeBlockPosCoord1,
                                                                    relativeBlockPosCoord2,
                                                                    targetBlockState.state,
                                                                    false
                                                                );
                                                                i++;
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return i > 0;
    }

    public static boolean canPlaceOre(
        BlockState state,
        Function<BlockPos, BlockState> adjacentStateAccessor,
        RandomSource random,
        OreConfiguration config,
        OreConfiguration.TargetBlockState targetState,
        BlockPos.MutableBlockPos mutablePos
    ) {
        return targetState.target.test(state, random)
            && (shouldSkipAirCheck(random, config.discardChanceOnAirExposure) || !isAdjacentToAir(adjacentStateAccessor, mutablePos));
    }

    protected static boolean shouldSkipAirCheck(RandomSource random, float chance) {
        return chance <= 0.0F || !(chance >= 1.0F) && random.nextFloat() >= chance;
    }
}
