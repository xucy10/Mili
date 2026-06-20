package net.minecraft.world.level.redstone;

import com.google.common.collect.Sets;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class DefaultRedstoneWireEvaluator extends RedstoneWireEvaluator {
    public DefaultRedstoneWireEvaluator(RedStoneWireBlock wireBlock) {
        super(wireBlock);
    }

    @Override
    public void updatePowerStrength(Level level, BlockPos pos, BlockState state, @Nullable Orientation orientation, boolean updateShape) {
        int i = this.calculateTargetStrength(level, pos);
        // CraftBukkit start
        int oldPower = state.getValue(RedStoneWireBlock.POWER);
        if (oldPower != i) {
            org.bukkit.event.block.BlockRedstoneEvent event = new org.bukkit.event.block.BlockRedstoneEvent(org.bukkit.craftbukkit.block.CraftBlock.at(level, pos), oldPower, i);
            level.getCraftServer().getPluginManager().callEvent(event);

            i = event.getNewCurrent();
        }
        if (oldPower != i) {
            // CraftBukkit end
            if (level.getBlockState(pos) == state) {
                level.setBlock(pos, state.setValue(RedStoneWireBlock.POWER, i), Block.UPDATE_CLIENTS);
            }

            Set<BlockPos> set = Sets.newHashSet();
            set.add(pos);

            for (Direction direction : Direction.values()) {
                set.add(pos.relative(direction));
            }

            for (BlockPos blockPos : set) {
                level.updateNeighborsAt(blockPos, this.wireBlock);
            }
        }
    }

    public int calculateTargetStrength(Level level, BlockPos pos) { // Paper - Optimize redstone
        int blockSignal = this.getBlockSignal(level, pos);
        return blockSignal == 15 ? blockSignal : Math.max(blockSignal, this.getIncomingWireSignal(level, pos));
    }
}
