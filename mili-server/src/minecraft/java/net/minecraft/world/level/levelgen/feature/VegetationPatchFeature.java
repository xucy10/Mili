package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.VegetationPatchConfiguration;

public class VegetationPatchFeature extends Feature<VegetationPatchConfiguration> {
    public VegetationPatchFeature(Codec<VegetationPatchConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<VegetationPatchConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        VegetationPatchConfiguration vegetationPatchConfiguration = context.config();
        RandomSource randomSource = context.random();
        BlockPos blockPos = context.origin();
        Predicate<BlockState> predicate = blockState -> blockState.is(vegetationPatchConfiguration.replaceable);
        int i = vegetationPatchConfiguration.xzRadius.sample(randomSource) + 1;
        int i1 = vegetationPatchConfiguration.xzRadius.sample(randomSource) + 1;
        Set<BlockPos> set = this.placeGroundPatch(worldGenLevel, vegetationPatchConfiguration, randomSource, blockPos, predicate, i, i1);
        this.distributeVegetation(context, worldGenLevel, vegetationPatchConfiguration, randomSource, set, i, i1);
        return !set.isEmpty();
    }

    protected Set<BlockPos> placeGroundPatch(
        WorldGenLevel level, VegetationPatchConfiguration config, RandomSource random, BlockPos pos, Predicate<BlockState> state, int xRadius, int zRadius
    ) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
        BlockPos.MutableBlockPos mutableBlockPos1 = mutableBlockPos.mutable();
        Direction direction = config.surface.getDirection();
        Direction opposite = direction.getOpposite();
        Set<BlockPos> set = new HashSet<>();

        for (int i = -xRadius; i <= xRadius; i++) {
            boolean flag = i == -xRadius || i == xRadius;

            for (int i1 = -zRadius; i1 <= zRadius; i1++) {
                boolean flag1 = i1 == -zRadius || i1 == zRadius;
                boolean flag2 = flag || flag1;
                boolean flag3 = flag && flag1;
                boolean flag4 = flag2 && !flag3;
                if (!flag3 && (!flag4 || config.extraEdgeColumnChance != 0.0F && !(random.nextFloat() > config.extraEdgeColumnChance))) {
                    mutableBlockPos.setWithOffset(pos, i, 0, i1);

                    for (int i2 = 0; level.isStateAtPosition(mutableBlockPos, BlockBehaviour.BlockStateBase::isAir) && i2 < config.verticalRange; i2++) {
                        mutableBlockPos.move(direction);
                    }

                    for (int var25 = 0; level.isStateAtPosition(mutableBlockPos, blockState1 -> !blockState1.isAir()) && var25 < config.verticalRange; var25++) {
                        mutableBlockPos.move(opposite);
                    }

                    mutableBlockPos1.setWithOffset(mutableBlockPos, config.surface.getDirection());
                    BlockState blockState = level.getBlockState(mutableBlockPos1);
                    if (level.isEmptyBlock(mutableBlockPos) && blockState.isFaceSturdy(level, mutableBlockPos1, config.surface.getDirection().getOpposite())) {
                        int i3 = config.depth.sample(random)
                            + (config.extraBottomBlockChance > 0.0F && random.nextFloat() < config.extraBottomBlockChance ? 1 : 0);
                        BlockPos blockPos = mutableBlockPos1.immutable();
                        boolean flag5 = this.placeGround(level, config, state, random, mutableBlockPos1, i3);
                        if (flag5) {
                            set.add(blockPos);
                        }
                    }
                }
            }
        }

        return set;
    }

    protected void distributeVegetation(
        FeaturePlaceContext<VegetationPatchConfiguration> context,
        WorldGenLevel level,
        VegetationPatchConfiguration config,
        RandomSource random,
        Set<BlockPos> possiblePositions,
        int xRadius,
        int zRadius
    ) {
        for (BlockPos blockPos : possiblePositions) {
            if (config.vegetationChance > 0.0F && random.nextFloat() < config.vegetationChance) {
                this.placeVegetation(level, config, context.chunkGenerator(), random, blockPos);
            }
        }
    }

    protected boolean placeVegetation(
        WorldGenLevel level, VegetationPatchConfiguration config, ChunkGenerator chunkGenerator, RandomSource random, BlockPos pos
    ) {
        return config.vegetationFeature.value().place(level, chunkGenerator, random, pos.relative(config.surface.getDirection().getOpposite()));
    }

    protected boolean placeGround(
        WorldGenLevel level,
        VegetationPatchConfiguration config,
        Predicate<BlockState> replaceableBlocks,
        RandomSource random,
        BlockPos.MutableBlockPos mutablePos,
        int maxDistance
    ) {
        for (int i = 0; i < maxDistance; i++) {
            BlockState state = config.groundState.getState(random, mutablePos);
            BlockState blockState = level.getBlockState(mutablePos);
            if (!state.is(blockState.getBlock())) {
                if (!replaceableBlocks.test(blockState)) {
                    return i != 0;
                }

                level.setBlock(mutablePos, state, Block.UPDATE_CLIENTS);
                mutablePos.move(config.surface.getDirection());
            }
        }

        return true;
    }
}
