package net.minecraft.world.level.block;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

public interface BonemealableBlock {
    boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state);

    boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state);

    void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state);

    static boolean hasSpreadableNeighbourPos(LevelReader level, BlockPos pos, BlockState state) {
        return getSpreadableNeighbourPos(Direction.Plane.HORIZONTAL.stream().toList(), level, pos, state).isPresent();
    }

    static Optional<BlockPos> findSpreadableNeighbourPos(Level level, BlockPos pos, BlockState state) {
        return getSpreadableNeighbourPos(Direction.Plane.HORIZONTAL.shuffledCopy(level.random), level, pos, state);
    }

    private static Optional<BlockPos> getSpreadableNeighbourPos(List<Direction> directions, LevelReader level, BlockPos pos, BlockState state) {
        for (Direction direction : directions) {
            BlockPos blockPos = pos.relative(direction);
            if (level.isEmptyBlock(blockPos) && state.canSurvive(level, blockPos)) {
                return Optional.of(blockPos);
            }
        }

        return Optional.empty();
    }

    default BlockPos getParticlePos(BlockPos pos) {
        return switch (this.getType()) {
            case NEIGHBOR_SPREADER -> pos.above();
            case GROWER -> pos;
        };
    }

    default BonemealableBlock.Type getType() {
        return BonemealableBlock.Type.GROWER;
    }

    public static enum Type {
        NEIGHBOR_SPREADER,
        GROWER;
    }
}
