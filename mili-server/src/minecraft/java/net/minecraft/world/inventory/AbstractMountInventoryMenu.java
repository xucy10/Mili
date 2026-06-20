package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public abstract class AbstractMountInventoryMenu extends AbstractContainerMenu {
    protected final Container mountContainer;
    public final LivingEntity mount;
    public static final int SLOT_SADDLE = 0; // Paper - fix missing static
    public static final int SLOT_BODY_ARMOR = 1; // Paper - fix missing static
    public static final int SLOT_INVENTORY_START = 2; // Paper - fix missing static
    protected static final int INVENTORY_ROWS = 3;

    // CraftBukkit start
    private @javax.annotation.Nullable org.bukkit.craftbukkit.inventory.CraftInventoryView view;
    private final Inventory inventory;

    @Override
    public org.bukkit.inventory.InventoryView getBukkitView() {
        if (this.view != null) {
            return this.view;
        }

        return this.view = new org.bukkit.craftbukkit.inventory.CraftInventoryView(this.inventory.player.getBukkitEntity(), this.mountContainer.getOwner().getInventory(), this);
    }
    // CraftBukkit end

    protected AbstractMountInventoryMenu(int containerId, Inventory playerInventory, Container mountContainer, LivingEntity mount) {
        super(null, containerId);
        this.inventory = playerInventory; // CraftBukkit
        this.mountContainer = mountContainer;
        this.mount = mount;
        mountContainer.startOpen(playerInventory.player);
    }

    protected abstract boolean hasInventoryChanged(Container oldInventory);

    @Override
    public boolean stillValid(Player player) {
        return !this.hasInventoryChanged(this.mountContainer)
            && this.mountContainer.stillValid(player)
            && this.mount.isAlive()
            && player.isWithinEntityInteractionRange(this.mount, 4.0);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.mountContainer.stopOpen(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            int i = 2 + this.mountContainer.getContainerSize();
            if (slotIndex < i) {
                if (!this.moveItemStackTo(item, i, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(1).mayPlace(item) && !this.getSlot(1).hasItem()) {
                if (!this.moveItemStackTo(item, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(0).mayPlace(item) && !this.getSlot(0).hasItem()) {
                if (!this.moveItemStackTo(item, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.mountContainer.getContainerSize() == 0 || !this.moveItemStackTo(item, 2, i, false)) {
                int i1 = i + 27;
                int i3 = i1 + 9;
                if (slotIndex >= i1 && slotIndex < i3) {
                    if (!this.moveItemStackTo(item, i, i1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex >= i && slotIndex < i1) {
                    if (!this.moveItemStackTo(item, i1, i3, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(item, i1, i1, false)) {
                    return ItemStack.EMPTY;
                }

                return ItemStack.EMPTY;
            }

            if (item.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemStack;
    }

    public static int getInventorySize(int inventoryColumns) {
        return inventoryColumns * 3;
    }
}
