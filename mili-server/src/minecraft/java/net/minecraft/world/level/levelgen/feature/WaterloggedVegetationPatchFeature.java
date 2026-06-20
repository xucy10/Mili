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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.VegetationPatchConfiguration;

public class WaterloggedVegetationPatchFeature extends VegetationPatchFeature {
    public WaterloggedVegetationPatchFeature(Codec<VegetationPatchConfiguration> codec) {
        super(codec);
    }

    @Override
    protected Set<BlockPos> placeGroundPatch(
        WorldGenLevel level, VegetationPatchConfiguration config, RandomSource random, BlockPos pos, Predicate<BlockState> state, int xRadius, int zRadius
    ) {
        Set<BlockPos> set = super.placeGroundPatch(level, config, random, pos, state, xRadius, zRadius);
        Set<BlockPos> set1 = new HashSet<>();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (BlockPos blockPos : set) {
            if (!isExposed(level, set, blockPos, mutableBlockPos)) {
                set1.add(blockPos);
            }
        }

        for (BlockPos blockPosx : set1) {
            level.setBlock(blockPosx, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        return set1;
    }

    private static boolean isExposed(WorldGenLevel level, Set<BlockPos> positions, BlockPos pos, BlockPos.MutableBlockPos mutablePos) {
        return isExposedDirection(level, pos, mutablePos, Direction.NORTH)
            || isExposedDirection(level, pos, mutablePos, Direction.EAST)
            || isExposedDirection(level, pos, mutablePos, Direction.SOUTH)
            || isExposedDirection(level, pos, mutablePos, Direction.WEST)
            || isExposedDirection(level, pos, mutablePos, Direction.DOWN);
    }

    private static boolean isExposedDirection(WorldGenLevel level, BlockPos pos, BlockPos.MutableBlockPos mutablePos, Direction direction) {
        mutablePos.setWithOffset(pos, direction);
        return !level.getBlockState(mutablePos).isFaceSturdy(level, mutablePos, direction.getOpposite());
    }

    @Override
    protected boolean placeVegetation(
        WorldGenLevel level, VegetationPatchConfiguration config, ChunkGenerator chunkGenerator, RandomSource random, BlockPos pos
    ) {
        if (super.placeVegetation(level, config, chunkGenerator, random, pos.below())) {
            BlockState blockState = level.getBlockState(pos);
            if (blockState.hasProperty(BlockStateProperties.WATERLOGGED) && !blockState.getValue(BlockStateProperties.WATERLOGGED)) {
                level.setBlock(pos, blockState.setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_CLIENTS);
            }

            return true;
        } else {
            return false;
        }
    }
}
