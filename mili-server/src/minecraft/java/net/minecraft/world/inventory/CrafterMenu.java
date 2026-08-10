package net.minecraft.world.inventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.CrafterBlock;

public class CrafterMenu extends AbstractContainerMenu implements ContainerListener {
    protected static final int SLOT_COUNT = 9;
    private static final int INV_SLOT_START = 9;
    private static final int INV_SLOT_END = 36;
    private static final int USE_ROW_SLOT_START = 36;
    private static final int USE_ROW_SLOT_END = 45;
    private final ResultContainer resultContainer = new ResultContainer();
    private final ContainerData containerData;
    private final Player player;
    private final CraftingContainer container;
    // CraftBukkit start
    private @javax.annotation.Nullable org.bukkit.craftbukkit.inventory.view.CraftCrafterView view = null;

    @Override
    public org.bukkit.craftbukkit.inventory.view.CraftCrafterView getBukkitView() {
        if (this.view != null) {
            return this.view;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryCrafter inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryCrafter(this.container, this.resultContainer);
        this.view = new org.bukkit.craftbukkit.inventory.view.CraftCrafterView(this.player.getBukkitEntity(), inventory, this);
        return this.view;
    }
    // CraftBukkit end

    public CrafterMenu(int containerId, Inventory playerInventory) {
        super(MenuType.CRAFTER_3x3, containerId);
        this.player = playerInventory.player;
        this.containerData = new SimpleContainerData(10);
        this.container = new TransientCraftingContainer(this, 3, 3);
        this.addSlots(playerInventory);
    }

    public CrafterMenu(int containerId, Inventory playerInventory, CraftingContainer container, ContainerData containerData) {
        super(MenuType.CRAFTER_3x3, containerId);
        this.player = playerInventory.player;
        this.containerData = containerData;
        this.container = container;
        checkContainerSize(container, 9);
        container.startOpen(playerInventory.player);
        this.addSlots(playerInventory);
        this.addSlotListener(this);
    }

    private void addSlots(Inventory playerInventory) {
        for (int i = 0; i < 3; i++) {
            for (int i1 = 0; i1 < 3; i1++) {
                int i2 = i1 + i * 3;
                this.addSlot(new CrafterSlot(this.container, i2, 26 + i1 * 18, 17 + i * 18, this));
            }
        }

        this.addStandardInventorySlots(playerInventory, 8, 84);
        this.addSlot(new NonInteractiveResultSlot(this.resultContainer, 0, 134, 35));
        this.addDataSlots(this.containerData);
        this.refreshRecipeResult();
    }

    public void setSlotState(int slot, boolean enabled) {
        CrafterSlot crafterSlot = (CrafterSlot)this.getSlot(slot);
        this.containerData.set(crafterSlot.index, enabled ? 0 : 1);
        this.broadcastChanges();
    }

    public boolean isSlotDisabled(int slot) {
        return slot > -1 && slot < 9 && this.containerData.get(slot) == 1;
    }

    public boolean isPowered() {
        return this.containerData.get(9) == 1;
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
                slot.set(ItemStack.EMPTY);
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
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return this.container.stillValid(player);
    }

    private void refreshRecipeResult() {
        if (this.player instanceof ServerPlayer serverPlayer) {
            ServerLevel serverLevel = serverPlayer.level();
            CraftingInput craftInput = this.container.asCraftInput();
            ItemStack itemStack = CrafterBlock.getPotentialResults(serverLevel, craftInput)
                .map(recipeHolder -> recipeHolder.value().assemble(craftInput, serverLevel.registryAccess()))
                .orElse(ItemStack.EMPTY);
            this.resultContainer.setItem(0, itemStack);
        }
    }

    public Container getContainer() {
        return this.container;
    }

    @Override
    public void slotChanged(AbstractContainerMenu containerToSend, int dataSlotIndex, ItemStack stack) {
        this.refreshRecipeResult();
    }

    @Override
    public void dataChanged(AbstractContainerMenu containerMenu, int dataSlotIndex, int value) {
    }
}
