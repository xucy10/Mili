package net.minecraft.data.recipes;

import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Recipe;

public class SpecialRecipeBuilder {
    private final Function<CraftingBookCategory, Recipe<?>> factory;

    public SpecialRecipeBuilder(Function<CraftingBookCategory, Recipe<?>> factory) {
        this.factory = factory;
    }

    public static SpecialRecipeBuilder special(Function<CraftingBookCategory, Recipe<?>> factory) {
        return new SpecialRecipeBuilder(factory);
    }

    public void save(RecipeOutput recipeOutput, String recipeId) {
        this.save(recipeOutput, ResourceKey.create(Registries.RECIPE, Identifier.parse(recipeId)));
    }

    public void save(RecipeOutput output, ResourceKey<Recipe<?>> resourceKey) {
        output.accept(resourceKey, this.factory.apply(CraftingBookCategory.MISC), null);
    }
}
