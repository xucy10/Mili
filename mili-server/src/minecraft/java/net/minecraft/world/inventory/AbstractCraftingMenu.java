package net.minecraft.world.inventory;

import java.util.List;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

public abstract class AbstractCraftingMenu extends RecipeBookMenu {
    private final int width;
    private final int height;
    public final TransientCraftingContainer craftSlots; // CraftBukkit
    public final ResultContainer resultSlots = new ResultContainer();

    public AbstractCraftingMenu(MenuType<?> menuType, int containerId, int width, int height, Inventory playerInventory) { // CraftBukkit
        super(menuType, containerId);
        this.width = width;
        this.height = height;
        // CraftBukkit start
        this.craftSlots = new TransientCraftingContainer(this, width, height, playerInventory.player);
        this.craftSlots.resultInventory = this.resultSlots; // let InventoryCrafting know about its result slot
        // CraftBukkit end
    }

    protected Slot addResultSlot(Player player, int x, int y) {
        return this.addSlot(new ResultSlot(player, this.craftSlots, this.resultSlots, 0, x, y));
    }

    protected void addCraftingGridSlots(int x, int y) {
        for (int i = 0; i < this.width; i++) {
            for (int i1 = 0; i1 < this.height; i1++) {
                this.addSlot(new Slot(this.craftSlots, i1 + i * this.width, x + i1 * 18, y + i * 18));
            }
        }
    }

    @Override
    public RecipeBookMenu.PostPlaceAction handlePlacement(
        boolean useMaxItems, boolean isCreative, RecipeHolder<?> recipe, ServerLevel level, Inventory playerInventory
    ) {
        RecipeHolder<CraftingRecipe> recipeHolder = (RecipeHolder<CraftingRecipe>)recipe;
        this.beginPlacingRecipe();

        RecipeBookMenu.PostPlaceAction var8;
        try {
            List<Slot> inputGridSlots = this.getInputGridSlots();
            var8 = ServerPlaceRecipe.placeRecipe(new ServerPlaceRecipe.CraftingMenuAccess<CraftingRecipe>() {
                @Override
                public void fillCraftSlotsStackedContents(StackedItemContents stackedItemContents) {
                    AbstractCraftingMenu.this.fillCraftSlotsStackedContents(stackedItemContents);
                }

                @Override
                public void clearCraftingContent() {
                    AbstractCraftingMenu.this.resultSlots.clearContent();
                    AbstractCraftingMenu.this.craftSlots.clearContent();
                }

                @Override
                public boolean recipeMatches(RecipeHolder<CraftingRecipe> recipe1) {
                    return recipe1.value().matches(AbstractCraftingMenu.this.craftSlots.asCraftInput(), AbstractCraftingMenu.this.owner().level());
                }
            }, this.width, this.height, inputGridSlots, inputGridSlots, playerInventory, recipeHolder, useMaxItems, isCreative);
        } finally {
            this.finishPlacingRecipe(level, (RecipeHolder<CraftingRecipe>)recipe);
        }

        return var8;
    }

    protected void beginPlacingRecipe() {
    }

    protected void finishPlacingRecipe(ServerLevel level, RecipeHolder<CraftingRecipe> recipe) {
    }

    public abstract Slot getResultSlot();

    public abstract List<Slot> getInputGridSlots();

    public int getGridWidth() {
        return this.width;
    }

    public int getGridHeight() {
        return this.height;
    }

    protected abstract Player owner();

    @Override
    public void fillCraftSlotsStackedContents(StackedItemContents stackedItemContents) {
        this.craftSlots.fillStackedContents(stackedItemContents);
    }
}
