package net.minecraft.data.recipes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.RecipeUnlockedTrigger;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.crafting.TransmuteResult;
import org.jspecify.annotations.Nullable;

public class TransmuteRecipeBuilder implements RecipeBuilder {
    private final RecipeCategory category;
    private final Holder<Item> result;
    private final Ingredient input;
    private final Ingredient material;
    private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();
    private @Nullable String group;

    private TransmuteRecipeBuilder(RecipeCategory category, Holder<Item> result, Ingredient input, Ingredient material) {
        this.category = category;
        this.result = result;
        this.input = input;
        this.material = material;
    }

    public static TransmuteRecipeBuilder transmute(RecipeCategory category, Ingredient input, Ingredient material, Item result) {
        return new TransmuteRecipeBuilder(category, result.builtInRegistryHolder(), input, material);
    }

    @Override
    public TransmuteRecipeBuilder unlockedBy(String name, Criterion<?> criterion) {
        this.criteria.put(name, criterion);
        return this;
    }

    @Override
    public TransmuteRecipeBuilder group(@Nullable String groupName) {
        this.group = groupName;
        return this;
    }

    @Override
    public Item getResult() {
        return this.result.value();
    }

    @Override
    public void save(RecipeOutput output, ResourceKey<Recipe<?>> resourceKey) {
        this.ensureValid(resourceKey);
        Advancement.Builder builder = output.advancement()
            .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(resourceKey))
            .rewards(AdvancementRewards.Builder.recipe(resourceKey))
            .requirements(AdvancementRequirements.Strategy.OR);
        this.criteria.forEach(builder::addCriterion);
        TransmuteRecipe transmuteRecipe = new TransmuteRecipe(
            Objects.requireNonNullElse(this.group, ""),
            RecipeBuilder.determineBookCategory(this.category),
            this.input,
            this.material,
            new TransmuteResult(this.result.value())
        );
        output.accept(resourceKey, transmuteRecipe, builder.build(resourceKey.identifier().withPrefix("recipes/" + this.category.getFolderName() + "/")));
    }

    private void ensureValid(ResourceKey<Recipe<?>> recipe) {
        if (this.criteria.isEmpty()) {
            throw new IllegalStateException("No way of obtaining recipe " + recipe.identifier());
        }
    }
}
