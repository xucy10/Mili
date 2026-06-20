package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import org.jspecify.annotations.Nullable;

public class SmithingTrimRecipe implements SmithingRecipe {
    final Ingredient template;
    final Ingredient base;
    final Ingredient addition;
    final Holder<TrimPattern> pattern;
    private @Nullable PlacementInfo placementInfo;
    final boolean copyDataComponents; // Paper - Option to prevent data components copy

    public SmithingTrimRecipe(Ingredient template, Ingredient base, Ingredient addition, Holder<TrimPattern> pattern) {
        // Paper start - Option to prevent data components copy
        this(template, base, addition, pattern, true);
    }
    public SmithingTrimRecipe(Ingredient template, Ingredient base, Ingredient addition, Holder<TrimPattern> pattern, final boolean copyDataComponents) {
        this.copyDataComponents = copyDataComponents;
        // Paper end - Option to prevent data components copy
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.pattern = pattern;
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        return applyTrim(registries, input.base(), input.addition(), this.pattern, this.copyDataComponents); // Paper start - Option to prevent data components copy
    }

    public static ItemStack applyTrim(HolderLookup.Provider registries, ItemStack base, ItemStack addition, Holder<TrimPattern> pattern) {
    // Paper start - Option to prevent data components copy
        return applyTrim(registries, base, addition, pattern, true);
    }
    public static ItemStack applyTrim(HolderLookup.Provider registries, ItemStack base, ItemStack addition, Holder<TrimPattern> pattern, final boolean copyDataComponents) {
    // Paper end - Option to prevent data components copy
        Optional<Holder<TrimMaterial>> fromIngredient = TrimMaterials.getFromIngredient(registries, addition);
        if (fromIngredient.isPresent()) {
            ArmorTrim armorTrim = base.get(DataComponents.TRIM);
            ArmorTrim armorTrim1 = new ArmorTrim(fromIngredient.get(), pattern);
            if (Objects.equals(armorTrim, armorTrim1)) {
                return ItemStack.EMPTY;
            } else {
                ItemStack itemStack = copyDataComponents ? base.copyWithCount(1) : new ItemStack(base.getItem(), 1); // Paper - Option to prevent data components copy
                itemStack.set(DataComponents.TRIM, armorTrim1);
                return itemStack;
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public Optional<Ingredient> templateIngredient() {
        return Optional.of(this.template);
    }

    @Override
    public Ingredient baseIngredient() {
        return this.base;
    }

    @Override
    public Optional<Ingredient> additionIngredient() {
        return Optional.of(this.addition);
    }

    @Override
    public RecipeSerializer<SmithingTrimRecipe> getSerializer() {
        return RecipeSerializer.SMITHING_TRIM;
    }

    @Override
    public PlacementInfo placementInfo() {
        if (this.placementInfo == null) {
            this.placementInfo = PlacementInfo.create(List.of(this.template, this.base, this.addition));
        }

        return this.placementInfo;
    }

    @Override
    public List<RecipeDisplay> display() {
        SlotDisplay slotDisplay = this.base.display();
        SlotDisplay slotDisplay1 = this.addition.display();
        SlotDisplay slotDisplay2 = this.template.display();
        return List.of(
            new SmithingRecipeDisplay(
                slotDisplay2,
                slotDisplay,
                slotDisplay1,
                new SlotDisplay.SmithingTrimDemoSlotDisplay(slotDisplay, slotDisplay1, this.pattern),
                new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)
            )
        );
    }

    // CraftBukkit start
    @Override
    public org.bukkit.inventory.Recipe toBukkitRecipe(org.bukkit.NamespacedKey id) {
        return new org.bukkit.craftbukkit.inventory.CraftSmithingTrimRecipe(id, org.bukkit.craftbukkit.inventory.CraftRecipe.toBukkit(this.template), org.bukkit.craftbukkit.inventory.CraftRecipe.toBukkit(this.base), org.bukkit.craftbukkit.inventory.CraftRecipe.toBukkit(this.addition), org.bukkit.craftbukkit.inventory.trim.CraftTrimPattern.minecraftHolderToBukkit(this.pattern), this.copyDataComponents); // Paper - Option to prevent data components copy
    }
    // CraftBukkit end

    public static class Serializer implements RecipeSerializer<SmithingTrimRecipe> {
        private static final MapCodec<SmithingTrimRecipe> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Ingredient.CODEC.fieldOf("template").forGetter(recipe -> recipe.template),
                    Ingredient.CODEC.fieldOf("base").forGetter(recipe -> recipe.base),
                    Ingredient.CODEC.fieldOf("addition").forGetter(recipe -> recipe.addition),
                    TrimPattern.CODEC.fieldOf("pattern").forGetter(recipe -> recipe.pattern)
                )
                .apply(instance, SmithingTrimRecipe::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SmithingTrimRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC,
            recipe -> recipe.template,
            Ingredient.CONTENTS_STREAM_CODEC,
            recipe -> recipe.base,
            Ingredient.CONTENTS_STREAM_CODEC,
            recipe -> recipe.addition,
            TrimPattern.STREAM_CODEC,
            recipe -> recipe.pattern,
            SmithingTrimRecipe::new
        );

        @Override
        public MapCodec<SmithingTrimRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SmithingTrimRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
