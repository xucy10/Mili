package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.RootSystemConfiguration;

public class RootSystemFeature extends Feature<RootSystemConfiguration> {
    public RootSystemFeature(Codec<RootSystemConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<RootSystemConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        if (!worldGenLevel.getBlockState(blockPos).isAir()) {
            return false;
        } else {
            RandomSource randomSource = context.random();
            BlockPos blockPos1 = context.origin();
            RootSystemConfiguration rootSystemConfiguration = context.config();
            BlockPos.MutableBlockPos mutableBlockPos = blockPos1.mutable();
            if (placeDirtAndTree(worldGenLevel, context.chunkGenerator(), rootSystemConfiguration, randomSource, mutableBlockPos, blockPos1)) {
                placeRoots(worldGenLevel, rootSystemConfiguration, randomSource, blockPos1, mutableBlockPos);
            }

            return true;
        }
    }

    private static boolean spaceForTree(WorldGenLevel level, RootSystemConfiguration config, BlockPos pos) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        for (int i = 1; i <= config.requiredVerticalSpaceForTree; i++) {
            mutableBlockPos.move(Direction.UP);
            BlockState blockState = level.getBlockState(mutableBlockPos);
            if (!isAllowedTreeSpace(blockState, i, config.allowedVerticalWaterForTree)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isAllowedTreeSpace(BlockState state, int y, int allowedVerticalWater) {
        if (state.isAir()) {
            return true;
        } else {
            int i = y + 1;
            return i <= allowedVerticalWater && state.getFluidState().is(FluidTags.WATER);
        }
    }

    private static boolean placeDirtAndTree(
        WorldGenLevel level,
        ChunkGenerator chunkGenerator,
        RootSystemConfiguration config,
        RandomSource random,
        BlockPos.MutableBlockPos mutablePos,
        BlockPos basePos
    ) {
        for (int i = 0; i < config.rootColumnMaxHeight; i++) {
            mutablePos.move(Direction.UP);
            if (config.allowedTreePosition.test(level, mutablePos) && spaceForTree(level, config, mutablePos)) {
                BlockPos blockPos = mutablePos.below();
                if (level.getFluidState(blockPos).is(FluidTags.LAVA) || !level.getBlockState(blockPos).isSolid()) {
                    return false;
                }

                if (config.treeFeature.value().place(level, chunkGenerator, random, mutablePos)) {
                    placeDirt(basePos, basePos.getY() + i, level, config, random);
                    return true;
                }
            }
        }

        return false;
    }

    private static void placeDirt(BlockPos pos, int maxY, WorldGenLevel level, RootSystemConfiguration config, RandomSource random) {
        int x = pos.getX();
        int z = pos.getZ();
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        for (int y = pos.getY(); y < maxY; y++) {
            placeRootedDirt(level, config, random, x, z, mutableBlockPos.set(x, y, z));
        }
    }

    private static void placeRootedDirt(WorldGenLevel level, RootSystemConfiguration config, RandomSource random, int x, int z, BlockPos.MutableBlockPos pos) {
        int i = config.rootRadius;
        Predicate<BlockState> predicate = blockState -> blockState.is(config.rootReplaceable);

        for (int i1 = 0; i1 < config.rootPlacementAttempts; i1++) {
            pos.setWithOffset(pos, random.nextInt(i) - random.nextInt(i), 0, random.nextInt(i) - random.nextInt(i));
            if (predicate.test(level.getBlockState(pos))) {
                level.setBlock(pos, config.rootStateProvider.getState(random, pos), Block.UPDATE_CLIENTS);
            }

            pos.setX(x);
            pos.setZ(z);
        }
    }

    private static void placeRoots(
        WorldGenLevel level, RootSystemConfiguration config, RandomSource random, BlockPos basePos, BlockPos.MutableBlockPos mutablePos
    ) {
        int i = config.hangingRootRadius;
        int i1 = config.hangingRootsVerticalSpan;

        for (int i2 = 0; i2 < config.hangingRootPlacementAttempts; i2++) {
            mutablePos.setWithOffset(
                basePos, random.nextInt(i) - random.nextInt(i), random.nextInt(i1) - random.nextInt(i1), random.nextInt(i) - random.nextInt(i)
            );
            if (level.isEmptyBlock(mutablePos)) {
                BlockState state = config.hangingRootStateProvider.getState(random, mutablePos);
                if (state.canSurvive(level, mutablePos) && level.getBlockState(mutablePos.above()).isFaceSturdy(level, mutablePos, Direction.DOWN)) {
                    level.setBlock(mutablePos, state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }
}
