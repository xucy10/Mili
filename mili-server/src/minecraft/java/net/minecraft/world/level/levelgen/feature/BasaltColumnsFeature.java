package net.minecraft.world.level.levelgen.feature;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.ColumnFeatureConfiguration;
import org.jspecify.annotations.Nullable;

public class BasaltColumnsFeature extends Feature<ColumnFeatureConfiguration> {
    private static final ImmutableList<Block> CANNOT_PLACE_ON = ImmutableList.of(
        Blocks.LAVA,
        Blocks.BEDROCK,
        Blocks.MAGMA_BLOCK,
        Blocks.SOUL_SAND,
        Blocks.NETHER_BRICKS,
        Blocks.NETHER_BRICK_FENCE,
        Blocks.NETHER_BRICK_STAIRS,
        Blocks.NETHER_WART,
        Blocks.CHEST,
        Blocks.SPAWNER
    );
    private static final int CLUSTERED_REACH = 5;
    private static final int CLUSTERED_SIZE = 50;
    private static final int UNCLUSTERED_REACH = 8;
    private static final int UNCLUSTERED_SIZE = 15;

    public BasaltColumnsFeature(Codec<ColumnFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<ColumnFeatureConfiguration> context) {
        int seaLevel = context.chunkGenerator().getSeaLevel();
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        ColumnFeatureConfiguration columnFeatureConfiguration = context.config();
        if (!canPlaceAt(worldGenLevel, seaLevel, blockPos.mutable())) {
            return false;
        } else {
            int i = columnFeatureConfiguration.height().sample(randomSource);
            boolean flag = randomSource.nextFloat() < 0.9F;
            int min = Math.min(i, flag ? 5 : 8);
            int i1 = flag ? 50 : 15;
            boolean flag1 = false;

            for (BlockPos blockPos1 : BlockPos.randomBetweenClosed(
                randomSource, i1, blockPos.getX() - min, blockPos.getY(), blockPos.getZ() - min, blockPos.getX() + min, blockPos.getY(), blockPos.getZ() + min
            )) {
                int i2 = i - blockPos1.distManhattan(blockPos);
                if (i2 >= 0) {
                    flag1 |= this.placeColumn(worldGenLevel, seaLevel, blockPos1, i2, columnFeatureConfiguration.reach().sample(randomSource));
                }
            }

            return flag1;
        }
    }

    private boolean placeColumn(LevelAccessor level, int seaLevel, BlockPos pos, int distance, int reach) {
        boolean flag = false;

        for (BlockPos blockPos : BlockPos.betweenClosed(pos.getX() - reach, pos.getY(), pos.getZ() - reach, pos.getX() + reach, pos.getY(), pos.getZ() + reach)) {
            int i = blockPos.distManhattan(pos);
            BlockPos blockPos1 = isAirOrLavaOcean(level, seaLevel, blockPos)
                ? findSurface(level, seaLevel, blockPos.mutable(), i)
                : findAir(level, blockPos.mutable(), i);
            if (blockPos1 != null) {
                int i1 = distance - i / 2;

                for (BlockPos.MutableBlockPos mutableBlockPos = blockPos1.mutable(); i1 >= 0; i1--) {
                    if (isAirOrLavaOcean(level, seaLevel, mutableBlockPos)) {
                        this.setBlock(level, mutableBlockPos, Blocks.BASALT.defaultBlockState());
                        mutableBlockPos.move(Direction.UP);
                        flag = true;
                    } else {
                        if (!level.getBlockState(mutableBlockPos).is(Blocks.BASALT)) {
                            break;
                        }

                        mutableBlockPos.move(Direction.UP);
                    }
                }
            }
        }

        return flag;
    }

    private static @Nullable BlockPos findSurface(LevelAccessor level, int seaLevel, BlockPos.MutableBlockPos pos, int distance) {
        while (pos.getY() > level.getMinY() + 1 && distance > 0) {
            distance--;
            if (canPlaceAt(level, seaLevel, pos)) {
                return pos;
            }

            pos.move(Direction.DOWN);
        }

        return null;
    }

    private static boolean canPlaceAt(LevelAccessor level, int seaLevel, BlockPos.MutableBlockPos pos) {
        if (!isAirOrLavaOcean(level, seaLevel, pos)) {
            return false;
        } else {
            BlockState blockState = level.getBlockState(pos.move(Direction.DOWN));
            pos.move(Direction.UP);
            return !blockState.isAir() && !CANNOT_PLACE_ON.contains(blockState.getBlock());
        }
    }

    private static @Nullable BlockPos findAir(LevelAccessor level, BlockPos.MutableBlockPos pos, int distance) {
        while (pos.getY() <= level.getMaxY() && distance > 0) {
            distance--;
            BlockState blockState = level.getBlockState(pos);
            if (CANNOT_PLACE_ON.contains(blockState.getBlock())) {
                return null;
            }

            if (blockState.isAir()) {
                return pos;
            }

            pos.move(Direction.UP);
        }

        return null;
    }

    private static boolean isAirOrLavaOcean(LevelAccessor level, int seaLevel, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return blockState.isAir() || blockState.is(Blocks.LAVA) && pos.getY() <= seaLevel;
    }
}
