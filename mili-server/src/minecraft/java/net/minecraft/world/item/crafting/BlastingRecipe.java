package net.minecraft.world.item.crafting;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BlastingRecipe extends AbstractCookingRecipe {
    public BlastingRecipe(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime) {
        super(group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    protected Item furnaceIcon() {
        return Items.BLAST_FURNACE;
    }

    @Override
    public RecipeSerializer<BlastingRecipe> getSerializer() {
        return RecipeSerializer.BLASTING_RECIPE;
    }

    @Override
    public RecipeType<BlastingRecipe> getType() {
        return RecipeType.BLASTING;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return switch (this.category()) {
            case BLOCKS -> RecipeBookCategories.BLAST_FURNACE_BLOCKS;
            case FOOD, MISC -> RecipeBookCategories.BLAST_FURNACE_MISC;
        };
    }

    // CraftBukkit start
    @Override
    public org.bukkit.inventory.Recipe toBukkitRecipe(org.bukkit.NamespacedKey id) {
        org.bukkit.craftbukkit.inventory.CraftItemStack result = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(this.result());

        org.bukkit.craftbukkit.inventory.CraftBlastingRecipe recipe = new org.bukkit.craftbukkit.inventory.CraftBlastingRecipe(id, result, org.bukkit.craftbukkit.inventory.CraftRecipe.toBukkit(this.input()), this.experience(), this.cookingTime());
        recipe.setGroup(this.group());
        recipe.setCategory(org.bukkit.craftbukkit.inventory.CraftRecipe.getCategory(this.category()));

        return recipe;
    }
    // CraftBukkit end
}
