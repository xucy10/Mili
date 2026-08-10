package net.minecraft.world.level.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class CommandBlock extends BaseEntityBlock implements GameMasterBlock {
    public static final MapCodec<CommandBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Codec.BOOL.fieldOf("automatic").forGetter(commandBlock -> commandBlock.automatic), propertiesCodec())
            .apply(instance, CommandBlock::new)
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
    public static final BooleanProperty CONDITIONAL = BlockStateProperties.CONDITIONAL;
    private final boolean automatic;

    @Override
    public MapCodec<CommandBlock> codec() {
        return CODEC;
    }

    public CommandBlock(boolean automatic, BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CONDITIONAL, false));
        this.automatic = automatic;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        CommandBlockEntity commandBlockEntity = new CommandBlockEntity(pos, state);
        commandBlockEntity.setAutomatic(this.automatic);
        return commandBlockEntity;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof CommandBlockEntity commandBlockEntity) {
                this.setPoweredAndUpdate(level, pos, commandBlockEntity, level.hasNeighborSignal(pos));
            }
        }
    }

    private void setPoweredAndUpdate(Level level, BlockPos pos, CommandBlockEntity blockEntity, boolean powered) {
        boolean isPowered = blockEntity.isPowered();
        // CraftBukkit start
        org.bukkit.block.Block bukkitBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
        int old = isPowered ? 15 : 0;
        int current = powered ? 15 : 0;

        org.bukkit.event.block.BlockRedstoneEvent eventRedstone = new org.bukkit.event.block.BlockRedstoneEvent(bukkitBlock, old, current);
        level.getCraftServer().getPluginManager().callEvent(eventRedstone);
        powered = eventRedstone.getNewCurrent() > 0;
        // CraftBukkit end
        if (powered != isPowered) {
            blockEntity.setPowered(powered);
            if (powered) {
                if (blockEntity.isAutomatic() || blockEntity.getMode() == CommandBlockEntity.Mode.SEQUENCE) {
                    return;
                }

                blockEntity.markConditionMet();
                level.scheduleTick(pos, this, 1);
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof CommandBlockEntity commandBlockEntity) {
            BaseCommandBlock commandBlock = commandBlockEntity.getCommandBlock();
            boolean flag = !StringUtil.isNullOrEmpty(commandBlock.getCommand());
            CommandBlockEntity.Mode mode = commandBlockEntity.getMode();
            boolean wasConditionMet = commandBlockEntity.wasConditionMet();
            if (mode == CommandBlockEntity.Mode.AUTO) {
                commandBlockEntity.markConditionMet();
                if (wasConditionMet) {
                    this.execute(state, level, pos, commandBlock, flag);
                } else if (commandBlockEntity.isConditional()) {
                    commandBlock.setSuccessCount(0);
                }

                if (commandBlockEntity.isPowered() || commandBlockEntity.isAutomatic()) {
                    level.scheduleTick(pos, this, 1);
                }
            } else if (mode == CommandBlockEntity.Mode.REDSTONE) {
                if (wasConditionMet) {
                    this.execute(state, level, pos, commandBlock, flag);
                } else if (commandBlockEntity.isConditional()) {
                    commandBlock.setSuccessCount(0);
                }
            }

            level.updateNeighbourForOutputSignal(pos, this);
        }
    }

    private void execute(BlockState state, ServerLevel level, BlockPos pos, BaseCommandBlock logic, boolean canTrigger) {
        if (canTrigger) {
            logic.performCommand(level);
        } else {
            logic.setSuccessCount(0);
        }

        executeChain(level, pos, state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof CommandBlockEntity && (player.canUseGameMasterBlocks() || (player.isCreative() && player.getBukkitEntity().hasPermission("minecraft.commandblock")))) { // Paper - command block permission
            player.openCommandBlock((CommandBlockEntity)blockEntity);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof CommandBlockEntity ? ((CommandBlockEntity)blockEntity).getCommandBlock().getSuccessCount() : 0;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof CommandBlockEntity commandBlockEntity) {
            BaseCommandBlock commandBlock = commandBlockEntity.getCommandBlock();
            if (level instanceof ServerLevel serverLevel) {
                if (!stack.has(DataComponents.BLOCK_ENTITY_DATA)) {
                    commandBlock.setTrackOutput(serverLevel.getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK));
                    commandBlockEntity.setAutomatic(this.automatic);
                }

                boolean hasNeighborSignal = level.hasNeighborSignal(pos);
                this.setPoweredAndUpdate(level, pos, commandBlockEntity, hasNeighborSignal);
            }
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONDITIONAL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    private static void executeChain(ServerLevel level, BlockPos pos, Direction direction) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
        GameRules gameRules = level.getGameRules();
        int i = gameRules.get(GameRules.MAX_COMMAND_SEQUENCE_LENGTH);

        while (i-- > 0) {
            mutableBlockPos.move(direction);
            BlockState blockState = level.getBlockState(mutableBlockPos);
            Block block = blockState.getBlock();
            if (!blockState.is(Blocks.CHAIN_COMMAND_BLOCK)
                || !(level.getBlockEntity(mutableBlockPos) instanceof CommandBlockEntity commandBlockEntity)
                || commandBlockEntity.getMode() != CommandBlockEntity.Mode.SEQUENCE) {
                break;
            }

            if (commandBlockEntity.isPowered() || commandBlockEntity.isAutomatic()) {
                BaseCommandBlock commandBlock = commandBlockEntity.getCommandBlock();
                if (commandBlockEntity.markConditionMet()) {
                    if (!commandBlock.performCommand(level)) {
                        break;
                    }

                    level.updateNeighbourForOutputSignal(mutableBlockPos, block);
                } else if (commandBlockEntity.isConditional()) {
                    commandBlock.setSuccessCount(0);
                }
            }

            direction = blockState.getValue(FACING);
        }

        if (i <= 0) {
            int max = Math.max(gameRules.get(GameRules.MAX_COMMAND_SEQUENCE_LENGTH), 0);
            LOGGER.warn("Command Block chain tried to execute more than {} steps!", max);
        }
    }
}
