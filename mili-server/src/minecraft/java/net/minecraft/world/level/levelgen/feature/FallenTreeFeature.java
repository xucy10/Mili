package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FallenTreeConfiguration;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;

public class FallenTreeFeature extends Feature<FallenTreeConfiguration> {
    private static final int STUMP_HEIGHT = 1;
    private static final int STUMP_HEIGHT_PLUS_EMPTY_SPACE = 2;
    private static final int FALLEN_LOG_MAX_FALL_HEIGHT_TO_GROUND = 5;
    private static final int FALLEN_LOG_MAX_GROUND_GAP = 2;
    private static final int FALLEN_LOG_MAX_SPACE_FROM_STUMP = 2;

    public FallenTreeFeature(Codec<FallenTreeConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<FallenTreeConfiguration> context) {
        this.placeFallenTree(context.config(), context.origin(), context.level(), context.random());
        return true;
    }

    private void placeFallenTree(FallenTreeConfiguration config, BlockPos origin, WorldGenLevel level, RandomSource random) {
        this.placeStump(config, level, random, origin.mutable());
        Direction randomDirection = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int i = config.logLength.sample(random) - 2;
        BlockPos.MutableBlockPos mutableBlockPos = origin.relative(randomDirection, 2 + random.nextInt(2)).mutable();
        this.setGroundHeightForFallenLogStartPos(level, mutableBlockPos);
        if (this.canPlaceEntireFallenLog(level, i, mutableBlockPos, randomDirection)) {
            this.placeFallenLog(config, level, random, i, mutableBlockPos, randomDirection);
        }
    }

    private void setGroundHeightForFallenLogStartPos(WorldGenLevel level, BlockPos.MutableBlockPos pos) {
        pos.move(Direction.UP, 1);

        for (int i = 0; i < 6; i++) {
            if (this.mayPlaceOn(level, pos)) {
                return;
            }

            pos.move(Direction.DOWN);
        }
    }

    private void placeStump(FallenTreeConfiguration config, WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos) {
        BlockPos blockPos = this.placeLogBlock(config, level, random, pos, Function.identity());
        this.decorateLogs(level, random, Set.of(blockPos), config.stumpDecorators);
    }

    private boolean canPlaceEntireFallenLog(WorldGenLevel level, int logLength, BlockPos.MutableBlockPos pos, Direction direction) {
        int i = 0;

        for (int i1 = 0; i1 < logLength; i1++) {
            if (!TreeFeature.validTreePos(level, pos)) {
                return false;
            }

            if (!this.isOverSolidGround(level, pos)) {
                if (++i > 2) {
                    return false;
                }
            } else {
                i = 0;
            }

            pos.move(direction);
        }

        pos.move(direction.getOpposite(), logLength);
        return true;
    }

    private void placeFallenLog(
        FallenTreeConfiguration config, WorldGenLevel level, RandomSource random, int logLength, BlockPos.MutableBlockPos pos, Direction direction
    ) {
        Set<BlockPos> set = new HashSet<>();

        for (int i = 0; i < logLength; i++) {
            set.add(this.placeLogBlock(config, level, random, pos, getSidewaysStateModifier(direction)));
            pos.move(direction);
        }

        this.decorateLogs(level, random, set, config.logDecorators);
    }

    private boolean mayPlaceOn(LevelAccessor level, BlockPos pos) {
        return TreeFeature.validTreePos(level, pos) && this.isOverSolidGround(level, pos);
    }

    private boolean isOverSolidGround(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos, Direction.UP);
    }

    private BlockPos placeLogBlock(
        FallenTreeConfiguration config, WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos, Function<BlockState, BlockState> stateModifier
    ) {
        level.setBlock(pos, stateModifier.apply(config.trunkProvider.getState(random, pos)), Block.UPDATE_ALL);
        this.markAboveForPostProcessing(level, pos);
        return pos.immutable();
    }

    private void decorateLogs(WorldGenLevel level, RandomSource random, Set<BlockPos> logPositions, List<TreeDecorator> decorators) {
        if (!decorators.isEmpty()) {
            TreeDecorator.Context context = new TreeDecorator.Context(level, this.getDecorationSetter(level), random, logPositions, Set.of(), Set.of());
            decorators.forEach(decorator -> decorator.place(context));
        }
    }

    private BiConsumer<BlockPos, BlockState> getDecorationSetter(WorldGenLevel level) {
        return (pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
    }

    private static Function<BlockState, BlockState> getSidewaysStateModifier(Direction direction) {
        return state -> state.trySetValue(RotatedPillarBlock.AXIS, direction.getAxis());
    }
}
