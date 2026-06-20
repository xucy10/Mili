package net.minecraft.world.level.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.EquipmentDispenseItemBehavior;
import net.minecraft.core.dispenser.ProjectileDispenseBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class DispenserBlock extends BaseEntityBlock {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<DispenserBlock> CODEC = simpleCodec(DispenserBlock::new);
    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    private static final DefaultDispenseItemBehavior DEFAULT_BEHAVIOR = new DefaultDispenseItemBehavior();
    public static final Map<Item, DispenseItemBehavior> DISPENSER_REGISTRY = new IdentityHashMap<>();
    private static final int TRIGGER_DURATION = 4;

    @Override
    public MapCodec<? extends DispenserBlock> codec() {
        return CODEC;
    }

    public static void registerBehavior(ItemLike item, DispenseItemBehavior behavior) {
        DISPENSER_REGISTRY.put(item.asItem(), behavior);
    }

    public static void registerProjectileBehavior(ItemLike item) {
        DISPENSER_REGISTRY.put(item.asItem(), new ProjectileDispenseBehavior(item.asItem()));
    }

    protected DispenserBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TRIGGERED, false));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DispenserBlockEntity dispenserBlockEntity && player.openMenu(dispenserBlockEntity).isPresent()) { // Paper - Fix InventoryOpenEvent cancellation
            player.awardStat(dispenserBlockEntity instanceof DropperBlockEntity ? Stats.INSPECT_DROPPER : Stats.INSPECT_DISPENSER);
        }

        return InteractionResult.SUCCESS;
    }

    public void dispenseFrom(ServerLevel level, BlockState state, BlockPos pos) {
        DispenserBlockEntity dispenserBlockEntity = level.getBlockEntity(pos, BlockEntityType.DISPENSER).orElse(null);
        if (dispenserBlockEntity == null) {
            LOGGER.warn("Ignoring dispensing attempt for Dispenser without matching block entity at {}", pos);
        } else {
            BlockSource blockSource = new BlockSource(level, pos, state, dispenserBlockEntity);
            int randomSlot = dispenserBlockEntity.getRandomSlot(level.random);
            if (randomSlot < 0) {
                if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFailedDispenseEvent(level, pos)) { // Paper - Add BlockFailedDispenseEvent
                level.levelEvent(LevelEvent.SOUND_DISPENSER_FAIL, pos, 0);
                level.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(dispenserBlockEntity.getBlockState()));
                } // Paper - Add BlockFailedDispenseEvent
            } else {
                ItemStack item = dispenserBlockEntity.getItem(randomSlot);
                DispenseItemBehavior dispenseMethod = this.getDispenseMethod(level, item);
                if (dispenseMethod != DispenseItemBehavior.NOOP) {
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockPreDispenseEvent(level, pos, item, randomSlot)) return; // Paper - Add BlockPreDispenseEvent
                    dispenserBlockEntity.setItem(randomSlot, dispenseMethod.dispense(blockSource, item));
                }
            }
        }
    }

    // Paper start - Fix NPE with equippable and items without behavior
    public static DispenseItemBehavior getDispenseBehavior(BlockSource pointer, ItemStack stack) {
        return ((DispenserBlock) pointer.state().getBlock()).getDispenseMethod(pointer.level(), stack);
    }
    // Paper end - Fix NPE with equippable and items without behavior

    protected DispenseItemBehavior getDispenseMethod(Level level, ItemStack item) {
        if (!item.isItemEnabled(level.enabledFeatures())) {
            return DEFAULT_BEHAVIOR;
        } else {
            DispenseItemBehavior dispenseItemBehavior = DISPENSER_REGISTRY.get(item.getItem());
            return dispenseItemBehavior != null ? dispenseItemBehavior : getDefaultDispenseMethod(item);
        }
    }

    private static DispenseItemBehavior getDefaultDispenseMethod(ItemStack stack) {
        return (DispenseItemBehavior)(stack.has(DataComponents.EQUIPPABLE) ? EquipmentDispenseItemBehavior.INSTANCE : DEFAULT_BEHAVIOR);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        boolean flag = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
        boolean triggeredValue = state.getValue(TRIGGERED);
        if (flag && !triggeredValue) {
            level.scheduleTick(pos, this, 4);
            level.setBlock(pos, state.setValue(TRIGGERED, true), Block.UPDATE_CLIENTS);
        } else if (!flag && triggeredValue) {
            level.setBlock(pos, state.setValue(TRIGGERED, false), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        this.dispenseFrom(level, state, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DispenserBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    public static Position getDispensePosition(BlockSource blockSource) {
        return getDispensePosition(blockSource, 0.7, Vec3.ZERO);
    }

    public static Position getDispensePosition(BlockSource blockSource, double multiplier, Vec3 offset) {
        Direction direction = blockSource.state().getValue(FACING);
        return blockSource.center()
            .add(multiplier * direction.getStepX() + offset.x(), multiplier * direction.getStepY() + offset.y(), multiplier * direction.getStepZ() + offset.z());
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
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
        builder.add(FACING, TRIGGERED);
    }
}
