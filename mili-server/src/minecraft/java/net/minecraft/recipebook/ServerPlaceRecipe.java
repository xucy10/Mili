package net.minecraft.recipebook;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;

public class ServerPlaceRecipe<R extends Recipe<?>> {
    private static final int ITEM_NOT_FOUND = -1;
    private final Inventory inventory;
    private final ServerPlaceRecipe.CraftingMenuAccess<R> menu;
    private final boolean useMaxItems;
    private final int gridWidth;
    private final int gridHeight;
    private final List<Slot> inputGridSlots;
    private final List<Slot> slotsToClear;

    public static <I extends RecipeInput, R extends Recipe<I>> RecipeBookMenu.PostPlaceAction placeRecipe(
        ServerPlaceRecipe.CraftingMenuAccess<R> menu,
        int gridWidth,
        int gridHeight,
        List<Slot> inputGridSlots,
        List<Slot> slotsToClear,
        Inventory inventory,
        RecipeHolder<R> recipe,
        boolean useMaxItems,
        boolean isCreative
    ) {
        ServerPlaceRecipe<R> serverPlaceRecipe = new ServerPlaceRecipe<>(menu, inventory, useMaxItems, gridWidth, gridHeight, inputGridSlots, slotsToClear);
        if (!isCreative && !serverPlaceRecipe.testClearGrid()) {
            return RecipeBookMenu.PostPlaceAction.NOTHING;
        } else {
            StackedItemContents stackedItemContents = new StackedItemContents();
            stackedItemContents.initializeExtras(recipe.value(), null); // Paper - Improve exact choice recipe ingredients
            inventory.fillStackedContents(stackedItemContents);
            menu.fillCraftSlotsStackedContents(stackedItemContents);
            return serverPlaceRecipe.tryPlaceRecipe(recipe, stackedItemContents);
        }
    }

    private ServerPlaceRecipe(
        ServerPlaceRecipe.CraftingMenuAccess<R> menu,
        Inventory inventory,
        boolean useMaxItems,
        int gridWidth,
        int gridHeight,
        List<Slot> inputGridSlots,
        List<Slot> slotsToClear
    ) {
        this.menu = menu;
        this.inventory = inventory;
        this.useMaxItems = useMaxItems;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.inputGridSlots = inputGridSlots;
        this.slotsToClear = slotsToClear;
    }

    private RecipeBookMenu.PostPlaceAction tryPlaceRecipe(RecipeHolder<R> recipe, StackedItemContents stackedItemContents) {
        if (stackedItemContents.canCraft(recipe.value(), null)) {
            this.placeRecipe(recipe, stackedItemContents);
            this.inventory.setChanged();
            return RecipeBookMenu.PostPlaceAction.NOTHING;
        } else {
            this.clearGrid();
            this.inventory.setChanged();
            return RecipeBookMenu.PostPlaceAction.PLACE_GHOST_RECIPE;
        }
    }

    private void clearGrid() {
        for (Slot slot : this.slotsToClear) {
            ItemStack itemStack = slot.getItem().copy();
            this.inventory.placeItemBackInInventory(itemStack, false);
            slot.set(itemStack);
        }

        this.menu.clearCraftingContent();
    }

    private void placeRecipe(RecipeHolder<R> recipe, StackedItemContents stackedItemContents) {
        boolean flag = this.menu.recipeMatches(recipe);
        int biggestCraftableStack = stackedItemContents.getBiggestCraftableStack(recipe.value(), null);
        if (flag) {
            for (Slot slot : this.inputGridSlots) {
                ItemStack item = slot.getItem();
                if (!item.isEmpty() && Math.min(biggestCraftableStack, item.getMaxStackSize()) < item.getCount() + 1) {
                    return;
                }
            }
        }

        int i = this.calculateAmountToCraft(biggestCraftableStack, flag);
        List<io.papermc.paper.inventory.recipe.ItemOrExact> list = new ArrayList<>(); // Paper - Improve exact choice recipe ingredients
        if (stackedItemContents.canCraft(recipe.value(), i, list::add)) {
            int i1 = clampToMaxStackSize(i, list);
            if (i1 != i) {
                list.clear();
                if (!stackedItemContents.canCraft(recipe.value(), i1, list::add)) {
                    return;
                }
            }

            this.clearGrid();
            PlaceRecipeHelper.placeRecipe(
                this.gridWidth, this.gridHeight, recipe.value(), recipe.value().placementInfo().slotsToIngredientIndex(), (item1, slot1, x, y) -> {
                    if (item1 != -1) {
                        Slot slot2 = this.inputGridSlots.get(slot1);
                        io.papermc.paper.inventory.recipe.ItemOrExact holder = list.get(item1); // Paper - Improve exact choice recipe ingredients
                        int i2 = i1;

                        while (i2 > 0) {
                            i2 = this.moveItemToGrid(slot2, holder, i2);
                            if (i2 == -1) {
                                return;
                            }
                        }
                    }
                }
            );
        }
    }

    // Paper start - Improve exact choice recipe ingredients
    private static int clampToMaxStackSize(int amount, List<io.papermc.paper.inventory.recipe.ItemOrExact> items) {
        for (io.papermc.paper.inventory.recipe.ItemOrExact holder : items) {
            amount = Math.min(amount, holder.getMaxStackSize());
            // Paper end - Improve exact choice recipe ingredients
        }

        return amount;
    }

    private int calculateAmountToCraft(int max, boolean recipeMatches) {
        if (this.useMaxItems) {
            return max;
        } else if (recipeMatches) {
            int i = Integer.MAX_VALUE;

            for (Slot slot : this.inputGridSlots) {
                ItemStack item = slot.getItem();
                if (!item.isEmpty() && i > item.getCount()) {
                    i = item.getCount();
                }
            }

            if (i != Integer.MAX_VALUE) {
                i++;
            }

            return i;
        } else {
            return 1;
        }
    }

    private int moveItemToGrid(Slot slot, io.papermc.paper.inventory.recipe.ItemOrExact item, int count) { // Paper - Improve exact choice recipe ingredients
        ItemStack item1 = slot.getItem();
        int i = this.inventory.findSlotMatchingCraftingIngredient(item, item1);
        if (i == -1) {
            return -1;
        } else {
            ItemStack item2 = this.inventory.getItem(i);
            ItemStack itemStack;
            if (count < item2.getCount()) {
                itemStack = this.inventory.removeItem(i, count);
            } else {
                itemStack = this.inventory.removeItemNoUpdate(i);
            }

            int count1 = itemStack.getCount();
            if (item1.isEmpty()) {
                slot.set(itemStack);
            } else {
                item1.grow(count1);
            }

            return count - count1;
        }
    }

    private boolean testClearGrid() {
        List<ItemStack> list = Lists.newArrayList();
        int amountOfFreeSlotsInInventory = this.getAmountOfFreeSlotsInInventory();

        for (Slot slot : this.inputGridSlots) {
            ItemStack itemStack = slot.getItem().copy();
            if (!itemStack.isEmpty()) {
                int slotWithRemainingSpace = this.inventory.getSlotWithRemainingSpace(itemStack);
                if (slotWithRemainingSpace == -1 && list.size() <= amountOfFreeSlotsInInventory) {
                    for (ItemStack itemStack1 : list) {
                        if (ItemStack.isSameItem(itemStack1, itemStack)
                            && itemStack1.getCount() != itemStack1.getMaxStackSize()
                            && itemStack1.getCount() + itemStack.getCount() <= itemStack1.getMaxStackSize()) {
                            itemStack1.grow(itemStack.getCount());
                            itemStack.setCount(0);
                            break;
                        }
                    }

                    if (!itemStack.isEmpty()) {
                        if (list.size() >= amountOfFreeSlotsInInventory) {
                            return false;
                        }

                        list.add(itemStack);
                    }
                } else if (slotWithRemainingSpace == -1) {
                    return false;
                }
            }
        }

        return true;
    }

    private int getAmountOfFreeSlotsInInventory() {
        int i = 0;

        for (ItemStack itemStack : this.inventory.getNonEquipmentItems()) {
            if (itemStack.isEmpty()) {
                i++;
            }
        }

        return i;
    }

    public interface CraftingMenuAccess<T extends Recipe<?>> {
        void fillCraftSlotsStackedContents(StackedItemContents stackedItemContents);

        void clearCraftingContent();

        boolean recipeMatches(RecipeHolder<T> recipe);
    }
}
