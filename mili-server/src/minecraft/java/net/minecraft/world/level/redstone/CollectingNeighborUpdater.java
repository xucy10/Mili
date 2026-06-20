package net.minecraft.world.level.redstone;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class CollectingNeighborUpdater implements NeighborUpdater {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Level level;
    private final int maxChainedNeighborUpdates;
    private final ArrayDeque<CollectingNeighborUpdater.NeighborUpdates> stack = new ArrayDeque<>();
    private final List<CollectingNeighborUpdater.NeighborUpdates> addedThisLayer = new ArrayList<>();
    private int count = 0;
    private @Nullable Consumer<BlockPos> debugListener;

    public CollectingNeighborUpdater(Level level, int maxChainedNeighborUpdates) {
        this.level = level;
        this.maxChainedNeighborUpdates = maxChainedNeighborUpdates;
    }

    public void setDebugListener(@Nullable Consumer<BlockPos> debugListener) {
        this.debugListener = debugListener;
    }

    @Override
    public void shapeUpdate(Direction direction, BlockState state, BlockPos pos, BlockPos neighborPos, @Block.UpdateFlags int flags, int recursionLeft) {
        this.addAndRun(pos, new CollectingNeighborUpdater.ShapeUpdate(direction, state, pos.immutable(), neighborPos.immutable(), flags, recursionLeft));
    }

    @Override
    public void neighborChanged(BlockPos pos, Block neighborBlock, @Nullable Orientation orientation) {
        this.addAndRun(pos, new CollectingNeighborUpdater.SimpleNeighborUpdate(pos, neighborBlock, orientation));
    }

    @Override
    public void neighborChanged(BlockState state, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        this.addAndRun(pos, new CollectingNeighborUpdater.FullNeighborUpdate(state, pos.immutable(), neighborBlock, orientation, movedByPiston));
    }

    @Override
    public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block block, @Nullable Direction facing, @Nullable Orientation orientation) {
        this.addAndRun(pos, new CollectingNeighborUpdater.MultiNeighborUpdate(pos.immutable(), block, orientation, facing));
    }

    private void addAndRun(BlockPos pos, CollectingNeighborUpdater.NeighborUpdates updates) {
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread((net.minecraft.server.level.ServerLevel)this.level, pos, "Adding block without owning region"); // Folia - region threading
        boolean flag = this.count > 0;
        boolean flag1 = this.maxChainedNeighborUpdates >= 0 && this.count >= this.maxChainedNeighborUpdates;
        this.count++;
        if (!flag1) {
            if (flag) {
                this.addedThisLayer.add(updates);
            } else {
                this.stack.push(updates);
            }
        } else if (this.count - 1 == this.maxChainedNeighborUpdates) {
            LOGGER.error("Too many chained neighbor updates. Skipping the rest. First skipped position: {}", pos.toShortString());
        }

        if (!flag) {
            this.runUpdates();
        }
    }

    private void runUpdates() {
        try {
            while (!this.stack.isEmpty() || !this.addedThisLayer.isEmpty()) {
                for (int i = this.addedThisLayer.size() - 1; i >= 0; i--) {
                    this.stack.push(this.addedThisLayer.get(i));
                }

                this.addedThisLayer.clear();
                CollectingNeighborUpdater.NeighborUpdates neighborUpdates = this.stack.peek();
                if (this.debugListener != null) {
                    neighborUpdates.forEachUpdatedPos(this.debugListener);
                }

                while (this.addedThisLayer.isEmpty()) {
                    if (!neighborUpdates.runNext(this.level)) {
                        this.stack.pop();
                        break;
                    }
                }
            }
        } finally {
            this.stack.clear();
            this.addedThisLayer.clear();
            this.count = 0;
        }
    }

    record FullNeighborUpdate(BlockState state, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston)
        implements CollectingNeighborUpdater.NeighborUpdates {
        @Override
        public boolean runNext(Level level) {
            NeighborUpdater.executeUpdate(level, this.state, this.pos, this.block, this.orientation, this.movedByPiston);
            return false;
        }

        @Override
        public void forEachUpdatedPos(Consumer<BlockPos> action) {
            action.accept(this.pos);
        }
    }

    static final class MultiNeighborUpdate implements CollectingNeighborUpdater.NeighborUpdates {
        private final BlockPos sourcePos;
        private final Block sourceBlock;
        private @Nullable Orientation orientation;
        private final @Nullable Direction skipDirection;
        private int idx = 0;

        MultiNeighborUpdate(BlockPos sourcePos, Block sourceBlock, @Nullable Orientation orientation, @Nullable Direction skipDirection) {
            this.sourcePos = sourcePos;
            this.sourceBlock = sourceBlock;
            this.orientation = orientation;
            this.skipDirection = skipDirection;
            if (NeighborUpdater.UPDATE_ORDER[this.idx] == skipDirection) {
                this.idx++;
            }
        }

        @Override
        public boolean runNext(Level level) {
            Direction direction = NeighborUpdater.UPDATE_ORDER[this.idx++];
            BlockPos blockPos = this.sourcePos.relative(direction);
            BlockState blockState = !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(level, blockPos, 16) ? null : level.getBlockState(blockPos); // Folia - block updates in unloaded chunks
            if (blockState != null) { // Folia - block updates in unloaded chunks
            Orientation orientation = null;
            if (level.enabledFeatures().contains(FeatureFlags.REDSTONE_EXPERIMENTS)) {
                if (this.orientation == null) {
                    this.orientation = ExperimentalRedstoneUtils.initialOrientation(
                        level, this.skipDirection == null ? null : this.skipDirection.getOpposite(), null
                    );
                }

                orientation = this.orientation.withFront(direction);
            }

            NeighborUpdater.executeUpdate(level, blockState, blockPos, this.sourceBlock, orientation, false, this.sourcePos); // Paper - Add source block to BlockPhysicsEvent
            } // Folia - block updates in unloaded chunks
            if (this.idx < NeighborUpdater.UPDATE_ORDER.length && NeighborUpdater.UPDATE_ORDER[this.idx] == this.skipDirection) {
                this.idx++;
            }

            return this.idx < NeighborUpdater.UPDATE_ORDER.length;
        }

        @Override
        public void forEachUpdatedPos(Consumer<BlockPos> action) {
            for (Direction direction : NeighborUpdater.UPDATE_ORDER) {
                if (direction != this.skipDirection) {
                    BlockPos blockPos = this.sourcePos.relative(direction);
                    action.accept(blockPos);
                }
            }
        }
    }

    interface NeighborUpdates {
        boolean runNext(Level level);

        void forEachUpdatedPos(Consumer<BlockPos> action);
    }

    record ShapeUpdate(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, @Block.UpdateFlags int updateFlags, int updateLimit)
        implements CollectingNeighborUpdater.NeighborUpdates {
        @Override
        public boolean runNext(Level level) {
            if (ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(level, this.pos, 16) && level.getChunkIfLoaded(this.pos) != null) { // Folia - block updates in unloaded chunks
            NeighborUpdater.executeShapeUpdate(level, this.direction, this.pos, this.neighborPos, this.neighborState, this.updateFlags, this.updateLimit);
            } // Folia - block updates in unloaded chunks
            return false;
        }

        @Override
        public void forEachUpdatedPos(Consumer<BlockPos> action) {
            action.accept(this.pos);
        }
    }

    record SimpleNeighborUpdate(BlockPos pos, Block block, @Nullable Orientation orientation) implements CollectingNeighborUpdater.NeighborUpdates {
        @Override
        public boolean runNext(Level level) {
            BlockState blockState = !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(level, this.pos, 16) ? null : level.getBlockStateIfLoaded(this.pos); // Folia - block updates in unloaded chunks
            if (blockState != null) NeighborUpdater.executeUpdate(level, blockState, this.pos, this.block, this.orientation, false);
            return false;
        }

        @Override
        public void forEachUpdatedPos(Consumer<BlockPos> action) {
            action.accept(this.pos);
        }
    }
}
