package net.minecraft.world.item.crafting;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public interface CraftingRecipe extends Recipe<CraftingInput> {
    @Override
    default RecipeType<CraftingRecipe> getType() {
        return RecipeType.CRAFTING;
    }

    @Override
    RecipeSerializer<? extends CraftingRecipe> getSerializer();

    CraftingBookCategory category();

    default NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return defaultCraftingReminder(input);
    }

    static NonNullList<ItemStack> defaultCraftingReminder(CraftingInput input) {
        NonNullList<ItemStack> list = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        for (int i = 0; i < list.size(); i++) {
            Item item = input.getItem(i).getItem();
            list.set(i, item.getCraftingRemainder());
        }

        return list;
    }

    @Override
    default RecipeBookCategory recipeBookCategory() {
        return switch (this.category()) {
            case BUILDING -> RecipeBookCategories.CRAFTING_BUILDING_BLOCKS;
            case EQUIPMENT -> RecipeBookCategories.CRAFTING_EQUIPMENT;
            case REDSTONE -> RecipeBookCategories.CRAFTING_REDSTONE;
            case MISC -> RecipeBookCategories.CRAFTING_MISC;
        };
    }
}
