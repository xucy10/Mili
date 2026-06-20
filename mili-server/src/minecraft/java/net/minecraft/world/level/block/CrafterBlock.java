package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeCache;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class CrafterBlock extends BaseEntityBlock {
    public static final MapCodec<CrafterBlock> CODEC = simpleCodec(CrafterBlock::new);
    public static final BooleanProperty CRAFTING = BlockStateProperties.CRAFTING;
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    private static final EnumProperty<FrontAndTop> ORIENTATION = BlockStateProperties.ORIENTATION;
    private static final int MAX_CRAFTING_TICKS = 6;
    private static final int CRAFTING_TICK_DELAY = 4;
    private static final RecipeCache RECIPE_CACHE = new RecipeCache(10);
    private static final int CRAFTER_ADVANCEMENT_DIAMETER = 17;

    public CrafterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ORIENTATION, FrontAndTop.NORTH_UP).setValue(TRIGGERED, false).setValue(CRAFTING, false));
    }

    @Override
    protected MapCodec<CrafterBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof CrafterBlockEntity crafterBlockEntity ? crafterBlockEntity.getRedstoneSignal() : 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        boolean hasNeighborSignal = level.hasNeighborSignal(pos);
        boolean triggeredValue = state.getValue(TRIGGERED);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (hasNeighborSignal && !triggeredValue) {
            level.scheduleTick(pos, this, 4);
            level.setBlock(pos, state.setValue(TRIGGERED, true), Block.UPDATE_CLIENTS);
            this.setBlockEntityTriggered(blockEntity, true);
        } else if (!hasNeighborSignal && triggeredValue) {
            level.setBlock(pos, state.setValue(TRIGGERED, false).setValue(CRAFTING, false), Block.UPDATE_CLIENTS);
            this.setBlockEntityTriggered(blockEntity, false);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        this.dispenseFrom(state, level, pos);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide() ? null : createTickerHelper(blockEntityType, BlockEntityType.CRAFTER, CrafterBlockEntity::serverTick);
    }

    private void setBlockEntityTriggered(@Nullable BlockEntity blockEntity, boolean triggered) {
        if (blockEntity instanceof CrafterBlockEntity crafterBlockEntity) {
            crafterBlockEntity.setTriggered(triggered);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        CrafterBlockEntity crafterBlockEntity = new CrafterBlockEntity(pos, state);
        crafterBlockEntity.setTriggered(state.hasProperty(TRIGGERED) && state.getValue(TRIGGERED));
        return crafterBlockEntity;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction opposite = context.getNearestLookingDirection().getOpposite();

        Direction direction = switch (opposite) {
            case DOWN -> context.getHorizontalDirection().getOpposite();
            case UP -> context.getHorizontalDirection();
            case NORTH, SOUTH, WEST, EAST -> Direction.UP;
        };
        return this.defaultBlockState()
            .setValue(ORIENTATION, FrontAndTop.fromFrontAndTop(opposite, direction))
            .setValue(TRIGGERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (state.getValue(TRIGGERED)) {
            level.scheduleTick(pos, this, 4);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CrafterBlockEntity crafterBlockEntity) {
            player.openMenu(crafterBlockEntity);
        }

        return InteractionResult.SUCCESS;
    }

    protected void dispenseFrom(BlockState state, ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CrafterBlockEntity crafterBlockEntity) {
            CraftingInput var11 = crafterBlockEntity.asCraftInput();
            Optional<RecipeHolder<CraftingRecipe>> potentialResults = getPotentialResults(level, var11);
            if (potentialResults.isEmpty()) {
                level.levelEvent(LevelEvent.SOUND_CRAFTER_FAIL, pos, 0);
            } else {
                RecipeHolder<CraftingRecipe> recipeHolder = potentialResults.get();
                ItemStack itemStack = recipeHolder.value().assemble(var11, level.registryAccess());
                // CraftBukkit start
                org.bukkit.event.block.CrafterCraftEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callCrafterCraftEvent(pos, level, itemStack, recipeHolder);
                if (event.isCancelled()) {
                    return;
                }
                itemStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getResult());
                // CraftBukkit end
                if (itemStack.isEmpty()) {
                    level.levelEvent(LevelEvent.SOUND_CRAFTER_FAIL, pos, 0);
                } else {
                    crafterBlockEntity.setCraftingTicksRemaining(6);
                    level.setBlock(pos, state.setValue(CRAFTING, true), Block.UPDATE_CLIENTS);
                    itemStack.onCraftedBySystem(level);
                    this.dispenseItem(level, pos, crafterBlockEntity, itemStack, state, recipeHolder);

                    for (ItemStack itemStack1 : recipeHolder.value().getRemainingItems(var11)) {
                        if (!itemStack1.isEmpty()) {
                            this.dispenseItem(level, pos, crafterBlockEntity, itemStack1, state, recipeHolder);
                        }
                    }

                    crafterBlockEntity.getItems().forEach(stack -> {
                        if (!stack.isEmpty()) {
                            stack.shrink(1);
                        }
                    });
                    crafterBlockEntity.setChanged();
                }
            }
        }
    }

    public static Optional<RecipeHolder<CraftingRecipe>> getPotentialResults(ServerLevel level, CraftingInput craftingInput) {
        return RECIPE_CACHE.get(level, craftingInput);
    }

    private void dispenseItem(ServerLevel level, BlockPos pos, CrafterBlockEntity crafter, ItemStack stack, BlockState state, RecipeHolder<?> recipe) {
        Direction direction = state.getValue(ORIENTATION).front();
        Container containerAt = HopperBlockEntity.getContainerAt(level, pos.relative(direction));
        ItemStack itemStack = stack.copy();
        if (containerAt != null && (containerAt instanceof CrafterBlockEntity || stack.getCount() > containerAt.getMaxStackSize(stack))) {
            // CraftBukkit start - InventoryMoveItemEvent
            org.bukkit.craftbukkit.inventory.CraftItemStack oitemstack = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack);

            org.bukkit.inventory.Inventory destinationInventory;
            // Have to special case large chests as they work oddly
            if (containerAt instanceof net.minecraft.world.CompoundContainer compoundContainer) {
                destinationInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest(compoundContainer);
            } else {
                destinationInventory = containerAt.getOwner().getInventory();
            }

            org.bukkit.event.inventory.InventoryMoveItemEvent event = new org.bukkit.event.inventory.InventoryMoveItemEvent(crafter.getOwner().getInventory(), oitemstack, destinationInventory, true);
            level.getCraftServer().getPluginManager().callEvent(event);
            itemStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
            while (!itemStack.isEmpty()) {
                if (event.isCancelled()) {
                    break;
                }
                // CraftBukkit end
                ItemStack itemStack1 = itemStack.copyWithCount(1);
                ItemStack itemStack2 = HopperBlockEntity.addItem(crafter, containerAt, itemStack1, direction.getOpposite());
                if (!itemStack2.isEmpty()) {
                    break;
                }

                itemStack.shrink(1);
            }
        } else if (containerAt != null) {
            // CraftBukkit start - InventoryMoveItemEvent
            org.bukkit.craftbukkit.inventory.CraftItemStack oitemstack = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack);

            org.bukkit.inventory.Inventory destinationInventory;
            // Have to special case large chests as they work oddly
            if (containerAt instanceof net.minecraft.world.CompoundContainer compoundContainer) {
                destinationInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest(compoundContainer);
            } else {
                destinationInventory = containerAt.getOwner().getInventory();
            }

            org.bukkit.event.inventory.InventoryMoveItemEvent event = new org.bukkit.event.inventory.InventoryMoveItemEvent(crafter.getOwner().getInventory(), oitemstack, destinationInventory, true);
            level.getCraftServer().getPluginManager().callEvent(event);
            itemStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItem());
            while (!itemStack.isEmpty()) {
                if (event.isCancelled()) {
                    break;
                }
                // CraftBukkit end
                int count = itemStack.getCount();
                itemStack = HopperBlockEntity.addItem(crafter, containerAt, itemStack, direction.getOpposite());
                if (count == itemStack.getCount()) {
                    break;
                }
            }
        }

        if (!itemStack.isEmpty()) {
            Vec3 vec3 = Vec3.atCenterOf(pos);
            Vec3 vec31 = vec3.relative(direction, 0.7);
            DefaultDispenseItemBehavior.spawnItem(level, itemStack, 6, direction, vec31);

            for (ServerPlayer serverPlayer : level.getEntitiesOfClass(ServerPlayer.class, AABB.ofSize(vec3, 17.0, 17.0, 17.0))) {
                CriteriaTriggers.CRAFTER_RECIPE_CRAFTED.trigger(serverPlayer, recipe.id(), crafter.getItems());
            }

            level.levelEvent(LevelEvent.SOUND_CRAFTER_CRAFT, pos, 0);
            level.levelEvent(LevelEvent.PARTICLES_SHOOT_WHITE_SMOKE, pos, direction.get3DDataValue());
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ORIENTATION, rotation.rotation().rotate(state.getValue(ORIENTATION)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ORIENTATION, mirror.rotation().rotate(state.getValue(ORIENTATION)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ORIENTATION, TRIGGERED, CRAFTING);
    }
}
