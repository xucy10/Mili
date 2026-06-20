package net.minecraft.world.level.levelgen.feature;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.GeodeBlockSettings;
import net.minecraft.world.level.levelgen.GeodeCrackSettings;
import net.minecraft.world.level.levelgen.GeodeLayerSettings;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.configurations.GeodeConfiguration;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.material.FluidState;

public class GeodeFeature extends Feature<GeodeConfiguration> {
    private static final Direction[] DIRECTIONS = Direction.values();

    public GeodeFeature(Codec<GeodeConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<GeodeConfiguration> context) {
        GeodeConfiguration geodeConfiguration = context.config();
        RandomSource randomSource = context.random();
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        int i = geodeConfiguration.minGenOffset;
        int i1 = geodeConfiguration.maxGenOffset;
        List<Pair<BlockPos, Integer>> list = Lists.newLinkedList();
        int i2 = geodeConfiguration.distributionPoints.sample(randomSource);
        // Leaf start - Matter - Secure Seed
        WorldgenRandom worldgenRandom = me.earthme.luminol.config.modules.function.SecureSeedConfig.enabled
                ? new su.plo.matter.WorldgenCryptoRandom(0, 0, su.plo.matter.Globals.Salt.GEODE_FEATURE, 0)
                : new WorldgenRandom(new LegacyRandomSource(worldGenLevel.getSeed()));
        // Leaf end - Matter - Secure Seed
        NormalNoise normalNoise = NormalNoise.create(worldgenRandom, -4, 1.0);
        List<BlockPos> list1 = Lists.newLinkedList();
        double d = (double)i2 / geodeConfiguration.outerWallDistance.getMaxValue();
        GeodeLayerSettings geodeLayerSettings = geodeConfiguration.geodeLayerSettings;
        GeodeBlockSettings geodeBlockSettings = geodeConfiguration.geodeBlockSettings;
        GeodeCrackSettings geodeCrackSettings = geodeConfiguration.geodeCrackSettings;
        double d1 = 1.0 / Math.sqrt(geodeLayerSettings.filling);
        double d2 = 1.0 / Math.sqrt(geodeLayerSettings.innerLayer + d);
        double d3 = 1.0 / Math.sqrt(geodeLayerSettings.middleLayer + d);
        double d4 = 1.0 / Math.sqrt(geodeLayerSettings.outerLayer + d);
        double d5 = 1.0 / Math.sqrt(geodeCrackSettings.baseCrackSize + randomSource.nextDouble() / 2.0 + (i2 > 3 ? d : 0.0));
        boolean flag = randomSource.nextFloat() < geodeCrackSettings.generateCrackChance;
        int i3 = 0;

        for (int i4 = 0; i4 < i2; i4++) {
            int i5 = geodeConfiguration.outerWallDistance.sample(randomSource);
            int i6 = geodeConfiguration.outerWallDistance.sample(randomSource);
            int i7 = geodeConfiguration.outerWallDistance.sample(randomSource);
            BlockPos blockPos1 = blockPos.offset(i5, i6, i7);
            BlockState blockState = worldGenLevel.getBlockState(blockPos1);
            if (blockState.isAir() || blockState.is(geodeBlockSettings.invalidBlocks)) {
                if (++i3 > geodeConfiguration.invalidBlocksThreshold) {
                    return false;
                }
            }

            list.add(Pair.of(blockPos1, geodeConfiguration.pointOffset.sample(randomSource)));
        }

        if (flag) {
            int i4 = randomSource.nextInt(4);
            int i5 = i2 * 2 + 1;
            if (i4 == 0) {
                list1.add(blockPos.offset(i5, 7, 0));
                list1.add(blockPos.offset(i5, 5, 0));
                list1.add(blockPos.offset(i5, 1, 0));
            } else if (i4 == 1) {
                list1.add(blockPos.offset(0, 7, i5));
                list1.add(blockPos.offset(0, 5, i5));
                list1.add(blockPos.offset(0, 1, i5));
            } else if (i4 == 2) {
                list1.add(blockPos.offset(i5, 7, i5));
                list1.add(blockPos.offset(i5, 5, i5));
                list1.add(blockPos.offset(i5, 1, i5));
            } else {
                list1.add(blockPos.offset(0, 7, 0));
                list1.add(blockPos.offset(0, 5, 0));
                list1.add(blockPos.offset(0, 1, 0));
            }
        }

        List<BlockPos> list2 = Lists.newArrayList();
        Predicate<BlockState> predicate = isReplaceable(geodeConfiguration.geodeBlockSettings.cannotReplace);

        for (BlockPos blockPos2 : BlockPos.betweenClosed(blockPos.offset(i, i, i), blockPos.offset(i1, i1, i1))) {
            double d6 = normalNoise.getValue(blockPos2.getX(), blockPos2.getY(), blockPos2.getZ()) * geodeConfiguration.noiseMultiplier;
            double d7 = 0.0;
            double d8 = 0.0;

            for (Pair<BlockPos, Integer> pair : list) {
                d7 += Mth.invSqrt(blockPos2.distSqr(pair.getFirst()) + pair.getSecond().intValue()) + d6;
            }

            for (BlockPos blockPos3 : list1) {
                d8 += Mth.invSqrt(blockPos2.distSqr(blockPos3) + geodeCrackSettings.crackPointOffset) + d6;
            }

            if (!(d7 < d4)) {
                if (flag && d8 >= d5 && d7 < d1) {
                    this.safeSetBlock(worldGenLevel, blockPos2, Blocks.AIR.defaultBlockState(), predicate);

                    for (Direction direction : DIRECTIONS) {
                        BlockPos blockPos4 = blockPos2.relative(direction);
                        FluidState fluidState = worldGenLevel.getFluidState(blockPos4);
                        if (!fluidState.isEmpty()) {
                            worldGenLevel.scheduleTick(blockPos4, fluidState.getType(), 0);
                        }
                    }
                } else if (d7 >= d1) {
                    this.safeSetBlock(worldGenLevel, blockPos2, geodeBlockSettings.fillingProvider.getState(randomSource, blockPos2), predicate);
                } else if (d7 >= d2) {
                    boolean flag1 = randomSource.nextFloat() < geodeConfiguration.useAlternateLayer0Chance;
                    if (flag1) {
                        this.safeSetBlock(worldGenLevel, blockPos2, geodeBlockSettings.alternateInnerLayerProvider.getState(randomSource, blockPos2), predicate);
                    } else {
                        this.safeSetBlock(worldGenLevel, blockPos2, geodeBlockSettings.innerLayerProvider.getState(randomSource, blockPos2), predicate);
                    }

                    if ((!geodeConfiguration.placementsRequireLayer0Alternate || flag1)
                        && randomSource.nextFloat() < geodeConfiguration.usePotentialPlacementsChance) {
                        list2.add(blockPos2.immutable());
                    }
                } else if (d7 >= d3) {
                    this.safeSetBlock(worldGenLevel, blockPos2, geodeBlockSettings.middleLayerProvider.getState(randomSource, blockPos2), predicate);
                } else if (d7 >= d4) {
                    this.safeSetBlock(worldGenLevel, blockPos2, geodeBlockSettings.outerLayerProvider.getState(randomSource, blockPos2), predicate);
                }
            }
        }

        List<BlockState> list3 = geodeBlockSettings.innerPlacements;

        for (BlockPos blockPos1 : list2) {
            BlockState blockState = Util.getRandom(list3, randomSource);

            for (Direction direction1 : DIRECTIONS) {
                if (blockState.hasProperty(BlockStateProperties.FACING)) {
                    blockState = blockState.setValue(BlockStateProperties.FACING, direction1);
                }

                BlockPos blockPos5 = blockPos1.relative(direction1);
                BlockState blockState1 = worldGenLevel.getBlockState(blockPos5);
                if (blockState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                    blockState = blockState.setValue(BlockStateProperties.WATERLOGGED, blockState1.getFluidState().isSource());
                }

                if (BuddingAmethystBlock.canClusterGrowAtState(blockState1)) {
                    this.safeSetBlock(worldGenLevel, blockPos5, blockState, predicate);
                    break;
                }
            }
        }

        return true;
    }
}
