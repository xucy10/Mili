package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class ShapelessRecipe implements CraftingRecipe {
    final String group;
    final CraftingBookCategory category;
    final ItemStack result;
    final List<Ingredient> ingredients;
    private @Nullable PlacementInfo placementInfo;
    private final boolean isBukkit; // Gale - Airplane - simpler ShapelessRecipe comparison for vanilla

    public ShapelessRecipe(String group, CraftingBookCategory category, ItemStack result, List<Ingredient> ingredients) {
        // Gale start - Airplane - simpler ShapelessRecipe comparison for vanilla
        this(group, category, result, ingredients, false);
    }

    public ShapelessRecipe(String group, CraftingBookCategory category, ItemStack result, List<Ingredient> ingredients, boolean isBukkit) {
        this.isBukkit = isBukkit;
        // Gale end - Airplane - simpler ShapelessRecipe comparison for vanilla
        this.group = group;
        this.category = category;
        this.result = result;
        this.ingredients = ingredients;
    }

    // CraftBukkit start
    @Override
    public org.bukkit.inventory.ShapelessRecipe toBukkitRecipe(org.bukkit.NamespacedKey id) {
        org.bukkit.craftbukkit.inventory.CraftItemStack result = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(this.result);
        org.bukkit.craftbukkit.inventory.CraftShapelessRecipe recipe = new org.bukkit.craftbukkit.inventory.CraftShapelessRecipe(id, result, this);
        recipe.setGroup(this.group);
        recipe.setCategory(org.bukkit.craftbukkit.inventory.CraftRecipe.getCategory(this.category()));

        for (Ingredient list : this.ingredients) {
            recipe.addIngredient(org.bukkit.craftbukkit.inventory.CraftRecipe.toBukkit(list));
        }
        return recipe;
    }
    // CraftBukkit end

    @Override
    public RecipeSerializer<ShapelessRecipe> getSerializer() {
        return RecipeSerializer.SHAPELESS_RECIPE;
    }

    @Override
    public String group() {
        return this.group;
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    @Override
    public PlacementInfo placementInfo() {
        if (this.placementInfo == null) {
            this.placementInfo = PlacementInfo.create(this.ingredients);
        }

        return this.placementInfo;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        // Gale start - Airplane - simpler ShapelessRecipe comparison for vanilla
        if (!this.isBukkit) {
            java.util.List<Ingredient> ingredients = com.google.common.collect.Lists.newArrayList(this.ingredients.toArray(new Ingredient[0]));

            inventory:
            for (int index = 0; index < input.size(); index++) {
                ItemStack itemStack = input.getItem(index);

                if (!itemStack.isEmpty()) {
                    for (int i = 0; i < ingredients.size(); i++) {
                        if (ingredients.get(i).test(itemStack)) {
                            ingredients.remove(i);
                            continue inventory;
                        }
                    }

                    return false;
                }
            }

            return ingredients.isEmpty();
        }
        // Gale end - Airplane - simpler ShapelessRecipe comparison for vanilla
        // Paper start - Improve exact choice recipe ingredients & unwrap ternary
        if (input.ingredientCount() != this.ingredients.size()) {
            return false;
        }
        if (input.size() == 1 && this.ingredients.size() == 1) {
            return this.ingredients.getFirst().test(input.getItem(0));
        }
        input.stackedContents().initializeExtras(this, input);
        boolean canCraft = input.stackedContents().canCraft(this, null);
        input.stackedContents().resetExtras();
        return canCraft;
        // Paper end - Improve exact choice recipe ingredients & unwrap ternary
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(
            new ShapelessCraftingRecipeDisplay(
                this.ingredients.stream().map(Ingredient::display).toList(),
                new SlotDisplay.ItemStackSlotDisplay(this.result),
                new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)
            )
        );
    }

    public static class Serializer implements RecipeSerializer<ShapelessRecipe> {
        private static final MapCodec<ShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter(recipe -> recipe.group),
                    CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(recipe -> recipe.category),
                    ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                    Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(recipe -> recipe.ingredients)
                )
                .apply(instance, ShapelessRecipe::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, ShapelessRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            recipe -> recipe.group,
            CraftingBookCategory.STREAM_CODEC,
            recipe -> recipe.category,
            ItemStack.STREAM_CODEC,
            recipe -> recipe.result,
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
            recipe -> recipe.ingredients,
            ShapelessRecipe::new
        );

        @Override
        public MapCodec<ShapelessRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ShapelessRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
