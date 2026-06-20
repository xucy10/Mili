package net.minecraft.world.inventory;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class AnvilMenu extends ItemCombinerMenu {
    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG_COST = false;
    public static final int MAX_NAME_LENGTH = 50;
    public int repairItemCountCost;
    public @Nullable String itemName;
    public final DataSlot cost = DataSlot.standalone();
    private boolean onlyRenaming = false;
    private static final int COST_FAIL = 0;
    private static final int COST_BASE = 1;
    private static final int COST_ADDED_BASE = 1;
    private static final int COST_REPAIR_MATERIAL = 1;
    private static final int COST_REPAIR_SACRIFICE = 2;
    private static final int COST_INCOMPATIBLE_PENALTY = 1;
    private static final int COST_RENAME = 1;
    private static final int INPUT_SLOT_X_PLACEMENT = 27;
    private static final int ADDITIONAL_SLOT_X_PLACEMENT = 76;
    private static final int RESULT_SLOT_X_PLACEMENT = 134;
    private static final int SLOT_Y_PLACEMENT = 47;
    // CraftBukkit start
    public static final int DEFAULT_DENIED_COST = -1;
    public int maximumRepairCost = 40;
    private org.bukkit.craftbukkit.inventory.view.CraftAnvilView bukkitEntity;
    // CraftBukkit end
    public boolean bypassEnchantmentLevelRestriction = false; // Paper - bypass anvil level restrictions

    public AnvilMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public AnvilMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(MenuType.ANVIL, containerId, playerInventory, access, createInputSlotDefinitions());
        this.addDataSlot(this.cost);
    }

    private static ItemCombinerMenuSlotDefinition createInputSlotDefinitions() {
        return ItemCombinerMenuSlotDefinition.create()
            .withSlot(0, 27, 47, itemStack -> true)
            .withSlot(1, 76, 47, itemStack -> true)
            .withResultSlot(2, 134, 47)
            .build();
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(BlockTags.ANVIL);
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasStack) {
        return (player.hasInfiniteMaterials() || player.experienceLevel >= this.cost.get()) && this.cost.get() > AnvilMenu.DEFAULT_DENIED_COST && hasStack; // CraftBukkit - allow cost 0 like a free item
    }

    @Override
    protected void onTake(Player player, ItemStack stack) {
        if (!player.hasInfiniteMaterials()) {
            player.giveExperienceLevels(-this.cost.get());
        }

        if (this.repairItemCountCost > 0) {
            ItemStack item = this.inputSlots.getItem(1);
            if (!item.isEmpty() && item.getCount() > this.repairItemCountCost) {
                item.shrink(this.repairItemCountCost);
                this.inputSlots.setItem(1, item);
            } else {
                this.inputSlots.setItem(1, ItemStack.EMPTY);
            }
        } else if (!this.onlyRenaming) {
            this.inputSlots.setItem(1, ItemStack.EMPTY);
        }

        this.cost.set(AnvilMenu.DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
        if (player instanceof ServerPlayer serverPlayer
            && !StringUtil.isBlank(this.itemName)
            && !this.inputSlots.getItem(0).getHoverName().getString().equals(this.itemName)) {
            serverPlayer.getTextFilter().processStreamMessage(this.itemName);
        }

        this.inputSlots.setItem(0, ItemStack.EMPTY);
        this.access.execute((level, blockPos) -> {
            BlockState blockState = level.getBlockState(blockPos);
            if (!player.hasInfiniteMaterials() && blockState.is(BlockTags.ANVIL) && player.getRandom().nextFloat() < 0.12F) {
                BlockState blockState1 = AnvilBlock.damage(blockState);
                // Paper start - AnvilDamageEvent
                com.destroystokyo.paper.event.block.AnvilDamagedEvent event = new com.destroystokyo.paper.event.block.AnvilDamagedEvent(getBukkitView(), blockState1 != null ? org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(blockState1) : null);
                if (!event.callEvent()) {
                    return;
                } else if (event.getDamageState() == com.destroystokyo.paper.event.block.AnvilDamagedEvent.DamageState.BROKEN) {
                    blockState1 = null;
                } else {
                    blockState1 = ((org.bukkit.craftbukkit.block.data.CraftBlockData) event.getDamageState().getMaterial().createBlockData()).getState().setValue(AnvilBlock.FACING, blockState.getValue(AnvilBlock.FACING));
                }
                // Paper end - AnvilDamageEvent
                if (blockState1 == null) {
                    level.removeBlock(blockPos, false);
                    level.levelEvent(LevelEvent.SOUND_ANVIL_BROKEN, blockPos, 0);
                } else {
                    level.setBlock(blockPos, blockState1, Block.UPDATE_CLIENTS);
                    level.levelEvent(LevelEvent.SOUND_ANVIL_USED, blockPos, 0);
                }
            } else {
                level.levelEvent(LevelEvent.SOUND_ANVIL_USED, blockPos, 0);
            }
        });
    }

    @Override
    public void createResult() {
        ItemStack item = this.inputSlots.getItem(0);
        this.onlyRenaming = false;
        this.cost.set(1);
        int i = 0;
        long l = 0L;
        int i1 = 0;
        if (!item.isEmpty() && EnchantmentHelper.canStoreEnchantments(item)) {
            ItemStack itemStack = item.copy();
            ItemStack item1 = this.inputSlots.getItem(1);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(itemStack));
            l += (long)item.getOrDefault(DataComponents.REPAIR_COST, 0).intValue() + item1.getOrDefault(DataComponents.REPAIR_COST, 0).intValue();
            this.repairItemCountCost = 0;
            if (!item1.isEmpty()) {
                boolean hasStoredEnchantments = item1.has(DataComponents.STORED_ENCHANTMENTS);
                if (itemStack.isDamageableItem() && item.isValidRepairItem(item1)) {
                    int min = Math.min(itemStack.getDamageValue(), itemStack.getMaxDamage() / 4);
                    if (min <= 0) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(this.getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(AnvilMenu.DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }

                    int i2;
                    for (i2 = 0; min > 0 && i2 < item1.getCount(); i2++) {
                        int i3 = itemStack.getDamageValue() - min;
                        itemStack.setDamageValue(i3);
                        i++;
                        min = Math.min(itemStack.getDamageValue(), itemStack.getMaxDamage() / 4);
                    }

                    this.repairItemCountCost = i2;
                } else {
                    if (!hasStoredEnchantments && (!itemStack.is(item1.getItem()) || !itemStack.isDamageableItem())) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(this.getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(AnvilMenu.DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }

                    if (itemStack.isDamageableItem() && !hasStoredEnchantments) {
                        int minx = item.getMaxDamage() - item.getDamageValue();
                        int i2 = item1.getMaxDamage() - item1.getDamageValue();
                        int i3 = i2 + itemStack.getMaxDamage() * 12 / 100;
                        int i4 = minx + i3;
                        int i5 = itemStack.getMaxDamage() - i4;
                        if (i5 < 0) {
                            i5 = 0;
                        }

                        if (i5 < itemStack.getDamageValue()) {
                            itemStack.setDamageValue(i5);
                            i += 2;
                        }
                    }

                    ItemEnchantments enchantmentsForCrafting = EnchantmentHelper.getEnchantmentsForCrafting(item1);
                    boolean flag = false;
                    boolean flag1 = false;

                    for (Entry<Holder<Enchantment>> entry : enchantmentsForCrafting.entrySet()) {
                        Holder<Enchantment> holder = entry.getKey();
                        int level = mutable.getLevel(holder);
                        int intValue = entry.getIntValue();
                        intValue = level == intValue ? intValue + 1 : Math.max(intValue, level);
                        Enchantment enchantment = holder.value();
                        boolean canEnchant = enchantment.canEnchant(item);
                        if (this.player.hasInfiniteMaterials() || item.is(Items.ENCHANTED_BOOK)) {
                            canEnchant = true;
                        }

                        for (Holder<Enchantment> holder1 : mutable.keySet()) {
                            if (!holder1.equals(holder) && !Enchantment.areCompatible(holder, holder1)) {
                                canEnchant = false;
                                i++;
                            }
                        }

                        if (!canEnchant) {
                            flag1 = true;
                        } else {
                            flag = true;
                            if (intValue > enchantment.getMaxLevel() && !this.bypassEnchantmentLevelRestriction) { // Paper - bypass anvil level restrictions
                                intValue = enchantment.getMaxLevel();
                            }

                            mutable.set(holder, intValue);
                            int anvilCost = enchantment.getAnvilCost();
                            if (hasStoredEnchantments) {
                                anvilCost = Math.max(1, anvilCost / 2);
                            }

                            i += anvilCost * intValue;
                            if (item.getCount() > 1) {
                                i = 40;
                            }
                        }
                    }

                    if (flag1 && !flag) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(this.getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(AnvilMenu.DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }
                }
            }

            if (this.itemName != null && !StringUtil.isBlank(this.itemName)) {
                if (!this.itemName.equals(item.getHoverName().getString())) {
                    i1 = 1;
                    i += i1;
                    itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(this.itemName));
                }
            } else if (item.has(DataComponents.CUSTOM_NAME)) {
                i1 = 1;
                i += i1;
                itemStack.remove(DataComponents.CUSTOM_NAME);
            }

            int i6 = i <= 0 ? 0 : (int)Mth.clamp(l + i, 0L, 2147483647L);
            this.cost.set(i6);
            if (i <= 0) {
                itemStack = ItemStack.EMPTY;
            }

            if (i1 == i && i1 > 0) {
                // CraftBukkit start
                if (this.cost.get() >= this.maximumRepairCost) {
                    this.cost.set(this.maximumRepairCost - 1);
                // CraftBukkit end
                }

                this.onlyRenaming = true;
            }

            if (this.cost.get() >= this.maximumRepairCost && !this.player.hasInfiniteMaterials()) { // CraftBukkit
                itemStack = ItemStack.EMPTY;
            }

            if (!itemStack.isEmpty()) {
                int minxx = itemStack.getOrDefault(DataComponents.REPAIR_COST, 0);
                if (minxx < item1.getOrDefault(DataComponents.REPAIR_COST, 0)) {
                    minxx = item1.getOrDefault(DataComponents.REPAIR_COST, 0);
                }

                if (i1 != i || i1 == 0) {
                    minxx = calculateIncreasedRepairCost(minxx);
                }

                itemStack.set(DataComponents.REPAIR_COST, minxx);
                EnchantmentHelper.setEnchantments(itemStack, mutable.toImmutable());
            }

            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(this.getBukkitView(), itemStack); // CraftBukkit
            this.broadcastChanges();
        } else {
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(this.getBukkitView(), ItemStack.EMPTY); // CraftBukkit
            this.cost.set(AnvilMenu.DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
        }
        this.sendAllDataToRemote(); // CraftBukkit - SPIGOT-6686, SPIGOT-7931: Always send completed inventory to stay in sync with client
    }

    public static int calculateIncreasedRepairCost(int oldRepairCost) {
        return (int)Math.min(oldRepairCost * 2L + 1L, 2147483647L);
    }

    public boolean setItemName(String itemName) {
        String string = validateName(itemName);
        if (string != null && !string.equals(this.itemName)) {
            this.itemName = string;
            if (this.getSlot(2).hasItem()) {
                ItemStack item = this.getSlot(2).getItem();
                if (StringUtil.isBlank(string)) {
                    item.remove(DataComponents.CUSTOM_NAME);
                } else {
                    item.set(DataComponents.CUSTOM_NAME, Component.literal(string));
                }
            }

            this.createResult();
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareResultEvent(this, RESULT_SLOT); // Paper - Add PrepareResultEvent
            return true;
        } else {
            return false;
        }
    }

    private static @Nullable String validateName(String itemName) {
        String string = StringUtil.filterText(itemName);
        return string.length() <= 50 ? string : null;
    }

    public int getCost() {
        return this.cost.get();
    }

    // CraftBukkit start
    @Override
    public org.bukkit.craftbukkit.inventory.view.CraftAnvilView getBukkitView() {
        if (this.bukkitEntity != null) {
            return this.bukkitEntity;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryAnvil inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryAnvil(
                this.access.getLocation(), this.inputSlots, this.resultSlots);
        this.bukkitEntity = new org.bukkit.craftbukkit.inventory.view.CraftAnvilView(this.player.getBukkitEntity(), inventory, this);
        this.bukkitEntity.updateFromLegacy(inventory);
        return this.bukkitEntity;
    }
    // CraftBukkit end
}
