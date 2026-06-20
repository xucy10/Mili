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

public class TransmuteRecipe implements CraftingRecipe {
    final String group;
    final CraftingBookCategory category;
    final Ingredient input;
    final Ingredient material;
    final TransmuteResult result;
    private @Nullable PlacementInfo placementInfo;

    public TransmuteRecipe(String group, CraftingBookCategory category, Ingredient input, Ingredient material, TransmuteResult result) {
        this.group = group;
        this.category = category;
        this.input = input;
        this.material = material;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != 2) {
            return false;
        } else {
            boolean flag = false;
            boolean flag1 = false;

            for (int i = 0; i < input.size(); i++) {
                ItemStack item = input.getItem(i);
                if (!item.isEmpty()) {
                    if (!flag && this.input.test(item)) {
                        if (this.result.isResultUnchanged(item)) {
                            return false;
                        }

                        flag = true;
                    } else {
                        if (flag1 || !this.material.test(item)) {
                            return false;
                        }

                        flag1 = true;
                    }
                }
            }

            return flag && flag1;
        }
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack item = input.getItem(i);
            if (!item.isEmpty() && this.input.test(item)) {
                return this.result.apply(item);
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(
            new ShapelessCraftingRecipeDisplay(
                List.of(this.input.display(), this.material.display()), this.result.display(), new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)
            )
        );
    }

    // CraftBukkit start
    @Override
    public org.bukkit.inventory.Recipe toBukkitRecipe(org.bukkit.NamespacedKey id) {
        return new org.bukkit.craftbukkit.inventory.CraftTransmuteRecipe(id, org.bukkit.craftbukkit.inventory.CraftItemType.minecraftToBukkit(this.result.item().value()), org.bukkit.craftbukkit.inventory.CraftRecipe.toBukkit(this.input), org.bukkit.craftbukkit.inventory.CraftRecipe.toBukkit(this.material));
    }
    // CraftBukkit end

    @Override
    public RecipeSerializer<TransmuteRecipe> getSerializer() {
        return RecipeSerializer.TRANSMUTE;
    }

    @Override
    public String group() {
        return this.group;
    }

    @Override
    public PlacementInfo placementInfo() {
        if (this.placementInfo == null) {
            this.placementInfo = PlacementInfo.create(List.of(this.input, this.material));
        }

        return this.placementInfo;
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    public static class Serializer implements RecipeSerializer<TransmuteRecipe> {
        private static final MapCodec<TransmuteRecipe> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter(recipe -> recipe.group),
                    CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(recipe -> recipe.category),
                    Ingredient.CODEC.fieldOf("input").forGetter(recipe -> recipe.input),
                    Ingredient.CODEC.fieldOf("material").forGetter(recipe -> recipe.material),
                    TransmuteResult.CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
                )
                .apply(instance, TransmuteRecipe::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, TransmuteRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            recipe -> recipe.group,
            CraftingBookCategory.STREAM_CODEC,
            recipe -> recipe.category,
            Ingredient.CONTENTS_STREAM_CODEC,
            recipe -> recipe.input,
            Ingredient.CONTENTS_STREAM_CODEC,
            recipe -> recipe.material,
            TransmuteResult.STREAM_CODEC,
            transmuteRecipe -> transmuteRecipe.result,
            TransmuteRecipe::new
        );

        @Override
        public MapCodec<TransmuteRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, TransmuteRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
