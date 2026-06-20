package net.minecraft.world.level.block.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ChestBlockEntity extends RandomizableContainerBlockEntity implements LidBlockEntity, org.leavesmc.leaves.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker, org.leavesmc.leaves.lithium.common.block.entity.inventory_change_tracking.InventoryChangeEmitter, org.leavesmc.leaves.lithium.common.block.entity.SetBlockStateHandlingBlockEntity, org.leavesmc.leaves.lithium.common.block.entity.SleepingBlockEntity, org.leavesmc.leaves.lithium.api.inventory.LithiumInventory { // Leaves - Lithium Sleeping Block Entity
    private static final int EVENT_SET_OPEN_COUNT = 1;
    public static final Component DEFAULT_NAME = Component.translatable("container.chest");
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    public final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            if (state.getBlock() instanceof ChestBlock chestBlock) {
                ChestBlockEntity.playSound(level, pos, state, chestBlock.getOpenChestSound());
            }
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            if (state.getBlock() instanceof ChestBlock chestBlock) {
                ChestBlockEntity.playSound(level, pos, state, chestBlock.getCloseChestSound());
            }
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int count, int openCount) {
            ChestBlockEntity.this.signalOpenCount(level, pos, state, count, openCount);
        }

        @Override
        public boolean isOwnContainer(Player player) {
            if (!(player.containerMenu instanceof ChestMenu)) {
                return false;
            } else {
                Container container = ((ChestMenu)player.containerMenu).getContainer();
                return container == ChestBlockEntity.this
                    || container instanceof CompoundContainer && ((CompoundContainer)container).contains(ChestBlockEntity.this);
            }
        }
    };
    private final ChestLidController chestLidController = new ChestLidController();

    // CraftBukkit start - add fields and methods
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;

    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getContents() {
        return this.items;
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
    public java.util.List<org.bukkit.entity.HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }
    // CraftBukkit end

    protected ChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public ChestBlockEntity(BlockPos pos, BlockState blockState) {
        this(BlockEntityType.CHEST, pos, blockState);
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, this.items);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, this.items);
        }
    }

    public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, ChestBlockEntity blockEntity) {
        blockEntity.chestLidController.tickLid();
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) blockEntity.checkSleep(); // Leaves - Lithium Sleeping Block Entity
    }

    public static void playSound(Level level, BlockPos pos, BlockState state, SoundEvent sound) {
        ChestType chestType = state.getValue(ChestBlock.TYPE);
        if (chestType != ChestType.LEFT) {
            double d = pos.getX() + 0.5;
            double d1 = pos.getY() + 0.5;
            double d2 = pos.getZ() + 0.5;
            if (chestType == ChestType.RIGHT) {
                Direction connectedDirection = ChestBlock.getConnectedDirection(state);
                d += connectedDirection.getStepX() * 0.5;
                d2 += connectedDirection.getStepZ() * 0.5;
            }

            level.playSound(null, d, d1, d2, sound, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
        }
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == 1) {
            if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && this.sleepingTicker != null) this.wakeUpNow(); // Leaves - Lithium Sleeping Block Entity
            this.chestLidController.shouldBeOpen(type > 0);
            return true;
        } else {
            return super.triggerEvent(id, type);
        }
    }

    @Override
    public void startOpen(ContainerUser user) {
        if (!this.remove && !user.getLivingEntity().isSpectator()) {
            this.openersCounter
                .incrementOpeners(user.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState(), user.getContainerInteractionRange());
        }
    }

    @Override
    public void stopOpen(ContainerUser user) {
        if (!this.remove && !user.getLivingEntity().isSpectator()) {
            this.openersCounter.decrementOpeners(user.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public List<ContainerUser> getEntitiesWithContainerOpen() {
        return this.openersCounter.getEntitiesWithContainerOpen(this.getLevel(), this.getBlockPos());
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled) this.lithium$emitStackListReplaced(); // Leaves - Lithium Sleeping Block Entity
    }

    @Override
    public float getOpenNess(float partialTick) {
        return this.chestLidController.getOpenness(partialTick);
    }

    public static int getOpenCount(BlockGetter level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.hasBlockEntity()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ChestBlockEntity) {
                return ((ChestBlockEntity)blockEntity).openersCounter.getOpenerCount();
            }
        }

        return 0;
    }

    public static void swapContents(ChestBlockEntity chest, ChestBlockEntity otherChest) {
        NonNullList<ItemStack> items = chest.getItems();
        chest.setItems(otherChest.getItems());
        otherChest.setItems(items);
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return ChestMenu.threeRows(id, player, this);
    }

    public void recheckOpen() {
        if (!this.remove) {
            this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    protected void signalOpenCount(Level level, BlockPos pos, BlockState state, int eventId, int eventParam) {
        Block block = state.getBlock();
        level.blockEvent(pos, block, 1, eventParam);
    }

    // Leaves start - Lithium Sleeping Block Entity
    private net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = null;
    private TickingBlockEntity sleepingTicker = null;

    private void checkSleep() {
        //If the animation is finished, it will stay unchanged until the next triggerEvent, which may change shouldBeOpen
        if (this.getOpenNess(0.0F) == this.getOpenNess(1.0F)) {
            this.lithium$startSleeping();
        }
    }

    @Override
    public net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper lithium$getTickWrapper() {
        return this.tickWrapper;
    }

    @Override
    public void lithium$setTickWrapper(net.minecraft.world.level.chunk.LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper) {
        this.tickWrapper = tickWrapper;
    }

    @Override
    public TickingBlockEntity lithium$getSleepingTicker() {
        return this.sleepingTicker;
    }

    @Override
    public void lithium$setSleepingTicker(TickingBlockEntity sleepingTicker) {
        this.sleepingTicker = sleepingTicker;
    }

    @Override
    public void lithium$handleSetBlockState() {
        //Handle switching double / single chest state
        this.lithium$emitRemoved();
    }

    @Override
    public net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> getInventoryLithium() {
        return items;
    }

    @Override
    public void setInventoryLithium(net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> inventory) {
        items = inventory;
    }
    // Leaves end - Lithium Sleeping Block Entity
}
