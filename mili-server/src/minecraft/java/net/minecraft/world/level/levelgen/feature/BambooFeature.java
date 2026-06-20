package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration;

public class BambooFeature extends Feature<ProbabilityFeatureConfiguration> {
    private static final BlockState BAMBOO_TRUNK = Blocks.BAMBOO
        .defaultBlockState()
        .setValue(BambooStalkBlock.AGE, 1)
        .setValue(BambooStalkBlock.LEAVES, BambooLeaves.NONE)
        .setValue(BambooStalkBlock.STAGE, 0);
    private static final BlockState BAMBOO_FINAL_LARGE = BAMBOO_TRUNK.setValue(BambooStalkBlock.LEAVES, BambooLeaves.LARGE).setValue(BambooStalkBlock.STAGE, 1);
    private static final BlockState BAMBOO_TOP_LARGE = BAMBOO_TRUNK.setValue(BambooStalkBlock.LEAVES, BambooLeaves.LARGE);
    private static final BlockState BAMBOO_TOP_SMALL = BAMBOO_TRUNK.setValue(BambooStalkBlock.LEAVES, BambooLeaves.SMALL);

    public BambooFeature(Codec<ProbabilityFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<ProbabilityFeatureConfiguration> context) {
        int i = 0;
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        ProbabilityFeatureConfiguration probabilityFeatureConfiguration = context.config();
        BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable();
        BlockPos.MutableBlockPos mutableBlockPos1 = blockPos.mutable();
        if (worldGenLevel.isEmptyBlock(mutableBlockPos)) {
            if (Blocks.BAMBOO.defaultBlockState().canSurvive(worldGenLevel, mutableBlockPos)) {
                int i1 = randomSource.nextInt(12) + 5;
                if (randomSource.nextFloat() < probabilityFeatureConfiguration.probability) {
                    int i2 = randomSource.nextInt(4) + 1;

                    for (int i3 = blockPos.getX() - i2; i3 <= blockPos.getX() + i2; i3++) {
                        for (int i4 = blockPos.getZ() - i2; i4 <= blockPos.getZ() + i2; i4++) {
                            int i5 = i3 - blockPos.getX();
                            int i6 = i4 - blockPos.getZ();
                            if (i5 * i5 + i6 * i6 <= i2 * i2) {
                                mutableBlockPos1.set(i3, worldGenLevel.getHeight(Heightmap.Types.WORLD_SURFACE, i3, i4) - 1, i4);
                                if (isDirt(worldGenLevel.getBlockState(mutableBlockPos1))) {
                                    worldGenLevel.setBlock(mutableBlockPos1, Blocks.PODZOL.defaultBlockState(), Block.UPDATE_CLIENTS);
                                }
                            }
                        }
                    }
                }

                for (int i2 = 0; i2 < i1 && worldGenLevel.isEmptyBlock(mutableBlockPos); i2++) {
                    worldGenLevel.setBlock(mutableBlockPos, BAMBOO_TRUNK, Block.UPDATE_CLIENTS);
                    mutableBlockPos.move(Direction.UP, 1);
                }

                if (mutableBlockPos.getY() - blockPos.getY() >= 3) {
                    worldGenLevel.setBlock(mutableBlockPos, BAMBOO_FINAL_LARGE, Block.UPDATE_CLIENTS);
                    worldGenLevel.setBlock(mutableBlockPos.move(Direction.DOWN, 1), BAMBOO_TOP_LARGE, Block.UPDATE_CLIENTS);
                    worldGenLevel.setBlock(mutableBlockPos.move(Direction.DOWN, 1), BAMBOO_TOP_SMALL, Block.UPDATE_CLIENTS);
                }
            }

            i++;
        }

        return i > 0;
    }
}
