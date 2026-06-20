package net.minecraft.data.recipes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.RecipeUnlockedTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

public class SingleItemRecipeBuilder implements RecipeBuilder {
    private final RecipeCategory category;
    private final Item result;
    private final Ingredient ingredient;
    private final int count;
    private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();
    private @Nullable String group;
    private final SingleItemRecipe.Factory<?> factory;

    public SingleItemRecipeBuilder(RecipeCategory category, SingleItemRecipe.Factory<?> factory, Ingredient ingredient, ItemLike result, int count) {
        this.category = category;
        this.factory = factory;
        this.result = result.asItem();
        this.ingredient = ingredient;
        this.count = count;
    }

    public static SingleItemRecipeBuilder stonecutting(Ingredient ingredient, RecipeCategory category, ItemLike result) {
        return new SingleItemRecipeBuilder(category, StonecutterRecipe::new, ingredient, result, 1);
    }

    public static SingleItemRecipeBuilder stonecutting(Ingredient ingredient, RecipeCategory category, ItemLike result, int count) {
        return new SingleItemRecipeBuilder(category, StonecutterRecipe::new, ingredient, result, count);
    }

    @Override
    public SingleItemRecipeBuilder unlockedBy(String name, Criterion<?> criterion) {
        this.criteria.put(name, criterion);
        return this;
    }

    @Override
    public SingleItemRecipeBuilder group(@Nullable String groupName) {
        this.group = groupName;
        return this;
    }

    @Override
    public Item getResult() {
        return this.result;
    }

    @Override
    public void save(RecipeOutput output, ResourceKey<Recipe<?>> resourceKey) {
        this.ensureValid(resourceKey);
        Advancement.Builder builder = output.advancement()
            .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(resourceKey))
            .rewards(AdvancementRewards.Builder.recipe(resourceKey))
            .requirements(AdvancementRequirements.Strategy.OR);
        this.criteria.forEach(builder::addCriterion);
        SingleItemRecipe singleItemRecipe = this.factory
            .create(Objects.requireNonNullElse(this.group, ""), this.ingredient, new ItemStack(this.result, this.count));
        output.accept(resourceKey, singleItemRecipe, builder.build(resourceKey.identifier().withPrefix("recipes/" + this.category.getFolderName() + "/")));
    }

    private void ensureValid(ResourceKey<Recipe<?>> recipe) {
        if (this.criteria.isEmpty()) {
            throw new IllegalStateException("No way of obtaining recipe " + recipe.identifier());
        }
    }
}
