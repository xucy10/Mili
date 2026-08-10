package net.minecraft.data.recipes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.RecipeUnlockedTrigger;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

public class SimpleCookingRecipeBuilder implements RecipeBuilder {
    private final RecipeCategory category;
    private final CookingBookCategory bookCategory;
    private final Item result;
    private final Ingredient ingredient;
    private final float experience;
    private final int cookingTime;
    private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();
    private @Nullable String group;
    private final AbstractCookingRecipe.Factory<?> factory;

    private SimpleCookingRecipeBuilder(
        RecipeCategory category,
        CookingBookCategory bookCategory,
        ItemLike result,
        Ingredient ingredient,
        float experience,
        int cookingTime,
        AbstractCookingRecipe.Factory<?> factory
    ) {
        this.category = category;
        this.bookCategory = bookCategory;
        this.result = result.asItem();
        this.ingredient = ingredient;
        this.experience = experience;
        this.cookingTime = cookingTime;
        this.factory = factory;
    }

    public static <T extends AbstractCookingRecipe> SimpleCookingRecipeBuilder generic(
        Ingredient ingredient,
        RecipeCategory category,
        ItemLike result,
        float experience,
        int cookingTime,
        RecipeSerializer<T> cookingSerializer,
        AbstractCookingRecipe.Factory<T> factory
    ) {
        return new SimpleCookingRecipeBuilder(
            category, determineRecipeCategory(cookingSerializer, result), result, ingredient, experience, cookingTime, factory
        );
    }

    public static SimpleCookingRecipeBuilder campfireCooking(Ingredient ingredient, RecipeCategory category, ItemLike result, float experience, int cookingTime) {
        return new SimpleCookingRecipeBuilder(category, CookingBookCategory.FOOD, result, ingredient, experience, cookingTime, CampfireCookingRecipe::new);
    }

    public static SimpleCookingRecipeBuilder blasting(Ingredient ingredient, RecipeCategory category, ItemLike result, float experience, int cookingTime) {
        return new SimpleCookingRecipeBuilder(
            category, determineBlastingRecipeCategory(result), result, ingredient, experience, cookingTime, BlastingRecipe::new
        );
    }

    public static SimpleCookingRecipeBuilder smelting(Ingredient ingredient, RecipeCategory category, ItemLike result, float experience, int cookingTime) {
        return new SimpleCookingRecipeBuilder(
            category, determineSmeltingRecipeCategory(result), result, ingredient, experience, cookingTime, SmeltingRecipe::new
        );
    }

    public static SimpleCookingRecipeBuilder smoking(Ingredient ingredient, RecipeCategory category, ItemLike result, float experience, int cookingTime) {
        return new SimpleCookingRecipeBuilder(category, CookingBookCategory.FOOD, result, ingredient, experience, cookingTime, SmokingRecipe::new);
    }

    @Override
    public SimpleCookingRecipeBuilder unlockedBy(String name, Criterion<?> criterion) {
        this.criteria.put(name, criterion);
        return this;
    }

    @Override
    public SimpleCookingRecipeBuilder group(@Nullable String groupName) {
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
        AbstractCookingRecipe abstractCookingRecipe = this.factory
            .create(
                Objects.requireNonNullElse(this.group, ""), this.bookCategory, this.ingredient, new ItemStack(this.result), this.experience, this.cookingTime
            );
        output.accept(resourceKey, abstractCookingRecipe, builder.build(resourceKey.identifier().withPrefix("recipes/" + this.category.getFolderName() + "/")));
    }

    private static CookingBookCategory determineSmeltingRecipeCategory(ItemLike result) {
        if (result.asItem().components().has(DataComponents.FOOD)) {
            return CookingBookCategory.FOOD;
        } else {
            return result.asItem() instanceof BlockItem ? CookingBookCategory.BLOCKS : CookingBookCategory.MISC;
        }
    }

    private static CookingBookCategory determineBlastingRecipeCategory(ItemLike result) {
        return result.asItem() instanceof BlockItem ? CookingBookCategory.BLOCKS : CookingBookCategory.MISC;
    }

    private static CookingBookCategory determineRecipeCategory(RecipeSerializer<? extends AbstractCookingRecipe> serializer, ItemLike result) {
        if (serializer == RecipeSerializer.SMELTING_RECIPE) {
            return determineSmeltingRecipeCategory(result);
        } else if (serializer == RecipeSerializer.BLASTING_RECIPE) {
            return determineBlastingRecipeCategory(result);
        } else if (serializer != RecipeSerializer.SMOKING_RECIPE && serializer != RecipeSerializer.CAMPFIRE_COOKING_RECIPE) {
            throw new IllegalStateException("Unknown cooking recipe type");
        } else {
            return CookingBookCategory.FOOD;
        }
    }

    private void ensureValid(ResourceKey<Recipe<?>> recipe) {
        if (this.criteria.isEmpty()) {
            throw new IllegalStateException("No way of obtaining recipe " + recipe.identifier());
        }
    }
}
