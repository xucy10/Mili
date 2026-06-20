package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;

public abstract class SpreadingSnowyDirtBlock extends SnowyDirtBlock {
    protected SpreadingSnowyDirtBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    private static boolean canBeGrass(BlockState state, LevelReader level, BlockPos pos) {
        // Paper start - Perf: optimize dirt and snow spreading
        return canBeGrass(level.getChunk(pos), state, level, pos);
    }
    private static boolean canBeGrass(net.minecraft.world.level.chunk.ChunkAccess chunk, BlockState state, LevelReader level, BlockPos pos) {
        // Paper end - Perf: optimize dirt and snow spreading
        BlockPos blockPos = pos.above();
        BlockState blockState = chunk.getBlockState(blockPos); // Paper - Perf: optimize dirt and snow spreading
        if (blockState.is(Blocks.SNOW) && blockState.getValue(SnowLayerBlock.LAYERS) == 1) {
            return true;
        } else if (blockState.getFluidState().getAmount() == 8) {
            return false;
        } else {
            int lightBlockInto = LightEngine.getLightBlockInto(state, blockState, Direction.UP, blockState.getLightBlock());
            return lightBlockInto < 15;
        }
    }

    @Override
    protected abstract MapCodec<? extends SpreadingSnowyDirtBlock> codec();

    private static boolean canPropagate(BlockState state, LevelReader level, BlockPos pos) {
        // Paper start - Perf: optimize dirt and snow spreading
        return canPropagate(level.getChunk(pos), state, level, pos);
    }

    private static boolean canPropagate(net.minecraft.world.level.chunk.ChunkAccess chunk, BlockState state, LevelReader level, BlockPos pos) {
        // Paper end - Perf: optimize dirt and snow spreading
        BlockPos blockPos = pos.above();
        return canBeGrass(chunk, state, level, pos) && !chunk.getFluidState(blockPos).is(FluidTags.WATER); // Paper - Perf: optimize dirt and snow spreading
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this instanceof GrassBlock && level.paperConfig().tickRates.grassSpread != 1 && (level.paperConfig().tickRates.grassSpread < 1 || (io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() + pos.hashCode()) % level.paperConfig().tickRates.grassSpread != 0)) { return; } // Paper - Configurable random tick rates for blocks // Folia - regionised ticking
        // Paper start - Perf: optimize dirt and snow spreading
        final net.minecraft.world.level.chunk.ChunkAccess cachedBlockChunk = level.getChunkIfLoaded(pos);
        if (cachedBlockChunk == null) { // Is this needed?
            return;
        }

        if (!canBeGrass(cachedBlockChunk, state, level, pos)) {
            // Paper end - Perf: optimize dirt and snow spreading
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(level, pos, Blocks.DIRT.defaultBlockState()).isCancelled()) {
                return;
            }
            // CraftBukkit end
            level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        } else {
            if (level.getMaxLocalRawBrightness(pos.above()) >= 9) {
                BlockState blockState = this.defaultBlockState();

                for (int i = 0; i < 4; i++) {
                    BlockPos blockPos = pos.offset(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
                    // Paper start - Perf: optimize dirt and snow spreading
                    if (pos.getX() == blockPos.getX() && pos.getY() == blockPos.getY() && pos.getZ() == blockPos.getZ()) {
                        continue;
                    }

                    final net.minecraft.world.level.chunk.ChunkAccess access;
                    if (cachedBlockChunk.locX == blockPos.getX() >> 4 && cachedBlockChunk.locZ == blockPos.getZ() >> 4) {
                        access = cachedBlockChunk;
                    } else {
                        access = level.getChunkAt(blockPos);
                    }
                    if (access.getBlockState(blockPos).is(Blocks.DIRT) && SpreadingSnowyDirtBlock.canPropagate(access, blockState, level, blockPos)) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, pos, blockPos, blockState.setValue(SNOWY, isSnowySetting(access.getBlockState(blockPos.above()))), Block.UPDATE_ALL); // CraftBukkit
                        // Paper end - Perf: optimize dirt and snow spreading
                    }
                }
            }
        }
    }
}
