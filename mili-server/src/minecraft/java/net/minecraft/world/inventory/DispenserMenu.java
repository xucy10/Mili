package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class DispenserMenu extends AbstractContainerMenu {
    private static final int SLOT_COUNT = 9;
    private static final int INV_SLOT_START = 9;
    private static final int INV_SLOT_END = 36;
    private static final int USE_ROW_SLOT_START = 36;
    private static final int USE_ROW_SLOT_END = 45;
    public final Container dispenser;
    // CraftBukkit start
    private @javax.annotation.Nullable org.bukkit.craftbukkit.inventory.CraftInventoryView view = null;
    private final Inventory inventory;
    // CraftBukkit end

    public DispenserMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(9));
    }

    public DispenserMenu(int containerId, Inventory playerInventory, Container container) {
        super(MenuType.GENERIC_3x3, containerId);
        // CraftBukkit start - Save player
        this.inventory = playerInventory;
        // CraftBukkit end
        checkContainerSize(container, 9);
        this.dispenser = container;
        container.startOpen(playerInventory.player);
        this.add3x3GridSlots(container, 62, 17);
        this.addStandardInventorySlots(playerInventory, 8, 84);
    }

    protected void add3x3GridSlots(Container container, int x, int y) {
        for (int i = 0; i < 3; i++) {
            for (int i1 = 0; i1 < 3; i1++) {
                int i2 = i1 + i * 3;
                this.addSlot(new Slot(container, i2, x + i1 * 18, y + i * 18));
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return this.dispenser.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            if (slotIndex < 9) {
                if (!this.moveItemStackTo(item, 9, 45, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(item, 0, 9, false)) {
                return ItemStack.EMPTY;
            }

            if (item.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (item.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, item);
        }

        return itemStack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.dispenser.stopOpen(player);
    }

    // CraftBukkit start
    @Override
    public org.bukkit.craftbukkit.inventory.CraftInventoryView getBukkitView() {
        if (this.view != null) {
            return this.view;
        }

        org.bukkit.craftbukkit.inventory.CraftInventory inventory = new org.bukkit.craftbukkit.inventory.CraftInventory(this.dispenser);
        this.view = new org.bukkit.craftbukkit.inventory.CraftInventoryView(this.inventory.player.getBukkitEntity(), inventory, this);
        return this.view;
    }
    // CraftBukkit end
}
