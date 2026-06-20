package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;

public class DecoratedPotRecipe extends CustomRecipe {
    public DecoratedPotRecipe(CraftingBookCategory category) {
        super(category);
    }

    private static ItemStack back(CraftingInput input) {
        return input.getItem(1, 0);
    }

    private static ItemStack left(CraftingInput input) {
        return input.getItem(0, 1);
    }

    private static ItemStack right(CraftingInput input) {
        return input.getItem(2, 1);
    }

    private static ItemStack front(CraftingInput input) {
        return input.getItem(1, 2);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return input.width() == 3
            && input.height() == 3
            && input.ingredientCount() == 4
            && back(input).is(ItemTags.DECORATED_POT_INGREDIENTS)
            && left(input).is(ItemTags.DECORATED_POT_INGREDIENTS)
            && right(input).is(ItemTags.DECORATED_POT_INGREDIENTS)
            && front(input).is(ItemTags.DECORATED_POT_INGREDIENTS);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        PotDecorations potDecorations = new PotDecorations(back(input).getItem(), left(input).getItem(), right(input).getItem(), front(input).getItem());
        return DecoratedPotBlockEntity.createDecoratedPotItem(potDecorations);
    }

    @Override
    public RecipeSerializer<DecoratedPotRecipe> getSerializer() {
        return RecipeSerializer.DECORATED_POT_RECIPE;
    }
}
