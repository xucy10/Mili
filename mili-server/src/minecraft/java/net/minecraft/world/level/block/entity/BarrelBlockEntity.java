package net.minecraft.world.level.block.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class BarrelBlockEntity extends RandomizableContainerBlockEntity implements org.leavesmc.leaves.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker, org.leavesmc.leaves.lithium.api.inventory.LithiumInventory { // Leaves - Lithium Sleeping Block Entity
    // CraftBukkit start - add fields and methods
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;

    @Override
    public java.util.List<ItemStack> getContents() {
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

    @Override
    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }
    // CraftBukkit end
    private static final Component DEFAULT_NAME = Component.translatable("container.barrel");
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    public final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            BarrelBlockEntity.this.playSound(state, SoundEvents.BARREL_OPEN);
            BarrelBlockEntity.this.updateBlockState(state, true);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            BarrelBlockEntity.this.playSound(state, SoundEvents.BARREL_CLOSE);
            BarrelBlockEntity.this.updateBlockState(state, false);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int count, int openCount) {
        }

        @Override
        public boolean isOwnContainer(Player player) {
            if (player.containerMenu instanceof ChestMenu) {
                Container container = ((ChestMenu)player.containerMenu).getContainer();
                return container == BarrelBlockEntity.this;
            } else {
                return false;
            }
        }
    };

    public BarrelBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.BARREL, pos, blockState);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, this.items);
        }
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
    public int getContainerSize() {
        return 27;
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
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return ChestMenu.threeRows(id, player, this);
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

    public void recheckOpen() {
        if (!this.remove) {
            this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    public void updateBlockState(BlockState state, boolean _open) {
        this.level.setBlock(this.getBlockPos(), state.setValue(BarrelBlock.OPEN, _open), Block.UPDATE_ALL);
    }

    public void playSound(BlockState state, SoundEvent sound) {
        Vec3i unitVec3i = state.getValue(BarrelBlock.FACING).getUnitVec3i();
        double d = this.worldPosition.getX() + 0.5 + unitVec3i.getX() / 2.0;
        double d1 = this.worldPosition.getY() + 0.5 + unitVec3i.getY() / 2.0;
        double d2 = this.worldPosition.getZ() + 0.5 + unitVec3i.getZ() / 2.0;
        this.level.playSound(null, d, d1, d2, sound, SoundSource.BLOCKS, 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
    }

    // Leaves start - Lithium Sleeping Block Entity


    @Override
    public net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> getInventoryLithium() {
        return items;
    }

    @Override
    public void setInventoryLithium(net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> inventory) {
        items = inventory;
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
