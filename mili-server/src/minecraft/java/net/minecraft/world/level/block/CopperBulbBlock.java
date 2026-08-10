package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

public class CopperBulbBlock extends Block {
    public static final MapCodec<CopperBulbBlock> CODEC = simpleCodec(CopperBulbBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    @Override
    protected MapCodec<? extends CopperBulbBlock> codec() {
        return CODEC;
    }

    public CopperBulbBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(LIT, false).setValue(POWERED, false));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (oldState.getBlock() != state.getBlock() && level instanceof ServerLevel serverLevel) {
            // Mili start - copper bulb 1 gt delay
            if (!fun.bm.mili.config.modules.function.OldFeatureConfig.copperBulb1gt) {
                this.checkAndFlip(state, serverLevel, pos);
            } else {
                level.scheduleTick(pos, this, 1);
            }
            // Mili end - copper bulb 1 gt delay
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel) {
            // Mili start - copper bulb 1 gt delay
            if (!fun.bm.mili.config.modules.function.OldFeatureConfig.copperBulb1gt) {
                this.checkAndFlip(state, serverLevel, pos);
            } else {
                level.scheduleTick(pos, this, 1);
            }
            // Mili end - copper bulb 1 gt delay
        }
    }

    public void checkAndFlip(BlockState state, ServerLevel level, BlockPos pos) {
        boolean hasNeighborSignal = level.hasNeighborSignal(pos);
        if (hasNeighborSignal != state.getValue(POWERED)) {
            BlockState blockState = state;
            if (!state.getValue(POWERED)) {
                blockState = state.cycle(LIT);
                level.playSound(null, pos, blockState.getValue(LIT) ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.BLOCKS);
            }

            level.setBlock(pos, blockState.setValue(POWERED, hasNeighborSignal), Block.UPDATE_ALL);
        }
    }

    // Mili start - copper bulb 1 gt delay
    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (fun.bm.mili.config.modules.function.OldFeatureConfig.copperBulb1gt) {
            checkAndFlip(state, world, pos);
        }
    }
    // Mili end - copper bulb 1 gt delay

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, POWERED);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockState(pos).getValue(LIT) ? 15 : 0;
    }
}
