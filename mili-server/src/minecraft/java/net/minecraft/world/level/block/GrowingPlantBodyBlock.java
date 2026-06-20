package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class GrowingPlantBodyBlock extends GrowingPlantBlock implements BonemealableBlock {
    protected GrowingPlantBodyBlock(BlockBehaviour.Properties properties, Direction growthDirection, VoxelShape shape, boolean scheduleFluidTicks) {
        super(properties, growthDirection, shape, scheduleFluidTicks);
    }

    @Override
    protected abstract MapCodec<? extends GrowingPlantBodyBlock> codec();

    protected BlockState updateHeadAfterConvertedFromBody(BlockState head, BlockState body) {
        return body;
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        if (direction == this.growthDirection.getOpposite() && !state.canSurvive(level, pos)) {
            scheduledTickAccess.scheduleTick(pos, this, 1);
        }

        GrowingPlantHeadBlock headBlock = this.getHeadBlock();
        if (direction == this.growthDirection && !neighborState.is(this) && !neighborState.is(headBlock)) {
            return this.updateHeadAfterConvertedFromBody(state, headBlock.getStateForPlacement(random));
        } else {
            if (this.scheduleFluidTicks) {
                scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }

            return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(this.getHeadBlock());
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        Optional<BlockPos> headPos = this.getHeadPos(level, pos, state.getBlock());
        return headPos.isPresent() && this.getHeadBlock().canGrowInto(level.getBlockState(headPos.get().relative(this.growthDirection)));
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        Optional<BlockPos> headPos = this.getHeadPos(level, pos, state.getBlock());
        if (headPos.isPresent()) {
            BlockState blockState = level.getBlockState(headPos.get());
            ((GrowingPlantHeadBlock)blockState.getBlock()).performBonemeal(level, random, headPos.get(), blockState);
        }
    }

    private Optional<BlockPos> getHeadPos(BlockGetter level, BlockPos pos, Block block) {
        return BlockUtil.getTopConnectedBlock(level, pos, block, this.growthDirection, this.getHeadBlock());
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        boolean flag = super.canBeReplaced(state, useContext);
        return (!flag || !useContext.getItemInHand().is(this.getHeadBlock().asItem())) && flag;
    }

    @Override
    protected Block getBodyBlock() {
        return this;
    }
}
