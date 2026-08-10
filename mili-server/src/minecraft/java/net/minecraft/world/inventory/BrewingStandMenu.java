package net.minecraft.world.inventory;

import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;

public class BrewingStandMenu extends AbstractContainerMenu {
    static final Identifier EMPTY_SLOT_FUEL = Identifier.withDefaultNamespace("container/slot/brewing_fuel");
    static final Identifier EMPTY_SLOT_POTION = Identifier.withDefaultNamespace("container/slot/potion");
    private static final int BOTTLE_SLOT_START = 0;
    private static final int BOTTLE_SLOT_END = 2;
    private static final int INGREDIENT_SLOT = 3;
    private static final int FUEL_SLOT = 4;
    private static final int SLOT_COUNT = 5;
    private static final int DATA_COUNT = 2;
    private static final int INV_SLOT_START = 5;
    private static final int INV_SLOT_END = 32;
    private static final int USE_ROW_SLOT_START = 32;
    private static final int USE_ROW_SLOT_END = 41;
    private final Container brewingStand;
    public final ContainerData brewingStandData;
    private final Slot ingredientSlot;
    // CraftBukkit start
    private @javax.annotation.Nullable org.bukkit.craftbukkit.inventory.view.CraftBrewingStandView view = null;
    private final Inventory inventory;
    // CraftBukkit end

    public BrewingStandMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(5), new io.papermc.paper.inventory.BrewingSimpleContainerData()); // Paper - Add totalBrewTime
    }

    public BrewingStandMenu(int containerId, Inventory playerInventory, Container brewingStandContainer, ContainerData brewingStandData) {
        super(MenuType.BREWING_STAND, containerId);
        this.inventory = playerInventory; // CraftBukkit
        checkContainerSize(brewingStandContainer, 5);
        checkContainerDataCount(brewingStandData, 3); // Paper - Add recipeBrewTime
        this.brewingStand = brewingStandContainer;
        this.brewingStandData = brewingStandData;
        PotionBrewing potionBrewing = playerInventory.player.level().potionBrewing();
        // Paper start - custom potion mixes
        this.addSlot(new BrewingStandMenu.PotionSlot(brewingStandContainer, 0, 56, 51, potionBrewing));
        this.addSlot(new BrewingStandMenu.PotionSlot(brewingStandContainer, 1, 79, 58, potionBrewing));
        this.addSlot(new BrewingStandMenu.PotionSlot(brewingStandContainer, 2, 102, 51, potionBrewing));
        // Paper end - custom potion mixes
        this.ingredientSlot = this.addSlot(new BrewingStandMenu.IngredientsSlot(potionBrewing, brewingStandContainer, 3, 79, 17));
        this.addSlot(new BrewingStandMenu.FuelSlot(brewingStandContainer, 4, 17, 17));
        // Paper start - Add recipeBrewTime
        this.addDataSlots(new SimpleContainerData(2) {
            @Override
            public int get(final int index) {
                if (index == 0) return 400 * brewingStandData.get(index) / brewingStandData.get(2);
                return brewingStandData.get(index);
            }

            @Override
            public void set(final int index, final int value) {
                brewingStandData.set(index, value);
            }
        });
        // Paper end - Add recipeBrewTime
        this.addStandardInventorySlots(playerInventory, 8, 84);
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return this.brewingStand.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            if ((slotIndex < 0 || slotIndex > 2) && slotIndex != 3 && slotIndex != 4) {
                if (BrewingStandMenu.FuelSlot.mayPlaceItem(itemStack)) {
                    if (this.moveItemStackTo(item, 4, 5, false) || this.ingredientSlot.mayPlace(item) && !this.moveItemStackTo(item, 3, 4, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (this.ingredientSlot.mayPlace(item)) {
                    if (!this.moveItemStackTo(item, 3, 4, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (BrewingStandMenu.PotionSlot.mayPlaceItem(itemStack, this.inventory.player.level().potionBrewing())) { // Paper - custom potion mixes
                    if (!this.moveItemStackTo(item, 0, 3, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex >= 5 && slotIndex < 32) {
                    if (!this.moveItemStackTo(item, 32, 41, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex >= 32 && slotIndex < 41) {
                    if (!this.moveItemStackTo(item, 5, 32, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(item, 5, 41, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(item, 5, 41, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(item, itemStack);
            }

            if (item.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (item.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemStack);
        }

        return itemStack;
    }

    public int getFuel() {
        return this.brewingStandData.get(1);
    }

    public int getBrewingTicks() {
        return this.brewingStandData.get(0);
    }

    static class FuelSlot extends Slot {
        public FuelSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return mayPlaceItem(stack);
        }

        public static boolean mayPlaceItem(ItemStack stack) {
            return stack.is(ItemTags.BREWING_FUEL);
        }

        @Override
        public Identifier getNoItemIcon() {
            return BrewingStandMenu.EMPTY_SLOT_FUEL;
        }
    }

    static class IngredientsSlot extends Slot {
        private final PotionBrewing potionBrewing;

        public IngredientsSlot(PotionBrewing potionBrewing, Container container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.potionBrewing = potionBrewing;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.potionBrewing.isIngredient(stack);
        }
    }

    static class PotionSlot extends Slot {
        private final PotionBrewing potionBrewing; // Paper - custom potion mixes
        public PotionSlot(Container container, int slot, int x, int y, PotionBrewing potionBrewing) { // Paper - custom potion mixes
            super(container, slot, x, y);
            this.potionBrewing = potionBrewing; // Paper - custom potion mixes
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return mayPlaceItem(stack, this.potionBrewing); // Paper - custom potion mixes
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            Optional<Holder<Potion>> optional = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion();
            if (optional.isPresent() && player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.BREWED_POTION.trigger(serverPlayer, optional.get());
            }

            super.onTake(player, stack);
        }

        public static boolean mayPlaceItem(ItemStack stack, PotionBrewing potionBrewing) { // Paper - custom potion mixes
            return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION) || stack.is(Items.GLASS_BOTTLE) || potionBrewing.isCustomInput(stack); // Paper - Custom Potion Mixes
        }

        @Override
        public Identifier getNoItemIcon() {
            return BrewingStandMenu.EMPTY_SLOT_POTION;
        }
    }
    // CraftBukkit start
    @Override
    public org.bukkit.craftbukkit.inventory.view.CraftBrewingStandView getBukkitView() {
        if (this.view != null) {
            return this.view;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryBrewer inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryBrewer(this.brewingStand);
        this.view = new org.bukkit.craftbukkit.inventory.view.CraftBrewingStandView(this.inventory.player.getBukkitEntity(), inventory, this);
        return this.view;
    }
    // CraftBukkit end
}
