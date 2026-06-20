package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.ticks.TickPriority;
import org.jspecify.annotations.Nullable;

public class ComparatorBlock extends DiodeBlock implements EntityBlock {
    public static final MapCodec<ComparatorBlock> CODEC = simpleCodec(ComparatorBlock::new);
    public static final EnumProperty<ComparatorMode> MODE = BlockStateProperties.MODE_COMPARATOR;

    @Override
    public MapCodec<ComparatorBlock> codec() {
        return CODEC;
    }

    public ComparatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(MODE, ComparatorMode.COMPARE));
    }

    @Override
    protected int getDelay(BlockState state) {
        return 2;
    }

    @Override
    public BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        return direction == Direction.DOWN && !this.canSurviveOn(level, neighborPos, neighborState)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected int getOutputSignal(BlockGetter level, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof ComparatorBlockEntity ? ((ComparatorBlockEntity)blockEntity).getOutputSignal() : 0;
    }

    private int calculateOutputSignal(Level level, BlockPos pos, BlockState state) {
        int inputSignal = this.getInputSignal(level, pos, state);
        if (inputSignal == 0) {
            return 0;
        } else {
            int alternateSignal = this.getAlternateSignal(level, pos, state);
            if (alternateSignal > inputSignal) {
                return 0;
            } else {
                return state.getValue(MODE) == ComparatorMode.SUBTRACT ? inputSignal - alternateSignal : inputSignal;
            }
        }
    }

    @Override
    protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
        int inputSignal = this.getInputSignal(level, pos, state);
        if (inputSignal == 0) {
            return false;
        } else {
            int alternateSignal = this.getAlternateSignal(level, pos, state);
            return inputSignal > alternateSignal || inputSignal == alternateSignal && state.getValue(MODE) == ComparatorMode.COMPARE;
        }
    }

    @Override
    protected int getInputSignal(Level level, BlockPos pos, BlockState state) {
        int i = super.getInputSignal(level, pos, state);
        Direction direction = state.getValue(FACING);
        BlockPos blockPos = pos.relative(direction);
        BlockState blockState = level.getBlockState(blockPos);
        if (blockState.hasAnalogOutputSignal()) {
            i = blockState.getAnalogOutputSignal(level, blockPos, direction.getOpposite());
        } else if (i < 15 && blockState.isRedstoneConductor(level, blockPos)) {
            blockPos = blockPos.relative(direction);
            blockState = level.getBlockState(blockPos);
            ItemFrame itemFrame = this.getItemFrame(level, direction, blockPos);
            int max = Math.max(
                itemFrame == null ? Integer.MIN_VALUE : itemFrame.getAnalogOutput(),
                blockState.hasAnalogOutputSignal() ? blockState.getAnalogOutputSignal(level, blockPos, direction.getOpposite()) : Integer.MIN_VALUE
            );
            if (max != Integer.MIN_VALUE) {
                i = max;
            }
        }

        return i;
    }

    private @Nullable ItemFrame getItemFrame(Level level, Direction facing, BlockPos pos) {
        List<ItemFrame> entitiesOfClass = level.getEntitiesOfClass(
            ItemFrame.class,
            new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1),
            itemFrame -> itemFrame.getDirection() == facing
        );
        return entitiesOfClass.size() == 1 ? entitiesOfClass.get(0) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        } else {
            state = state.cycle(MODE);
            float f = state.getValue(MODE) == ComparatorMode.SUBTRACT ? 0.55F : 0.5F;
            level.playSound(player, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, f);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            this.refreshOutputState(level, pos, state);
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    protected void checkTickOnNeighbor(Level level, BlockPos pos, BlockState state) {
        if (!level.getBlockTicks().willTickThisTick(pos, this)) {
            int i = this.calculateOutputSignal(level, pos, state);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            int i1 = blockEntity instanceof ComparatorBlockEntity ? ((ComparatorBlockEntity)blockEntity).getOutputSignal() : 0;
            if (i != i1 || state.getValue(POWERED) != this.shouldTurnOn(level, pos, state)) {
                TickPriority tickPriority = this.shouldPrioritize(level, pos, state) ? TickPriority.HIGH : TickPriority.NORMAL;
                level.scheduleTick(pos, this, 2, tickPriority);
            }
        }
    }

    private void refreshOutputState(Level level, BlockPos pos, BlockState state) {
        int i = this.calculateOutputSignal(level, pos, state);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        int i1 = 0;
        if (blockEntity instanceof ComparatorBlockEntity comparatorBlockEntity) {
            i1 = comparatorBlockEntity.getOutputSignal();
            comparatorBlockEntity.setOutputSignal(i);
        }

        if (i1 != i || state.getValue(MODE) == ComparatorMode.COMPARE) {
            boolean shouldTurnOn = this.shouldTurnOn(level, pos, state);
            boolean poweredValue = state.getValue(POWERED);
            if (poweredValue && !shouldTurnOn) {
                // CraftBukkit start
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(level, pos, 15, 0).getNewCurrent() != 0) {
                    return;
                }
                // CraftBukkit end
                level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_CLIENTS);
            } else if (!poweredValue && shouldTurnOn) {
                // CraftBukkit start
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(level, pos, 0, 15).getNewCurrent() != 15) {
                    return;
                }
                // CraftBukkit end
                level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_CLIENTS);
            }

            this.updateNeighborsInFront(level, pos, state);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        this.refreshOutputState(level, pos, state);
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(id, param);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComparatorBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE, POWERED);
    }
}
