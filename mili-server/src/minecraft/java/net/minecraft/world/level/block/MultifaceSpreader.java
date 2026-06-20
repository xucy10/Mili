package net.minecraft.world.level.block;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class MultifaceSpreader {
    public static final MultifaceSpreader.SpreadType[] DEFAULT_SPREAD_ORDER = new MultifaceSpreader.SpreadType[]{
        MultifaceSpreader.SpreadType.SAME_POSITION, MultifaceSpreader.SpreadType.SAME_PLANE, MultifaceSpreader.SpreadType.WRAP_AROUND
    };
    private final MultifaceSpreader.SpreadConfig config;

    public MultifaceSpreader(MultifaceBlock config) {
        this(new MultifaceSpreader.DefaultSpreaderConfig(config));
    }

    public MultifaceSpreader(MultifaceSpreader.SpreadConfig config) {
        this.config = config;
    }

    public boolean canSpreadInAnyDirection(BlockState state, BlockGetter level, BlockPos pos, Direction spreadDirection) {
        return Direction.stream()
            .anyMatch(face -> this.getSpreadFromFaceTowardDirection(state, level, pos, spreadDirection, face, this.config::canSpreadInto).isPresent());
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadFromRandomFaceTowardRandomDirection(
        BlockState state, LevelAccessor level, BlockPos pos, RandomSource random
    ) {
        return Direction.allShuffled(random)
            .stream()
            .filter(direction -> this.config.canSpreadFrom(state, direction))
            .map(spreadDirection -> this.spreadFromFaceTowardRandomDirection(state, level, pos, spreadDirection, random, false))
            .filter(Optional::isPresent)
            .findFirst()
            .orElse(Optional.empty());
    }

    public long spreadAll(BlockState state, LevelAccessor level, BlockPos pos, boolean markForPostprocessing) {
        return Direction.stream()
            .filter(direction -> this.config.canSpreadFrom(state, direction))
            .map(spreadDirection -> this.spreadFromFaceTowardAllDirections(state, level, pos, spreadDirection, markForPostprocessing))
            .reduce(0L, Long::sum);
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadFromFaceTowardRandomDirection(
        BlockState state, LevelAccessor level, BlockPos pos, Direction spreadDirection, RandomSource random, boolean markForPostprocessing
    ) {
        return Direction.allShuffled(random)
            .stream()
            .map(face -> this.spreadFromFaceTowardDirection(state, level, pos, spreadDirection, face, markForPostprocessing))
            .filter(Optional::isPresent)
            .findFirst()
            .orElse(Optional.empty());
    }

    private long spreadFromFaceTowardAllDirections(
        BlockState state, LevelAccessor level, BlockPos pos, Direction spreadDirection, boolean markForPostprocessing
    ) {
        return Direction.stream()
            .map(face -> this.spreadFromFaceTowardDirection(state, level, pos, spreadDirection, face, markForPostprocessing))
            .filter(Optional::isPresent)
            .count();
    }

    @VisibleForTesting
    public Optional<MultifaceSpreader.SpreadPos> spreadFromFaceTowardDirection(
        BlockState state, LevelAccessor level, BlockPos pos, Direction spreadDirection, Direction face, boolean markForPostprocessing
    ) {
        return this.getSpreadFromFaceTowardDirection(state, level, pos, spreadDirection, face, this.config::canSpreadInto)
            .flatMap(spreadPos -> this.spreadToFace(level, spreadPos, markForPostprocessing));
    }

    public Optional<MultifaceSpreader.SpreadPos> getSpreadFromFaceTowardDirection(
        BlockState state, BlockGetter level, BlockPos pos, Direction spreadDirection, Direction face, MultifaceSpreader.SpreadPredicate predicate
    ) {
        if (face.getAxis() == spreadDirection.getAxis()) {
            return Optional.empty();
        } else if (this.config.isOtherBlockValidAsSource(state) || this.config.hasFace(state, spreadDirection) && !this.config.hasFace(state, face)) {
            for (MultifaceSpreader.SpreadType spreadType : this.config.getSpreadTypes()) {
                MultifaceSpreader.SpreadPos spreadPos = spreadType.getSpreadPos(pos, face, spreadDirection);
                if (predicate.test(level, pos, spreadPos)) {
                    return Optional.of(spreadPos);
                }
            }

            return Optional.empty();
        } else {
            return Optional.empty();
        }
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadToFace(LevelAccessor level, MultifaceSpreader.SpreadPos pos, boolean markForPostprocessing) {
        BlockState blockState = level.getBlockState(pos.pos());
        return this.config.placeBlock(level, pos, blockState, markForPostprocessing) ? Optional.of(pos) : Optional.empty();
    }

    public static class DefaultSpreaderConfig implements MultifaceSpreader.SpreadConfig {
        protected MultifaceBlock block;

        public DefaultSpreaderConfig(MultifaceBlock block) {
            this.block = block;
        }

        @Override
        public @Nullable BlockState getStateForPlacement(BlockState currentState, BlockGetter level, BlockPos pos, Direction lookingDirection) {
            return this.block.getStateForPlacement(currentState, level, pos, lookingDirection);
        }

        protected boolean stateCanBeReplaced(BlockGetter level, BlockPos pos, BlockPos spreadPos, Direction direction, BlockState state) {
            return state.isAir() || state.is(this.block) || state.is(Blocks.WATER) && state.getFluidState().isSource();
        }

        @Override
        public boolean canSpreadInto(BlockGetter level, BlockPos pos, MultifaceSpreader.SpreadPos spreadPos) {
            BlockState blockState = level.getBlockState(spreadPos.pos());
            return this.stateCanBeReplaced(level, pos, spreadPos.pos(), spreadPos.face(), blockState)
                && this.block.isValidStateForPlacement(level, blockState, spreadPos.pos(), spreadPos.face());
        }
    }

    public interface SpreadConfig {
        @Nullable BlockState getStateForPlacement(BlockState currentState, BlockGetter level, BlockPos pos, Direction lookingDirection);

        boolean canSpreadInto(BlockGetter level, BlockPos pos, MultifaceSpreader.SpreadPos spreadPos);

        default MultifaceSpreader.SpreadType[] getSpreadTypes() {
            return MultifaceSpreader.DEFAULT_SPREAD_ORDER;
        }

        default boolean hasFace(BlockState state, Direction direction) {
            return MultifaceBlock.hasFace(state, direction);
        }

        default boolean isOtherBlockValidAsSource(BlockState otherBlock) {
            return false;
        }

        default boolean canSpreadFrom(BlockState state, Direction direction) {
            return this.isOtherBlockValidAsSource(state) || this.hasFace(state, direction);
        }

        default boolean placeBlock(LevelAccessor level, MultifaceSpreader.SpreadPos pos, BlockState state, boolean markForPostprocessing) {
            BlockState stateForPlacement = this.getStateForPlacement(state, level, pos.pos(), pos.face());
            if (stateForPlacement != null) {
                if (markForPostprocessing) {
                    level.getChunk(pos.pos()).markPosForPostprocessing(pos.pos());
                }

                return org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, pos.source(), pos.pos(), stateForPlacement, Block.UPDATE_CLIENTS, true); // CraftBukkit
            } else {
                return false;
            }
        }
    }

    public record SpreadPos(BlockPos pos, Direction face, BlockPos source) { // CraftBukkit
    }

    @FunctionalInterface
    public interface SpreadPredicate {
        boolean test(BlockGetter level, BlockPos pos, MultifaceSpreader.SpreadPos spreadPos);
    }

    public static enum SpreadType {
        SAME_POSITION {
            @Override
            public MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction face, Direction spreadDirection) {
                return new MultifaceSpreader.SpreadPos(pos, face, pos); // CraftBukkit
            }
        },
        SAME_PLANE {
            @Override
            public MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction face, Direction spreadDirection) {
                return new MultifaceSpreader.SpreadPos(pos.relative(face), spreadDirection, pos); // CraftBukkit
            }
        },
        WRAP_AROUND {
            @Override
            public MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction face, Direction spreadDirection) {
                return new MultifaceSpreader.SpreadPos(pos.relative(face).relative(spreadDirection), face.getOpposite(), pos); // CraftBukkit
            }
        };

        public abstract MultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction face, Direction spreadDirection);
    }
}
