package net.minecraft.world.level.block;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

public class RedstoneTorchBlock extends BaseTorchBlock {
    public static final MapCodec<RedstoneTorchBlock> CODEC = simpleCodec(RedstoneTorchBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    // Paper - Faster redstone torch rapid clock removal; Move the mapped list to World
    public static final int RECENT_TOGGLE_TIMER = 60;
    public static final int MAX_RECENT_TOGGLES = 8;
    public static final int RESTART_DELAY = 160;
    private static final int TOGGLE_DELAY = 2;

    @Override
    public MapCodec<? extends RedstoneTorchBlock> codec() {
        return CODEC;
    }

    protected RedstoneTorchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        this.notifyNeighbors(level, pos, state);
    }

    private void notifyNeighbors(Level level, BlockPos pos, BlockState state) {
        Orientation orientation = this.randomOrientation(level, state);

        for (Direction direction : Direction.values()) {
            level.updateNeighborsAt(pos.relative(direction), this, ExperimentalRedstoneUtils.withFront(orientation, direction));
        }
    }

    // Leaves start - behaviour 1.21.1-
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving) {
            this.notifyNeighbors(level, pos, state);
        }
    }
    // Leaves end - behaviour 1.21.1-

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston) {
            this.notifyNeighbors(level, pos, state);
        }
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(LIT) && Direction.UP != side ? 15 : 0;
    }

    protected boolean hasNeighborSignal(Level level, BlockPos pos, BlockState state) {
        return level.hasSignal(pos.below(), Direction.DOWN);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean hasNeighborSignal = this.hasNeighborSignal(level, pos, state);
        // Paper start - Faster redstone torch rapid clock removal
        java.util.ArrayDeque<RedstoneTorchBlock.Toggle> redstoneUpdateInfos = level.getCurrentWorldData().redstoneUpdateInfos; // Folia - region threading
        if (redstoneUpdateInfos != null) {
            RedstoneTorchBlock.Toggle curr;
            while ((curr = redstoneUpdateInfos.peek()) != null && level.getRedstoneGameTime() - curr.when > 60L) { // Folia - region threading
                redstoneUpdateInfos.poll();
            }
        }
        // Paper end - Faster redstone torch rapid clock removal

        // CraftBukkit start
        org.bukkit.plugin.PluginManager manager = level.getCraftServer().getPluginManager();
        org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
        int oldCurrent = state.getValue(LIT) ? 15 : 0;

        org.bukkit.event.block.BlockRedstoneEvent event = new org.bukkit.event.block.BlockRedstoneEvent(block, oldCurrent, oldCurrent);
        // CraftBukkit end
        if (state.getValue(LIT)) {
            if (hasNeighborSignal) {
                // CraftBukkit start
                if (oldCurrent != 0) {
                    event.setNewCurrent(0);
                    manager.callEvent(event);
                    if (event.getNewCurrent() != 0) {
                        return;
                    }
                }
                // CraftBukkit end
                level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
                if (isToggledTooFrequently(level, pos, true)) {
                    level.levelEvent(LevelEvent.REDSTONE_TORCH_BURNOUT, pos, 0);
                    level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 160);
                }
            }
        } else if (!hasNeighborSignal && !isToggledTooFrequently(level, pos, false)) {
            // CraftBukkit start
            if (oldCurrent != 15) {
                event.setNewCurrent(15);
                manager.callEvent(event);
                if (event.getNewCurrent() != 15) {
                    return;
                }
            }
            // CraftBukkit end
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (state.getValue(LIT) == this.hasNeighborSignal(level, pos, state) && !level.getBlockTicks().willTickThisTick(pos, this)) {
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return side == Direction.DOWN ? state.getSignal(level, pos, side) : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            double d = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            double d1 = pos.getY() + 0.7 + (random.nextDouble() - 0.5) * 0.2;
            double d2 = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            level.addParticle(DustParticleOptions.REDSTONE, d, d1, d2, 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    private static boolean isToggledTooFrequently(Level level, BlockPos pos, boolean logToggle) {
        // Paper start - Faster redstone torch rapid clock removal
        java.util.ArrayDeque<RedstoneTorchBlock.Toggle> list = level.getCurrentWorldData().redstoneUpdateInfos; // Folia - region threading
        if (list == null) {
            list = level.getCurrentWorldData().redstoneUpdateInfos = new java.util.ArrayDeque<>(); // Folia - region threading
        }
        // Paper end - Faster redstone torch rapid clock removal
        if (logToggle) {
            list.add(new RedstoneTorchBlock.Toggle(pos.immutable(), level.getRedstoneGameTime())); // Folia - region threading
        }

        int i = 0;

        for (RedstoneTorchBlock.Toggle toggle : list) {
            if (toggle.pos.equals(pos)) {
                if (++i >= 8) {
                    return true;
                }
            }
        }

        return false;
    }

    protected @Nullable Orientation randomOrientation(Level level, BlockState state) {
        return ExperimentalRedstoneUtils.initialOrientation(level, null, Direction.UP);
    }

    public static class Toggle {
        public final BlockPos pos; // Folia - region threading
        long when; // Folia - region threading

        public Toggle(BlockPos pos, long when) {
            this.pos = pos;
            this.when = when;
        }

        // Folia start - region ticking
        public void offsetTime(long offset) {
            this.when += offset;
        }
        // Folia end - region ticking
    }
}
