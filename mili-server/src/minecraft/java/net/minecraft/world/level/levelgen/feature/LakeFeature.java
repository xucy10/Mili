package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

@Deprecated
public class LakeFeature extends Feature<LakeFeature.Configuration> {
    private static final BlockState AIR = Blocks.CAVE_AIR.defaultBlockState();

    public LakeFeature(Codec<LakeFeature.Configuration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<LakeFeature.Configuration> context) {
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        LakeFeature.Configuration configuration = context.config();
        if (blockPos.getY() <= worldGenLevel.getMinY() + 4) {
            return false;
        } else {
            blockPos = blockPos.below(4);
            boolean[] flags = new boolean[2048];
            int i = randomSource.nextInt(4) + 4;

            for (int i1 = 0; i1 < i; i1++) {
                double d = randomSource.nextDouble() * 6.0 + 3.0;
                double d1 = randomSource.nextDouble() * 4.0 + 2.0;
                double d2 = randomSource.nextDouble() * 6.0 + 3.0;
                double d3 = randomSource.nextDouble() * (16.0 - d - 2.0) + 1.0 + d / 2.0;
                double d4 = randomSource.nextDouble() * (8.0 - d1 - 4.0) + 2.0 + d1 / 2.0;
                double d5 = randomSource.nextDouble() * (16.0 - d2 - 2.0) + 1.0 + d2 / 2.0;

                for (int i2 = 1; i2 < 15; i2++) {
                    for (int i3 = 1; i3 < 15; i3++) {
                        for (int i4 = 1; i4 < 7; i4++) {
                            double d6 = (i2 - d3) / (d / 2.0);
                            double d7 = (i4 - d4) / (d1 / 2.0);
                            double d8 = (i3 - d5) / (d2 / 2.0);
                            double d9 = d6 * d6 + d7 * d7 + d8 * d8;
                            if (d9 < 1.0) {
                                flags[(i2 * 16 + i3) * 8 + i4] = true;
                            }
                        }
                    }
                }
            }

            BlockState state = configuration.fluid().getState(randomSource, blockPos);

            for (int i5 = 0; i5 < 16; i5++) {
                for (int i6 = 0; i6 < 16; i6++) {
                    for (int i7 = 0; i7 < 8; i7++) {
                        boolean flag = !flags[(i5 * 16 + i6) * 8 + i7]
                            && (
                                i5 < 15 && flags[((i5 + 1) * 16 + i6) * 8 + i7]
                                    || i5 > 0 && flags[((i5 - 1) * 16 + i6) * 8 + i7]
                                    || i6 < 15 && flags[(i5 * 16 + i6 + 1) * 8 + i7]
                                    || i6 > 0 && flags[(i5 * 16 + (i6 - 1)) * 8 + i7]
                                    || i7 < 7 && flags[(i5 * 16 + i6) * 8 + i7 + 1]
                                    || i7 > 0 && flags[(i5 * 16 + i6) * 8 + (i7 - 1)]
                            );
                        if (flag) {
                            BlockState blockState = worldGenLevel.getBlockState(blockPos.offset(i5, i7, i6));
                            if (i7 >= 4 && blockState.liquid()) {
                                return false;
                            }

                            if (i7 < 4 && !blockState.isSolid() && worldGenLevel.getBlockState(blockPos.offset(i5, i7, i6)) != state) {
                                return false;
                            }
                        }
                    }
                }
            }

            for (int i5 = 0; i5 < 16; i5++) {
                for (int i6 = 0; i6 < 16; i6++) {
                    for (int i7x = 0; i7x < 8; i7x++) {
                        if (flags[(i5 * 16 + i6) * 8 + i7x]) {
                            BlockPos blockPos1 = blockPos.offset(i5, i7x, i6);
                            if (this.canReplaceBlock(worldGenLevel.getBlockState(blockPos1))) {
                                boolean flag1 = i7x >= 4;
                                worldGenLevel.setBlock(blockPos1, flag1 ? AIR : state, Block.UPDATE_CLIENTS);
                                if (flag1) {
                                    worldGenLevel.scheduleTick(blockPos1, AIR.getBlock(), 0);
                                    this.markAboveForPostProcessing(worldGenLevel, blockPos1);
                                }
                            }
                        }
                    }
                }
            }

            BlockState state1 = configuration.barrier().getState(randomSource, blockPos);
            if (!state1.isAir()) {
                for (int i6 = 0; i6 < 16; i6++) {
                    for (int i7xx = 0; i7xx < 16; i7xx++) {
                        for (int i8 = 0; i8 < 8; i8++) {
                            boolean flag1 = !flags[(i6 * 16 + i7xx) * 8 + i8]
                                && (
                                    i6 < 15 && flags[((i6 + 1) * 16 + i7xx) * 8 + i8]
                                        || i6 > 0 && flags[((i6 - 1) * 16 + i7xx) * 8 + i8]
                                        || i7xx < 15 && flags[(i6 * 16 + i7xx + 1) * 8 + i8]
                                        || i7xx > 0 && flags[(i6 * 16 + (i7xx - 1)) * 8 + i8]
                                        || i8 < 7 && flags[(i6 * 16 + i7xx) * 8 + i8 + 1]
                                        || i8 > 0 && flags[(i6 * 16 + i7xx) * 8 + (i8 - 1)]
                                );
                            if (flag1 && (i8 < 4 || randomSource.nextInt(2) != 0)) {
                                BlockState blockState1 = worldGenLevel.getBlockState(blockPos.offset(i6, i8, i7xx));
                                if (blockState1.isSolid() && !blockState1.is(BlockTags.LAVA_POOL_STONE_CANNOT_REPLACE)) {
                                    BlockPos blockPos2 = blockPos.offset(i6, i8, i7xx);
                                    worldGenLevel.setBlock(blockPos2, state1, Block.UPDATE_CLIENTS);
                                    this.markAboveForPostProcessing(worldGenLevel, blockPos2);
                                }
                            }
                        }
                    }
                }
            }

            if (state.getFluidState().is(FluidTags.WATER)) {
                for (int i6 = 0; i6 < 16; i6++) {
                    for (int i7xx = 0; i7xx < 16; i7xx++) {
                        int i8x = 4;
                        BlockPos blockPos3 = blockPos.offset(i6, 4, i7xx);
                        if (worldGenLevel.getBiome(blockPos3).value().shouldFreeze(worldGenLevel, blockPos3, false)
                            && this.canReplaceBlock(worldGenLevel.getBlockState(blockPos3))) {
                            worldGenLevel.setBlock(blockPos3, Blocks.ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }

            return true;
        }
    }

    private boolean canReplaceBlock(BlockState state) {
        return !state.is(BlockTags.FEATURES_CANNOT_REPLACE);
    }

    public record Configuration(BlockStateProvider fluid, BlockStateProvider barrier) implements FeatureConfiguration {
        public static final Codec<LakeFeature.Configuration> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    BlockStateProvider.CODEC.fieldOf("fluid").forGetter(LakeFeature.Configuration::fluid),
                    BlockStateProvider.CODEC.fieldOf("barrier").forGetter(LakeFeature.Configuration::barrier)
                )
                .apply(instance, LakeFeature.Configuration::new)
        );
    }
}
