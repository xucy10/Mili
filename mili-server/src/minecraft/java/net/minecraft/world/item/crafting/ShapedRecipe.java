package net.minecraft.world.item.crafting;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class ShapedRecipe implements CraftingRecipe {
    final ShapedRecipePattern pattern;
    final ItemStack result;
    final String group;
    final CraftingBookCategory category;
    final boolean showNotification;
    private @Nullable PlacementInfo placementInfo;

    public ShapedRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification) {
        this.group = group;
        this.category = category;
        this.pattern = pattern;
        this.result = result;
        this.showNotification = showNotification;
    }

    public ShapedRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result) {
        this(group, category, pattern, result, true);
    }

    @Override
    public RecipeSerializer<? extends ShapedRecipe> getSerializer() {
        return RecipeSerializer.SHAPED_RECIPE;
    }

    @Override
    public String group() {
        return this.group;
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    @VisibleForTesting
    public List<Optional<Ingredient>> getIngredients() {
        return this.pattern.ingredients();
    }

    @Override
    public PlacementInfo placementInfo() {
        if (this.placementInfo == null) {
            this.placementInfo = PlacementInfo.createFromOptionals(this.pattern.ingredients());
        }

        return this.placementInfo;
    }

    @Override
    public boolean showNotification() {
        return this.showNotification;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.pattern.matches(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return this.result.copy();
    }

    public int getWidth() {
        return this.pattern.width();
    }

    public int getHeight() {
        return this.pattern.height();
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(
            new ShapedCraftingRecipeDisplay(
                this.pattern.width(),
                this.pattern.height(),
                this.pattern.ingredients().stream().map(optional -> optional.map(Ingredient::display).orElse(SlotDisplay.Empty.INSTANCE)).toList(),
                new SlotDisplay.ItemStackSlotDisplay(this.result),
                new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)
            )
        );
    }

    // CraftBukkit start
    @Override
    public org.bukkit.inventory.ShapedRecipe toBukkitRecipe(org.bukkit.NamespacedKey id) {
        org.bukkit.craftbukkit.inventory.CraftItemStack result = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(this.result);
        org.bukkit.craftbukkit.inventory.CraftShapedRecipe recipe = new org.bukkit.craftbukkit.inventory.CraftShapedRecipe(id, result, this);
        recipe.setGroup(this.group);
        recipe.setCategory(org.bukkit.craftbukkit.inventory.CraftRecipe.getCategory(this.category()));

        switch (this.pattern.height()) {
        case 1:
            switch (this.pattern.width()) {
            case 1:
                recipe.shape("a");
                break;
            case 2:
                recipe.shape("ab");
                break;
            case 3:
                recipe.shape("abc");
                break;
            }
            break;
        case 2:
            switch (this.pattern.width()) {
            case 1:
                recipe.shape("a","b");
                break;
            case 2:
                recipe.shape("ab","cd");
                break;
            case 3:
                recipe.shape("abc","def");
                break;
            }
            break;
        case 3:
            switch (this.pattern.width()) {
            case 1:
                recipe.shape("a","b","c");
                break;
            case 2:
                recipe.shape("ab","cd","ef");
                break;
            case 3:
                recipe.shape("abc","def","ghi");
                break;
            }
            break;
        }
        char c = 'a';
        for (Optional<Ingredient> list : this.pattern.ingredients()) {
            org.bukkit.inventory.RecipeChoice choice = org.bukkit.craftbukkit.inventory.CraftRecipe.toBukkit(list);
            if (choice != org.bukkit.inventory.RecipeChoice.empty()) { // Paper
                recipe.setIngredient(c, choice);
            }

            c++;
        }
        return recipe;
    }
    // CraftBukkit end

    public static class Serializer implements RecipeSerializer<ShapedRecipe> {
        public static final MapCodec<ShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter(shapedRecipe -> shapedRecipe.group),
                    CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(shapedRecipe -> shapedRecipe.category),
                    ShapedRecipePattern.MAP_CODEC.forGetter(shapedRecipe -> shapedRecipe.pattern),
                    ItemStack.STRICT_CODEC.fieldOf("result").forGetter(shapedRecipe -> shapedRecipe.result),
                    Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(shapedRecipe -> shapedRecipe.showNotification)
                )
                .apply(instance, ShapedRecipe::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, ShapedRecipe> STREAM_CODEC = StreamCodec.of(
            ShapedRecipe.Serializer::toNetwork, ShapedRecipe.Serializer::fromNetwork
        );

        @Override
        public MapCodec<ShapedRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ShapedRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static ShapedRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String utf = buffer.readUtf();
            CraftingBookCategory craftingBookCategory = buffer.readEnum(CraftingBookCategory.class);
            ShapedRecipePattern shapedRecipePattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer);
            ItemStack itemStack = ItemStack.STREAM_CODEC.decode(buffer);
            boolean _boolean = buffer.readBoolean();
            return new ShapedRecipe(utf, craftingBookCategory, shapedRecipePattern, itemStack, _boolean);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, ShapedRecipe recipe) {
            buffer.writeUtf(recipe.group);
            buffer.writeEnum(recipe.category);
            ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
            buffer.writeBoolean(recipe.showNotification);
        }
    }
}
