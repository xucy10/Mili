package net.minecraft.world.item.crafting;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SmokingRecipe extends AbstractCookingRecipe {
    public SmokingRecipe(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime) {
        super(group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    protected Item furnaceIcon() {
        return Items.SMOKER;
    }

    @Override
    public RecipeType<SmokingRecipe> getType() {
        return RecipeType.SMOKING;
    }

    @Override
    public RecipeSerializer<SmokingRecipe> getSerializer() {
        return RecipeSerializer.SMOKING_RECIPE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.SMOKER_FOOD;
    }

    // CraftBukkit start
    @Override
    public org.bukkit.inventory.Recipe toBukkitRecipe(org.bukkit.NamespacedKey id) {
        org.bukkit.craftbukkit.inventory.CraftItemStack result = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(this.result());

        org.bukkit.craftbukkit.inventory.CraftSmokingRecipe recipe = new org.bukkit.craftbukkit.inventory.CraftSmokingRecipe(id, result, org.bukkit.craftbukkit.inventory.CraftRecipe.toBukkit(this.input()), this.experience(), this.cookingTime());
        recipe.setGroup(this.group());
        recipe.setCategory(org.bukkit.craftbukkit.inventory.CraftRecipe.getCategory(this.category()));

        return recipe;
    }
    // CraftBukkit end
}
