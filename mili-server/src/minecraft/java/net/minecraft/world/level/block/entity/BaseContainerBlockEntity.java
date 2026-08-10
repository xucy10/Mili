package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.LockCode;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
// Leaves start - Lithium Sleeping Block Entity
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import org.leavesmc.leaves.lithium.api.inventory.LithiumInventory;
import org.leavesmc.leaves.lithium.common.block.entity.inventory_change_tracking.InventoryChangeEmitter;
import org.leavesmc.leaves.lithium.common.block.entity.inventory_change_tracking.InventoryChangeListener;
import org.leavesmc.leaves.lithium.common.block.entity.inventory_change_tracking.InventoryChangeTracker;
import org.leavesmc.leaves.lithium.common.hopper.InventoryHelper;
import org.leavesmc.leaves.lithium.common.hopper.LithiumStackList;
// Leaves end - Lithium Sleeping Block Entity

public abstract class BaseContainerBlockEntity extends BlockEntity implements Container, MenuProvider, Nameable, InventoryChangeEmitter {
    public LockCode lockKey = LockCode.NO_LOCK;
    public @Nullable Component name;

    protected BaseContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.lockKey = LockCode.fromTag(input);
        this.name = parseCustomNameSafe(input, "CustomName");
        if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && this instanceof InventoryChangeTracker inventoryChangeTracker) inventoryChangeTracker.lithium$emitStackListReplaced(); // Leaves - Lithium Sleeping Block Entity
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.lockKey.addToTag(output);
        output.storeNullable("CustomName", ComponentSerialization.CODEC, this.name);
    }

    @Override
    public Component getName() {
        return this.name != null ? this.name : this.getDefaultName();
    }

    @Override
    public Component getDisplayName() {
        return this.getName();
    }

    @Override
    public @Nullable Component getCustomName() {
        return this.name;
    }

    protected abstract Component getDefaultName();

    public boolean canOpen(Player player) {
        return org.bukkit.craftbukkit.event.CraftEventFactory.callBlockLockCheckEvent(this, this.lockKey, this.getDisplayName(), player); // Paper - Call BlockLockCheckEvent
    }

    public static void sendChestLockedNotifications(Vec3 pos, Player player, Component displayName) {
        // Paper start - BlockLockCheckEvent
        if (org.bukkit.craftbukkit.event.CraftEventFactory.sendChestLockedNotifications(pos)) {
            return;
        }
        // Paper end - BlockLockCheckEvent
        Level level = player.level();
        player.displayClientMessage(Component.translatable("container.isLocked", displayName), true); // Paper - diff on change
        if (!level.isClientSide()) {
            level.playSound(null, pos.x(), pos.y(), pos.z(), SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F); // Paper - diff on change
        }
    }

    public boolean isLocked() {
        return !this.lockKey.equals(LockCode.NO_LOCK);
    }

    protected abstract NonNullList<ItemStack> getItems();

    protected abstract void setItems(NonNullList<ItemStack> items);

    @Override
    public boolean isEmpty() {
        for (ItemStack itemStack : this.getItems()) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.getItems().get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack itemStack = ContainerHelper.removeItem(this.getItems(), slot, amount);
        if (!itemStack.isEmpty()) {
            this.setChanged();
        }

        return itemStack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.getItems(), slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.getItems().set(slot, stack);
        stack.limitSize(this.getMaxStackLeaves(stack)); // Mili - item over-stack util
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.getItems().clear();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (this.canOpen(player)) {
            return this.createMenu(containerId, playerInventory);
        } else {
            sendChestLockedNotifications(this.getBlockPos().getCenter(), player, this.getDisplayName());
            return null;
        }
    }

    protected abstract AbstractContainerMenu createMenu(int containerId, Inventory inventory);

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        super.applyImplicitComponents(componentGetter);
        this.name = componentGetter.get(DataComponents.CUSTOM_NAME);
        this.lockKey = componentGetter.getOrDefault(DataComponents.LOCK, LockCode.NO_LOCK);
        componentGetter.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(this.getItems());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, this.name);
        if (this.isLocked()) {
            components.set(DataComponents.LOCK, this.lockKey);
        }

        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("CustomName");
        output.discard("lock");
        output.discard("Items");
    }

    // CraftBukkit start
    @Override
    public org.bukkit.@Nullable Location getLocation() {
        if (this.level == null) return null;
        return org.bukkit.craftbukkit.util.CraftLocation.toBukkit(this.worldPosition, this.level);
    }
    // CraftBukkit end

    // Leaves start - Lithium Sleeping Block Entity
    ReferenceArraySet<InventoryChangeListener> inventoryChangeListeners = null;
    ReferenceArraySet<InventoryChangeListener> inventoryHandlingTypeListeners = null;

    @Override
    public void lithium$emitContentModified() {
        ReferenceArraySet<InventoryChangeListener> inventoryChangeListeners = this.inventoryChangeListeners;
        if (inventoryChangeListeners != null) {
            for (InventoryChangeListener inventoryChangeListener : inventoryChangeListeners) {
                inventoryChangeListener.lithium$handleInventoryContentModified(this);
            }
            inventoryChangeListeners.clear();
        }
    }

    @Override
    public void lithium$emitStackListReplaced() {
        ReferenceArraySet<InventoryChangeListener> listeners = this.inventoryHandlingTypeListeners;
        if (listeners != null && !listeners.isEmpty()) {
            for (InventoryChangeListener inventoryChangeListener : listeners) {
                inventoryChangeListener.handleStackListReplaced(this);
            }
            listeners.clear();
        }

        if (this instanceof InventoryChangeListener listener) {
            listener.handleStackListReplaced(this);
        }

        this.invalidateChangeListening();
    }

    @Override
    public void lithium$emitRemoved() {
        ReferenceArraySet<InventoryChangeListener> listeners = this.inventoryHandlingTypeListeners;
        if (listeners != null && !listeners.isEmpty()) {
            for (InventoryChangeListener listener : listeners) {
                listener.lithium$handleInventoryRemoved(this);
            }
            listeners.clear();
        }

        if (this instanceof InventoryChangeListener listener) {
            listener.lithium$handleInventoryRemoved(this);
        }

        this.invalidateChangeListening();
    }

    private void invalidateChangeListening() {
        if (this.inventoryChangeListeners != null) {
            this.inventoryChangeListeners.clear();
        }

        LithiumStackList lithiumStackList = this instanceof LithiumInventory ? InventoryHelper.getLithiumStackListOrNull((LithiumInventory) this) : null;
        if (lithiumStackList != null && this instanceof InventoryChangeTracker inventoryChangeTracker) {
            lithiumStackList.removeInventoryModificationCallback(inventoryChangeTracker);
        }
    }

    @Override
    public void lithium$emitFirstComparatorAdded() {
        ReferenceArraySet<InventoryChangeListener> inventoryChangeListeners = this.inventoryChangeListeners;
        if (inventoryChangeListeners != null && !inventoryChangeListeners.isEmpty()) {
            inventoryChangeListeners.removeIf(inventoryChangeListener -> inventoryChangeListener.lithium$handleComparatorAdded(this));
        }
    }

    @Override
    public void lithium$forwardContentChangeOnce(InventoryChangeListener inventoryChangeListener, LithiumStackList stackList, InventoryChangeTracker thisTracker) {
        if (this.inventoryChangeListeners == null) {
            this.inventoryChangeListeners = new ReferenceArraySet<>(1);
        }
        stackList.setInventoryModificationCallback(thisTracker);
        this.inventoryChangeListeners.add(inventoryChangeListener);

    }

    @Override
    public void lithium$forwardMajorInventoryChanges(InventoryChangeListener inventoryChangeListener) {
        if (this.inventoryHandlingTypeListeners == null) {
            this.inventoryHandlingTypeListeners = new ReferenceArraySet<>(1);
        }
        this.inventoryHandlingTypeListeners.add(inventoryChangeListener);
    }

    @Override
    public void lithium$stopForwardingMajorInventoryChanges(InventoryChangeListener inventoryChangeListener) {
        if (this.inventoryHandlingTypeListeners != null) {
            this.inventoryHandlingTypeListeners.remove(inventoryChangeListener);
        }
    }
}
