package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

public interface ScheduledTickAccess {
    <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay, TickPriority priority);

    <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay);

    LevelTickAccess<Block> getBlockTicks();

    default void scheduleTick(BlockPos pos, Block block, int delay, TickPriority priority) {
        this.getBlockTicks().schedule(this.createTick(pos, block, delay, priority));
    }

    default void scheduleTick(BlockPos pos, Block block, int delay) {
        this.getBlockTicks().schedule(this.createTick(pos, block, delay));
    }

    LevelTickAccess<Fluid> getFluidTicks();

    default void scheduleTick(BlockPos pos, Fluid fluid, int delay, TickPriority priority) {
        this.getFluidTicks().schedule(this.createTick(pos, fluid, delay, priority));
    }

    default void scheduleTick(BlockPos pos, Fluid fluid, int delay) {
        this.getFluidTicks().schedule(this.createTick(pos, fluid, delay));
    }
}
