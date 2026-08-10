package net.minecraft.world.inventory;

import java.util.List;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

public abstract class AbstractFurnaceMenu extends RecipeBookMenu {
    public static final int INGREDIENT_SLOT = 0;
    public static final int FUEL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    public static final int SLOT_COUNT = 3;
    public static final int DATA_COUNT = 4;
    private static final int INV_SLOT_START = 3;
    private static final int INV_SLOT_END = 30;
    private static final int USE_ROW_SLOT_START = 30;
    private static final int USE_ROW_SLOT_END = 39;
    final Container container;
    private final ContainerData data;
    protected final Level level;
    private final RecipeType<? extends AbstractCookingRecipe> recipeType;
    private final RecipePropertySet acceptedInputs;
    private final RecipeBookType recipeBookType;
    // CraftBukkit start
    private @javax.annotation.Nullable org.bukkit.craftbukkit.inventory.view.CraftFurnaceView view = null;
    private final Inventory inventory;

    @Override
    public org.bukkit.craftbukkit.inventory.view.CraftFurnaceView getBukkitView() {
        if (this.view != null) {
            return this.view;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryFurnace inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryFurnace((net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) this.container);
        this.view = new org.bukkit.craftbukkit.inventory.view.CraftFurnaceView(this.inventory.player.getBukkitEntity(), inventory, this);
        return this.view;
    }
    // CraftBukkit end

    protected AbstractFurnaceMenu(
        MenuType<?> menuType,
        RecipeType<? extends AbstractCookingRecipe> recipeType,
        ResourceKey<RecipePropertySet> acceptedInputs,
        RecipeBookType recipeBookType,
        int containerId,
        Inventory inventory
    ) {
        this(menuType, recipeType, acceptedInputs, recipeBookType, containerId, inventory, new SimpleContainer(3), new SimpleContainerData(4));
    }

    protected AbstractFurnaceMenu(
        MenuType<?> menuType,
        RecipeType<? extends AbstractCookingRecipe> recipeType,
        ResourceKey<RecipePropertySet> acceptedInputs,
        RecipeBookType recipeBookType,
        int containerId,
        Inventory inventory,
        Container container,
        ContainerData data
    ) {
        super(menuType, containerId);
        this.recipeType = recipeType;
        this.recipeBookType = recipeBookType;
        checkContainerSize(container, 3);
        checkContainerDataCount(data, 4);
        this.container = container;
        this.data = data;
        this.level = inventory.player.level();
        this.acceptedInputs = this.level.recipeAccess().propertySet(acceptedInputs);
        this.addSlot(new Slot(container, 0, 56, 17));
        this.addSlot(new FurnaceFuelSlot(this, container, 1, 56, 53));
        this.addSlot(new FurnaceResultSlot(inventory.player, container, 2, 116, 35));
        this.inventory = inventory; // CraftBukkit
        this.addStandardInventorySlots(inventory, 8, 84);
        this.addDataSlots(data);
    }

    @Override
    public void fillCraftSlotsStackedContents(StackedItemContents stackedItemContents) {
        if (this.container instanceof StackedContentsCompatible) {
            ((StackedContentsCompatible)this.container).fillStackedContents(stackedItemContents);
        }
    }

    public Slot getResultSlot() {
        return this.slots.get(2);
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            itemStack = item.copy();
            if (slotIndex == 2) {
                if (!this.moveItemStackTo(item, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(item, itemStack);
            } else if (slotIndex != 1 && slotIndex != 0) {
                if (this.canSmelt(item)) {
                    if (!this.moveItemStackTo(item, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (this.isFuel(item)) {
                    if (!this.moveItemStackTo(item, 1, 2, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex >= 3 && slotIndex < 30) {
                    if (!this.moveItemStackTo(item, 30, 39, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex >= 30 && slotIndex < 39 && !this.moveItemStackTo(item, 3, 30, false)) {
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

    protected boolean canSmelt(ItemStack stack) {
        return this.acceptedInputs.test(stack);
    }

    protected boolean isFuel(ItemStack stack) {
        return this.level.fuelValues().isFuel(stack);
    }

    public float getBurnProgress() {
        int i = this.data.get(2);
        int i1 = this.data.get(3);
        return i1 != 0 && i != 0 ? Mth.clamp((float)i / i1, 0.0F, 1.0F) : 0.0F;
    }

    public float getLitProgress() {
        int i = this.data.get(1);
        if (i == 0) {
            i = 200;
        }

        return Mth.clamp((float)this.data.get(0) / i, 0.0F, 1.0F);
    }

    public boolean isLit() {
        return this.data.get(0) > 0;
    }

    @Override
    public RecipeBookType getRecipeBookType() {
        return this.recipeBookType;
    }

    @Override
    public RecipeBookMenu.PostPlaceAction handlePlacement(
        boolean useMaxItems, boolean isCreative, RecipeHolder<?> recipe, final ServerLevel level, Inventory playerInventory
    ) {
        final List<Slot> list = List.of(this.getSlot(0), this.getSlot(2));
        return ServerPlaceRecipe.placeRecipe(new ServerPlaceRecipe.CraftingMenuAccess<AbstractCookingRecipe>() {
            @Override
            public void fillCraftSlotsStackedContents(StackedItemContents stackedItemContents) {
                AbstractFurnaceMenu.this.fillCraftSlotsStackedContents(stackedItemContents);
            }

            @Override
            public void clearCraftingContent() {
                list.forEach(slot -> slot.set(ItemStack.EMPTY));
            }

            @Override
            public boolean recipeMatches(RecipeHolder<AbstractCookingRecipe> recipe1) {
                return recipe1.value().matches(new SingleRecipeInput(AbstractFurnaceMenu.this.container.getItem(0)), level);
            }
        }, 1, 1, List.of(this.getSlot(0)), list, playerInventory, (RecipeHolder<AbstractCookingRecipe>)recipe, useMaxItems, isCreative);
    }
}
