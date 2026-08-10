package net.minecraft.data.recipes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.RecipeUnlockedTrigger;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

public class ShapelessRecipeBuilder implements RecipeBuilder {
    private final HolderGetter<Item> items;
    private final RecipeCategory category;
    private final ItemStack result;
    private final List<Ingredient> ingredients = new ArrayList<>();
    private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();
    private @Nullable String group;

    private ShapelessRecipeBuilder(HolderGetter<Item> items, RecipeCategory category, ItemStack result) {
        this.items = items;
        this.category = category;
        this.result = result;
    }

    public static ShapelessRecipeBuilder shapeless(HolderGetter<Item> items, RecipeCategory category, ItemStack result) {
        return new ShapelessRecipeBuilder(items, category, result);
    }

    public static ShapelessRecipeBuilder shapeless(HolderGetter<Item> items, RecipeCategory category, ItemLike result) {
        return shapeless(items, category, result, 1);
    }

    public static ShapelessRecipeBuilder shapeless(HolderGetter<Item> items, RecipeCategory category, ItemLike result, int count) {
        return new ShapelessRecipeBuilder(items, category, result.asItem().getDefaultInstance().copyWithCount(count));
    }

    public ShapelessRecipeBuilder requires(TagKey<Item> tag) {
        return this.requires(Ingredient.of(this.items.getOrThrow(tag)));
    }

    public ShapelessRecipeBuilder requires(ItemLike item) {
        return this.requires(item, 1);
    }

    public ShapelessRecipeBuilder requires(ItemLike item, int quantity) {
        for (int i = 0; i < quantity; i++) {
            this.requires(Ingredient.of(item));
        }

        return this;
    }

    public ShapelessRecipeBuilder requires(Ingredient ingredient) {
        return this.requires(ingredient, 1);
    }

    public ShapelessRecipeBuilder requires(Ingredient ingredient, int quantity) {
        for (int i = 0; i < quantity; i++) {
            this.ingredients.add(ingredient);
        }

        return this;
    }

    @Override
    public ShapelessRecipeBuilder unlockedBy(String name, Criterion<?> criterion) {
        this.criteria.put(name, criterion);
        return this;
    }

    @Override
    public ShapelessRecipeBuilder group(@Nullable String groupName) {
        this.group = groupName;
        return this;
    }

    @Override
    public Item getResult() {
        return this.result.getItem();
    }

    @Override
    public void save(RecipeOutput output, ResourceKey<Recipe<?>> resourceKey) {
        this.ensureValid(resourceKey);
        Advancement.Builder builder = output.advancement()
            .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(resourceKey))
            .rewards(AdvancementRewards.Builder.recipe(resourceKey))
            .requirements(AdvancementRequirements.Strategy.OR);
        this.criteria.forEach(builder::addCriterion);
        ShapelessRecipe shapelessRecipe = new ShapelessRecipe(
            Objects.requireNonNullElse(this.group, ""), RecipeBuilder.determineBookCategory(this.category), this.result, this.ingredients
        );
        output.accept(resourceKey, shapelessRecipe, builder.build(resourceKey.identifier().withPrefix("recipes/" + this.category.getFolderName() + "/")));
    }

    private void ensureValid(ResourceKey<Recipe<?>> recipe) {
        if (this.criteria.isEmpty()) {
            throw new IllegalStateException("No way of obtaining recipe " + recipe.identifier());
        }
    }
}
