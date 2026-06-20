package net.minecraft.world.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class CartographyTableMenu extends AbstractContainerMenu {
    // CraftBukkit start
    private @javax.annotation.Nullable org.bukkit.craftbukkit.inventory.CraftInventoryView view = null;
    private final org.bukkit.entity.Player player;

    @Override
    public org.bukkit.craftbukkit.inventory.CraftInventoryView getBukkitView() {
        if (this.view != null) {
            return this.view;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryCartography inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryCartography(this.container, this.resultContainer);
        this.view = new org.bukkit.craftbukkit.inventory.CraftInventoryView<>(this.player, inventory, this);
        return this.view;
    }
    // CraftBukkit end
    public static final int MAP_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final int INV_SLOT_START = 3;
    private static final int INV_SLOT_END = 30;
    private static final int USE_ROW_SLOT_START = 30;
    private static final int USE_ROW_SLOT_END = 39;
    private final ContainerLevelAccess access;
    long lastSoundTime;
    public final Container container; // Paper - Add missing InventoryHolders - move down
    private final ResultContainer resultContainer; // Paper - Add missing InventoryHolders - move down

    public CartographyTableMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public CartographyTableMenu(int containerId, Inventory playerInventory, final ContainerLevelAccess access) {
        super(MenuType.CARTOGRAPHY_TABLE, containerId);
        // Paper start - Add missing InventoryHolders - move down
        this.container = new SimpleContainer(this.createBlockHolder(access), 2) {
            @Override
            public void setChanged() {
                CartographyTableMenu.this.slotsChanged(this);
                super.setChanged();
            }
            // CraftBukkit start
            @Override
            public org.bukkit.Location getLocation() {
                return access.getLocation();
            }
            // CraftBukkit end
        };
        this.resultContainer = new ResultContainer(this.createBlockHolder(access)) {
            @Override
            public void setChanged() {
                // CartographyTableMenu.this.slotsChanged(this); // Paper - Add CartographyItemEvent - do not recompute results if the result slot changes - allows to set the result slot via api
                super.setChanged();
            }
            // CraftBukkit start
            @Override
            public org.bukkit.Location getLocation() {
                return access.getLocation();
            }
            // CraftBukkit end
        };
        // Paper end - Add missing InventoryHolders - move down
        this.access = access;
        this.addSlot(new Slot(this.container, 0, 15, 15) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.has(DataComponents.MAP_ID);
            }
        });
        this.addSlot(new Slot(this.container, 1, 15, 52) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.PAPER) || stack.is(Items.MAP) || stack.is(Items.GLASS_PANE);
            }
        });
        this.addSlot(new Slot(this.resultContainer, 2, 145, 39) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                CartographyTableMenu.this.slots.get(0).remove(1);
                CartographyTableMenu.this.slots.get(1).remove(1);
                stack.getItem().onCraftedBy(stack, player);
                access.execute((level, blockPos) -> {
                    long gameTime = level.getGameTime();
                    if (CartographyTableMenu.this.lastSoundTime != gameTime) {
                        level.playSound(null, blockPos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        CartographyTableMenu.this.lastSoundTime = gameTime;
                    }
                });
                super.onTake(player, stack);
            }
        });
        this.addStandardInventorySlots(playerInventory, 8, 84);
        this.player = (org.bukkit.entity.Player) playerInventory.player.getBukkitEntity(); // CraftBukkit
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, player, Blocks.CARTOGRAPHY_TABLE);
    }

    @Override
    public void slotsChanged(Container inventory) {
        ItemStack item = this.container.getItem(0);
        ItemStack item1 = this.container.getItem(1);
        ItemStack item2 = this.resultContainer.getItem(2);
        if (item2.isEmpty() || !item.isEmpty() && !item1.isEmpty()) {
            if (!item.isEmpty() && !item1.isEmpty()) {
                this.setupResultSlot(item, item1, item2);
            }
        } else {
            this.resultContainer.removeItemNoUpdate(2);
        }
        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareResultEvent(this, RESULT_SLOT); // Paper - Add PrepareResultEvent
    }

    private void setupResultSlot(ItemStack map, ItemStack firstSlotStack, ItemStack resultOutput) {
        this.access.execute((level, pos) -> {
            MapItemSavedData savedData = MapItem.getSavedData(map, level);
            if (savedData != null) {
                ItemStack itemStack;
                if (firstSlotStack.is(Items.PAPER) && !savedData.locked && savedData.scale < 4) {
                    itemStack = map.copyWithCount(1);
                    itemStack.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
                    this.broadcastChanges();
                } else if (firstSlotStack.is(Items.GLASS_PANE) && !savedData.locked) {
                    itemStack = map.copyWithCount(1);
                    itemStack.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.LOCK);
                    this.broadcastChanges();
                } else {
                    if (!firstSlotStack.is(Items.MAP)) {
                        this.resultContainer.removeItemNoUpdate(2);
                        this.broadcastChanges();
                        return;
                    }

                    itemStack = map.copyWithCount(2);
                    this.broadcastChanges();
                }

                if (!ItemStack.matches(itemStack, resultOutput)) {
                    this.resultContainer.setItem(2, itemStack);
                    this.broadcastChanges();
                }
            }
        });
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultContainer && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            if (slotIndex == 2) {
                item.getItem().onCraftedBy(item, player);
                if (!this.moveItemStackTo(item, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(item, itemStack);
            } else if (slotIndex != 1 && slotIndex != 0) {
                if (item.has(DataComponents.MAP_ID)) {
                    if (!this.moveItemStackTo(item, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!item.is(Items.PAPER) && !item.is(Items.MAP) && !item.is(Items.GLASS_PANE)) {
                    if (slotIndex >= 3 && slotIndex < 30) {
                        if (!this.moveItemStackTo(item, 30, 39, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (slotIndex >= 30 && slotIndex < 39 && !this.moveItemStackTo(item, 3, 30, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(item, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(item, 3, 39, false)) {
                return ItemStack.EMPTY;
            }

            if (item.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            }

            slot.setChanged();
            if (item.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, item);
            this.broadcastChanges();
        }

        return itemStack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.resultContainer.removeItemNoUpdate(2);
        this.access.execute((level, pos) -> this.clearContainer(player, this.container));
    }
}
