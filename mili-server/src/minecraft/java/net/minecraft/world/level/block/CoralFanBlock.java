package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class CoralFanBlock extends BaseCoralFanBlock {
    public static final MapCodec<CoralFanBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(CoralBlock.DEAD_CORAL_FIELD.forGetter(coralFanBlock -> coralFanBlock.deadBlock), propertiesCodec())
            .apply(instance, CoralFanBlock::new)
    );
    private final Block deadBlock;

    @Override
    public MapCodec<CoralFanBlock> codec() {
        return CODEC;
    }

    protected CoralFanBlock(Block deadBlock, BlockBehaviour.Properties properties) {
        super(properties);
        this.deadBlock = deadBlock;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        this.tryScheduleDieTick(state, level, level, level.random, pos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!scanForWater(state, level, pos)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(level, pos, this.deadBlock.defaultBlockState().setValue(CoralFanBlock.WATERLOGGED, false)).isCancelled()) {
                return;
            }
            // CraftBukkit end
            level.setBlock(pos, this.deadBlock.defaultBlockState().setValue(WATERLOGGED, false), Block.UPDATE_CLIENTS);
        }
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
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            this.tryScheduleDieTick(state, level, scheduledTickAccess, random, pos);
            if (state.getValue(WATERLOGGED)) {
                scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }

            return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        }
    }
}
