package net.minecraft.world.inventory;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.Vec3;

public class GrindstoneMenu extends AbstractContainerMenu {
    // CraftBukkit start
    private @javax.annotation.Nullable org.bukkit.craftbukkit.inventory.CraftInventoryView view = null;
    private final org.bukkit.entity.Player player;

    @Override
    public org.bukkit.craftbukkit.inventory.CraftInventoryView getBukkitView() {
        if (this.view != null) {
            return this.view;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryGrindstone inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryGrindstone(this.repairSlots, this.resultSlots);
        this.view = new org.bukkit.craftbukkit.inventory.CraftInventoryView(this.player, inventory, this);
        return this.view;
    }
    // CraftBukkit end
    public static final int MAX_NAME_LENGTH = 35;
    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final int INV_SLOT_START = 3;
    private static final int INV_SLOT_END = 30;
    private static final int USE_ROW_SLOT_START = 30;
    private static final int USE_ROW_SLOT_END = 39;
    private final Container resultSlots; // Paper - Add missing InventoryHolders - move down
    final Container repairSlots; // Paper - Add missing InventoryHolders - move down
    private final ContainerLevelAccess access;

    public GrindstoneMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public GrindstoneMenu(int containerId, Inventory playerInventory, final ContainerLevelAccess access) {
        super(MenuType.GRINDSTONE, containerId);
        // Paper start - Add missing InventoryHolders
        this.resultSlots = new ResultContainer(this.createBlockHolder(access)); // Paper - Add missing InventoryHolders
        this.repairSlots = new SimpleContainer(this.createBlockHolder(access), 2) { // Paper - Add missing InventoryHolders
            @Override
            public void setChanged() {
                super.setChanged();
                GrindstoneMenu.this.slotsChanged(this);
            }
            // CraftBukkit start
            @Override
            public org.bukkit.Location getLocation() {
                return access.getLocation();
            }
            // CraftBukkit end
        };
        // Paper end - Add missing InventoryHolders
        this.access = access;
        this.addSlot(new Slot(this.repairSlots, 0, 49, 19) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(stack);
            }
        });
        this.addSlot(new Slot(this.repairSlots, 1, 49, 40) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(stack);
            }
        });
        this.addSlot(new Slot(this.resultSlots, 2, 129, 34) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                access.execute((level, blockPos) -> {
                    if (level instanceof ServerLevel) {
                        // Paper start - Fire BlockExpEvent on grindstone use
                        org.bukkit.event.block.BlockExpEvent event = new org.bukkit.event.block.BlockExpEvent(org.bukkit.craftbukkit.block.CraftBlock.at(level, blockPos), this.getExperienceAmount(level));
                        event.callEvent();
                        ExperienceOrb.awardWithDirection((ServerLevel) level, Vec3.atCenterOf(blockPos), Vec3.ZERO, event.getExpToDrop(), org.bukkit.entity.ExperienceOrb.SpawnReason.GRINDSTONE, player, null);
                        // Paper end - Fire BlockExpEvent on grindstone use
                    }

                    level.levelEvent(LevelEvent.SOUND_GRINDSTONE_USED, blockPos, 0);
                });
                GrindstoneMenu.this.repairSlots.setItem(0, ItemStack.EMPTY);
                GrindstoneMenu.this.repairSlots.setItem(1, ItemStack.EMPTY);
            }

            private int getExperienceAmount(Level level) {
                int i = 0;
                i += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(0));
                i += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(1));
                if (i > 0) {
                    int i1 = (int)Math.ceil(i / 2.0);
                    return i1 + level.random.nextInt(i1);
                } else {
                    return 0;
                }
            }

            private int getExperienceFromItem(ItemStack stack) {
                int i = 0;
                ItemEnchantments enchantmentsForCrafting = EnchantmentHelper.getEnchantmentsForCrafting(stack);

                for (Entry<Holder<Enchantment>> entry : enchantmentsForCrafting.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    int intValue = entry.getIntValue();
                    if (!holder.is(EnchantmentTags.CURSE)) {
                        i += holder.value().getMinCost(intValue);
                    }
                }

                return i;
            }
        });
        this.addStandardInventorySlots(playerInventory, 8, 84);
        this.player = (org.bukkit.entity.Player) playerInventory.player.getBukkitEntity(); // CraftBukkit
    }

    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        if (inventory == this.repairSlots) {
            this.createResult();
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareResultEvent(this, RESULT_SLOT); // Paper - Add PrepareResultEvent
        }
    }

    private void createResult() {
        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareGrindstoneEvent(this.getBukkitView(), this.computeResult(this.repairSlots.getItem(0), this.repairSlots.getItem(1))); // CraftBukkit
        this.sendAllDataToRemote(); // CraftBukkit - SPIGOT-6686: Always send completed inventory to stay in sync with client
        this.broadcastChanges();
    }

    private ItemStack computeResult(ItemStack inputItem, ItemStack additionalItem) {
        boolean flag = !inputItem.isEmpty() || !additionalItem.isEmpty();
        if (!flag) {
            return ItemStack.EMPTY;
        } else if (inputItem.getCount() <= 1 && additionalItem.getCount() <= 1) {
            boolean flag1 = !inputItem.isEmpty() && !additionalItem.isEmpty();
            if (!flag1) {
                ItemStack itemStack = !inputItem.isEmpty() ? inputItem : additionalItem;
                return !EnchantmentHelper.hasAnyEnchantments(itemStack) ? ItemStack.EMPTY : this.removeNonCursesFrom(itemStack.copy());
            } else {
                return this.mergeItems(inputItem, additionalItem);
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    private ItemStack mergeItems(ItemStack inputItem, ItemStack additionalItem) {
        if (!inputItem.is(additionalItem.getItem())) {
            return ItemStack.EMPTY;
        } else {
            int max = Math.max(inputItem.getMaxDamage(), additionalItem.getMaxDamage());
            int i = inputItem.getMaxDamage() - inputItem.getDamageValue();
            int i1 = additionalItem.getMaxDamage() - additionalItem.getDamageValue();
            int i2 = i + i1 + max * 5 / 100;
            int i3 = 1;
            if (!inputItem.isDamageableItem()) {
                if (inputItem.getMaxStackSize() < 2 || !ItemStack.matches(inputItem, additionalItem)) {
                    return ItemStack.EMPTY;
                }

                i3 = 2;
            }

            ItemStack itemStack = inputItem.copyWithCount(i3);
            if (itemStack.isDamageableItem()) {
                itemStack.set(DataComponents.MAX_DAMAGE, max);
                itemStack.setDamageValue(Math.max(max - i2, 0));
            }

            this.mergeEnchantsFrom(itemStack, additionalItem);
            return this.removeNonCursesFrom(itemStack);
        }
    }

    private void mergeEnchantsFrom(ItemStack inputItem, ItemStack additionalItem) {
        EnchantmentHelper.updateEnchantments(inputItem, mutable -> {
            ItemEnchantments enchantmentsForCrafting = EnchantmentHelper.getEnchantmentsForCrafting(additionalItem);

            for (Entry<Holder<Enchantment>> entry : enchantmentsForCrafting.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                if (!holder.is(EnchantmentTags.CURSE) || mutable.getLevel(holder) == 0) {
                    mutable.upgrade(holder, entry.getIntValue());
                }
            }
        });
    }

    private ItemStack removeNonCursesFrom(ItemStack item) {
        ItemEnchantments itemEnchantments = EnchantmentHelper.updateEnchantments(item, mutable -> mutable.removeIf(holder -> !holder.is(EnchantmentTags.CURSE)));
        if (item.is(Items.ENCHANTED_BOOK) && itemEnchantments.isEmpty()) {
            item = item.transmuteCopy(Items.BOOK);
        }

        int i = 0;

        for (int i1 = 0; i1 < itemEnchantments.size(); i1++) {
            i = AnvilMenu.calculateIncreasedRepairCost(i);
        }

        item.set(DataComponents.REPAIR_COST, i);
        return item;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, blockPos) -> this.clearContainer(player, this.repairSlots));
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, player, Blocks.GRINDSTONE);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            ItemStack item1 = this.repairSlots.getItem(0);
            ItemStack item2 = this.repairSlots.getItem(1);
            if (slotIndex == 2) {
                if (!this.moveItemStackTo(item, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(item, itemStack);
            } else if (slotIndex != 0 && slotIndex != 1) {
                if (!item1.isEmpty() && !item2.isEmpty()) {
                    if (slotIndex >= 3 && slotIndex < 30) {
                        if (!this.moveItemStackTo(item, 30, 39, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (slotIndex >= 30 && slotIndex < 39 && !this.moveItemStackTo(item, 3, 30, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(item, 0, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(item, 3, 39, false)) {
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
}
