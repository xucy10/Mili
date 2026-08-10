package net.minecraft.world.inventory;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class InventoryMenu extends AbstractCraftingMenu {
    public static final int CONTAINER_ID = 0;
    public static final int RESULT_SLOT = 0;
    private static final int CRAFTING_GRID_WIDTH = 2;
    private static final int CRAFTING_GRID_HEIGHT = 2;
    public static final int CRAFT_SLOT_START = 1;
    public static final int CRAFT_SLOT_COUNT = 4;
    public static final int CRAFT_SLOT_END = 5;
    public static final int ARMOR_SLOT_START = 5;
    public static final int ARMOR_SLOT_COUNT = 4;
    public static final int ARMOR_SLOT_END = 9;
    public static final int INV_SLOT_START = 9;
    public static final int INV_SLOT_END = 36;
    public static final int USE_ROW_SLOT_START = 36;
    public static final int USE_ROW_SLOT_END = 45;
    public static final int SHIELD_SLOT = 45;
    public static final Identifier EMPTY_ARMOR_SLOT_HELMET = Identifier.withDefaultNamespace("container/slot/helmet");
    public static final Identifier EMPTY_ARMOR_SLOT_CHESTPLATE = Identifier.withDefaultNamespace("container/slot/chestplate");
    public static final Identifier EMPTY_ARMOR_SLOT_LEGGINGS = Identifier.withDefaultNamespace("container/slot/leggings");
    public static final Identifier EMPTY_ARMOR_SLOT_BOOTS = Identifier.withDefaultNamespace("container/slot/boots");
    public static final Identifier EMPTY_ARMOR_SLOT_SHIELD = Identifier.withDefaultNamespace("container/slot/shield");
    private static final Map<EquipmentSlot, Identifier> TEXTURE_EMPTY_SLOTS = Map.of(
        EquipmentSlot.FEET,
        EMPTY_ARMOR_SLOT_BOOTS,
        EquipmentSlot.LEGS,
        EMPTY_ARMOR_SLOT_LEGGINGS,
        EquipmentSlot.CHEST,
        EMPTY_ARMOR_SLOT_CHESTPLATE,
        EquipmentSlot.HEAD,
        EMPTY_ARMOR_SLOT_HELMET
    );
    private static final EquipmentSlot[] SLOT_IDS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    public final boolean active;
    private final Player owner;
    private @javax.annotation.Nullable org.bukkit.craftbukkit.inventory.CraftInventoryView view = null; // CraftBukkit

    public InventoryMenu(Inventory playerInventory, boolean active, final Player owner) {
        // CraftBukkit start
        super(null, 0, 2, 2, playerInventory); // CraftBukkit
        this.setTitle(net.minecraft.network.chat.Component.translatable("container.crafting")); // SPIGOT-4722: Allocate title for player inventory
        // CraftBukkit end
        this.active = active;
        this.owner = owner;
        this.addResultSlot(owner, 154, 28);
        this.addCraftingGridSlots(98, 18);

        for (int i = 0; i < 4; i++) {
            EquipmentSlot equipmentSlot = SLOT_IDS[i];
            Identifier identifier = TEXTURE_EMPTY_SLOTS.get(equipmentSlot);
            this.addSlot(new ArmorSlot(playerInventory, owner, equipmentSlot, 39 - i, 8, 8 + i * 18, identifier));
        }

        this.addStandardInventorySlots(playerInventory, 8, 84);
        this.addSlot(new Slot(playerInventory, 40, 77, 62) {
            @Override
            public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
                owner.onEquipItem(EquipmentSlot.OFFHAND, oldStack, newStack);
                super.setByPlayer(newStack, oldStack);
            }

            @Override
            public Identifier getNoItemIcon() {
                return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
            }
        });
    }

    public static boolean isHotbarSlot(int index) {
        return index >= 36 && index < 45 || index == 45;
    }

    @Override
    public void slotsChanged(Container inventory) {
        if (this.owner.level() instanceof ServerLevel serverLevel) {
            CraftingMenu.slotChangedCraftingGrid(this, serverLevel, this.owner, this.craftSlots, this.resultSlots, null);
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.resultSlots.clearContent();
        if (!player.level().isClientSide()) {
            this.clearContainer(player, this.craftSlots);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            EquipmentSlot equipmentSlotForItem = player.getEquipmentSlotForItem(itemStack);
            if (slotIndex == 0) {
                if (!this.moveItemStackTo(item, 9, 45, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(item, itemStack);
            } else if (slotIndex >= 1 && slotIndex < 5) {
                if (!this.moveItemStackTo(item, 9, 45, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= 5 && slotIndex < 9) {
                if (!this.moveItemStackTo(item, 9, 45, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (equipmentSlotForItem.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !this.slots.get(8 - equipmentSlotForItem.getIndex()).hasItem()) {
                int i = 8 - equipmentSlotForItem.getIndex();
                if (!this.moveItemStackTo(item, i, i + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (equipmentSlotForItem == EquipmentSlot.OFFHAND && !this.slots.get(45).hasItem()) {
                if (!this.moveItemStackTo(item, 45, 46, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= 9 && slotIndex < 36) {
                if (!this.moveItemStackTo(item, 36, 45, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= 36 && slotIndex < 45) {
                if (!this.moveItemStackTo(item, 9, 36, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(item, 9, 45, false)) {
                return ItemStack.EMPTY;
            }

            if (item.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY, itemStack);
            } else {
                slot.setChanged();
            }

            if (item.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, item);
            if (slotIndex == 0) {
                player.drop(item, false);
            }
        }

        return itemStack;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public Slot getResultSlot() {
        return this.slots.get(0);
    }

    @Override
    public List<Slot> getInputGridSlots() {
        return this.slots.subList(1, 5);
    }

    public CraftingContainer getCraftSlots() {
        return this.craftSlots;
    }

    @Override
    public RecipeBookType getRecipeBookType() {
        return RecipeBookType.CRAFTING;
    }

    @Override
    protected Player owner() {
        return this.owner;
    }

    // CraftBukkit start
    @Override
    public org.bukkit.craftbukkit.inventory.CraftInventoryView getBukkitView() {
        if (this.view != null) {
            return this.view;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryCrafting inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryCrafting(this.craftSlots, this.resultSlots);
        this.view = new org.bukkit.craftbukkit.inventory.CraftInventoryView(this.owner.getBukkitEntity(), inventory, this);
        return this.view;
    }

    @Override
    public void forceHeldSlot(final net.minecraft.world.InteractionHand hand) {
        // If ever needed, a config option for instead synchronizing the full inventory can be added here to call this.sendAllDataToRemote();
        // Otherwise, only resync the hand slot
        final int slot = hand == net.minecraft.world.InteractionHand.MAIN_HAND ? this.owner.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
        this.forceSlot(this.owner.getInventory(), slot);
    }

    @Override
    public void forceHeldSlotAndArmor(final net.minecraft.world.InteractionHand hand) {
        this.forceHeldSlot(hand);

        final int size = net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE;
        final net.minecraft.world.entity.player.Inventory inventory = this.owner.getInventory();
        this.forceSlot(inventory, net.minecraft.world.entity.EquipmentSlot.FEET.getIndex(size));
        this.forceSlot(inventory, net.minecraft.world.entity.EquipmentSlot.LEGS.getIndex(size));
        this.forceSlot(inventory, net.minecraft.world.entity.EquipmentSlot.CHEST.getIndex(size));
        this.forceSlot(inventory, net.minecraft.world.entity.EquipmentSlot.HEAD.getIndex(size));
    }

    // CraftBukkit end

    // Paper start - utility methods for synchronizing slots usually not synchronized by the container menus
    public void broadcastNonContainerSlotChanges() {
        for (int i = RESULT_SLOT; i < ARMOR_SLOT_END; i++) {
            this.broadcastSlotChange(i);
        }
        this.broadcastSlotChange(SHIELD_SLOT);
    }

    private void broadcastSlotChange(int slot) {
        ItemStack item = this.slots.get(slot).getItem();
        java.util.function.Supplier<net.minecraft.world.item.ItemStack> supplier = com.google.common.base.Suppliers.memoize(item::copy);
        this.triggerSlotListeners(slot, item, supplier);
        this.synchronizeSlotToRemote(slot, item, supplier);
    }
    // Paper end - utility methods for synchronizing slots usually not synchronized by the container menus
}
