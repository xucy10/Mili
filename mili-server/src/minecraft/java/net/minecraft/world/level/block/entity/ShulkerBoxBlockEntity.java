package net.minecraft.world.level.block.entity;

import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ShulkerBoxBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer, org.leavesmc.leaves.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker, org.leavesmc.leaves.lithium.common.block.entity.SleepingBlockEntity, org.leavesmc.leaves.lithium.api.inventory.LithiumInventory { // Leaves - Lithium Sleeping Block Entity
    public static final int COLUMNS = 9;
    public static final int ROWS = 3;
    public static final int CONTAINER_SIZE = 27;
    public static final int EVENT_SET_OPEN_COUNT = 1;
    public static final int OPENING_TICK_LENGTH = 10;
    public static final float MAX_LID_HEIGHT = 0.5F;
    public static final float MAX_LID_ROTATION = 270.0F;
    private static final int[] SLOTS = IntStream.range(0, 27).toArray();
    private static final Component DEFAULT_NAME = Component.translatable("container.shulkerBox");
    private NonNullList<ItemStack> itemStacks = NonNullList.withSize(27, ItemStack.EMPTY);
    public int openCount;
    private ShulkerBoxBlockEntity.AnimationStatus animationStatus = ShulkerBoxBlockEntity.AnimationStatus.CLOSED;
    private float progress;
    private float progressOld;
    private final @Nullable DyeColor color;

    // CraftBukkit start - add fields and methods
    public List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;
    public boolean opened;

    @Override
    public List<ItemStack> getContents() {
        return this.itemStacks;
    }

    @Override
    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.add(player);
    }

    @Override
    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.remove(player);
    }

    @Override
    public List<org.bukkit.entity.HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    @Override
    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }
    // CraftBukkit end

    public ShulkerBoxBlockEntity(@Nullable DyeColor color, BlockPos pos, BlockState blockState) {
        super(BlockEntityType.SHULKER_BOX, pos, blockState);
        this.color = color;
    }

    public ShulkerBoxBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.SHULKER_BOX, pos, blockState);
        this.color = blockState.getBlock() instanceof ShulkerBoxBlock shulkerBoxBlock ? shulkerBoxBlock.getColor() : null;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ShulkerBoxBlockEntity blockEntity) {
        blockEntity.updateAnimation(level, pos, state);
    }

    private void updateAnimation(Level level, BlockPos pos, BlockState state) {
        this.progressOld = this.progress;
        switch (this.animationStatus) {
            case CLOSED:
                this.progress = 0.0F;
                break;
            case OPENING:
                this.progress += 0.1F;
                if (this.progressOld == 0.0F) {
                    doNeighborUpdates(level, pos, state);
                }

                if (this.progress >= 1.0F) {
                    this.animationStatus = ShulkerBoxBlockEntity.AnimationStatus.OPENED;
                    this.progress = 1.0F;
                    doNeighborUpdates(level, pos, state);
                }

                this.moveCollidedEntities(level, pos, state);
                break;
            case OPENED:
                this.progress = 1.0F;
                break;
            case CLOSING:
                this.progress -= 0.1F;
                if (this.progressOld == 1.0F) {
                    doNeighborUpdates(level, pos, state);
                }

                if (this.progress <= 0.0F) {
                    this.animationStatus = ShulkerBoxBlockEntity.AnimationStatus.CLOSED;
                    this.progress = 0.0F;
                    doNeighborUpdates(level, pos, state);
                }
        }
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && this.animationStatus == ShulkerBoxBlockEntity.AnimationStatus.CLOSED && this.progressOld == 0.0f && this.progress == 0.0f) this.lithium$startSleeping(); // Leaves - Lithium Sleeping Block Entity
    }

    public ShulkerBoxBlockEntity.AnimationStatus getAnimationStatus() {
        return this.animationStatus;
    }

    public AABB getBoundingBox(BlockState state) {
        Vec3 vec3 = new Vec3(0.5, 0.0, 0.5);
        return Shulker.getProgressAabb(1.0F, state.getValue(ShulkerBoxBlock.FACING), 0.5F * this.getProgress(1.0F), vec3);
    }

    private void moveCollidedEntities(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ShulkerBoxBlock) {
            Direction direction = state.getValue(ShulkerBoxBlock.FACING);
            AABB progressDeltaAabb = Shulker.getProgressDeltaAabb(1.0F, direction, this.progressOld, this.progress, pos.getBottomCenter());
            List<Entity> entities = level.getEntities(null, progressDeltaAabb);
            if (!entities.isEmpty()) {
                for (Entity entity : entities) {
                    if (entity.getPistonPushReaction() != PushReaction.IGNORE && !(entity instanceof net.minecraft.world.entity.player.Player player && player.isCreativeFlyOrSpectator())) { // Leaves - creative no clip
                        entity.move(
                            MoverType.SHULKER_BOX,
                            new Vec3(
                                (progressDeltaAabb.getXsize() + 0.01) * direction.getStepX(),
                                (progressDeltaAabb.getYsize() + 0.01) * direction.getStepY(),
                                (progressDeltaAabb.getZsize() + 0.01) * direction.getStepZ()
                            )
                        );
                    }
                }
            }
        }
    }

    @Override
    public int getContainerSize() {
        return this.itemStacks.size();
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && this.sleepingTicker != null) this.wakeUpNow(); // Leaves - Lithium Sleeping Block Entity
        if (id == 1) {
            this.openCount = type;
            if (type == 0) {
                this.animationStatus = ShulkerBoxBlockEntity.AnimationStatus.CLOSING;
            }

            if (type == 1) {
                this.animationStatus = ShulkerBoxBlockEntity.AnimationStatus.OPENING;
            }

            return true;
        } else {
            return super.triggerEvent(id, type);
        }
    }

    private static void doNeighborUpdates(Level level, BlockPos pos, BlockState state) {
        state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        level.updateNeighborsAt(pos, state.getBlock());
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    }

    @Override
    public void startOpen(ContainerUser user) {
        if (!this.remove && !user.getLivingEntity().isSpectator()) {
            if (this.openCount < 0) {
                this.openCount = 0;
            }

            this.openCount++;
            if (this.opened) return; // CraftBukkit - only animate if the ShulkerBox hasn't been forced open already by an API call
            this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), 1, this.openCount);
            if (this.openCount == 1) {
                this.level.gameEvent(user.getLivingEntity(), GameEvent.CONTAINER_OPEN, this.worldPosition);
                this.level
                    .playSound(null, this.worldPosition, SoundEvents.SHULKER_BOX_OPEN, SoundSource.BLOCKS, 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
            }
        }
    }

    @Override
    public void stopOpen(ContainerUser user) {
        if (!this.remove && !user.getLivingEntity().isSpectator()) {
            this.openCount--;
            if (this.opened) return; // CraftBukkit - only animate if the ShulkerBox hasn't been forced open already by an API call.
            this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), 1, this.openCount);
            if (this.openCount <= 0) {
                this.level.gameEvent(user.getLivingEntity(), GameEvent.CONTAINER_CLOSE, this.worldPosition);
                this.level
                    .playSound(null, this.worldPosition, SoundEvents.SHULKER_BOX_CLOSE, SoundSource.BLOCKS, 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
            }
        }
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.loadFromTag(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, this.itemStacks, false);
        }
    }

    public void loadFromTag(ValueInput input) {
        this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, this.itemStacks);
        }
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.itemStacks;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.itemStacks = items;
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) this.lithium$emitStackListReplaced(); // Leaves - Lithium Sleeping Block Entity
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return !(Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return true;
    }

    public float getProgress(float partialTick) {
        return Mth.lerp(partialTick, this.progressOld, this.progress);
    }

    public @Nullable DyeColor getColor() {
        return this.color;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return new ShulkerBoxMenu(id, player, this);
    }

    public boolean isClosed() {
        return this.animationStatus == ShulkerBoxBlockEntity.AnimationStatus.CLOSED;
    }

    public static enum AnimationStatus {
        CLOSED,
        OPENING,
        OPENED,
        CLOSING;
    }

    // Leaves start - Lithium Sleeping Block Entity
    private net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = null;
    private TickingBlockEntity sleepingTicker = null;

    @Override
    public net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper lithium$getTickWrapper() {
        return tickWrapper;
    }

    @Override
    public void lithium$setTickWrapper(net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper) {
        this.tickWrapper = tickWrapper;
    }

    @Override
    public TickingBlockEntity lithium$getSleepingTicker() {
        return sleepingTicker;
    }

    @Override
    public void lithium$setSleepingTicker(TickingBlockEntity sleepingTicker) {
        this.sleepingTicker = sleepingTicker;
    }

    @Override
    public net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> getInventoryLithium() {
        return itemStacks;
    }

    @Override
    public void setInventoryLithium(net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> inventory) {
        itemStacks = inventory;
    }
    // Leaves end - Lithium Sleeping Block Entity

    // Leaves start - pca
    @Override
    public void setChanged() {
        super.setChanged();
        if (fun.bm.mili.config.modules.function.protocol.PcaSyncProtocolConfig.enable) {
            org.leavesmc.leaves.protocol.PcaSyncProtocol.syncBlockEntityToClient(this);
        }
    }
    // Leaves end - pca
}
