package net.minecraft.data.recipes;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.RecipeUnlockedTrigger;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.equipment.trim.TrimPattern;

public class SmithingTrimRecipeBuilder {
    private final RecipeCategory category;
    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final Holder<TrimPattern> pattern;
    private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();

    public SmithingTrimRecipeBuilder(RecipeCategory category, Ingredient template, Ingredient base, Ingredient addition, Holder<TrimPattern> pattern) {
        this.category = category;
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.pattern = pattern;
    }

    public static SmithingTrimRecipeBuilder smithingTrim(
        Ingredient template, Ingredient base, Ingredient addition, Holder<TrimPattern> pattern, RecipeCategory category
    ) {
        return new SmithingTrimRecipeBuilder(category, template, base, addition, pattern);
    }

    public SmithingTrimRecipeBuilder unlocks(String key, Criterion<?> criterion) {
        this.criteria.put(key, criterion);
        return this;
    }

    public void save(RecipeOutput output, ResourceKey<Recipe<?>> resourceKey) {
        this.ensureValid(resourceKey);
        Advancement.Builder builder = output.advancement()
            .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(resourceKey))
            .rewards(AdvancementRewards.Builder.recipe(resourceKey))
            .requirements(AdvancementRequirements.Strategy.OR);
        this.criteria.forEach(builder::addCriterion);
        SmithingTrimRecipe smithingTrimRecipe = new SmithingTrimRecipe(this.template, this.base, this.addition, this.pattern);
        output.accept(resourceKey, smithingTrimRecipe, builder.build(resourceKey.identifier().withPrefix("recipes/" + this.category.getFolderName() + "/")));
    }

    private void ensureValid(ResourceKey<Recipe<?>> recipe) {
        if (this.criteria.isEmpty()) {
            throw new IllegalStateException("No way of obtaining recipe " + recipe.identifier());
        }
    }
}
