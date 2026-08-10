package net.minecraft.data.recipes;

import net.minecraft.advancements.Criterion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

public interface RecipeBuilder {
    Identifier ROOT_RECIPE_ADVANCEMENT = Identifier.withDefaultNamespace("recipes/root");

    RecipeBuilder unlockedBy(String name, Criterion<?> criterion);

    RecipeBuilder group(@Nullable String groupName);

    Item getResult();

    void save(RecipeOutput output, ResourceKey<Recipe<?>> resourceKey);

    default void save(RecipeOutput recipeOutput) {
        this.save(recipeOutput, ResourceKey.create(Registries.RECIPE, getDefaultRecipeId(this.getResult())));
    }

    default void save(RecipeOutput recipeOutput, String id) {
        Identifier defaultRecipeId = getDefaultRecipeId(this.getResult());
        Identifier identifier = Identifier.parse(id);
        if (identifier.equals(defaultRecipeId)) {
            throw new IllegalStateException("Recipe " + id + " should remove its 'save' argument as it is equal to default one");
        } else {
            this.save(recipeOutput, ResourceKey.create(Registries.RECIPE, identifier));
        }
    }

    static Identifier getDefaultRecipeId(ItemLike item) {
        return BuiltInRegistries.ITEM.getKey(item.asItem());
    }

    static CraftingBookCategory determineBookCategory(RecipeCategory category) {
        return switch (category) {
            case BUILDING_BLOCKS -> CraftingBookCategory.BUILDING;
            case TOOLS, COMBAT -> CraftingBookCategory.EQUIPMENT;
            case REDSTONE -> CraftingBookCategory.REDSTONE;
            default -> CraftingBookCategory.MISC;
        };
    }
}
