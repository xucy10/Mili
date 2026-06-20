package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class TippedArrowRecipe extends CustomRecipe {
    public TippedArrowRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() == 3 && input.height() == 3 && input.ingredientCount() == 9) {
            for (int i = 0; i < input.height(); i++) {
                for (int i1 = 0; i1 < input.width(); i1++) {
                    ItemStack item = input.getItem(i1, i);
                    if (item.isEmpty()) {
                        return false;
                    }

                    if (i1 == 1 && i == 1) {
                        if (!item.is(Items.LINGERING_POTION)) {
                            return false;
                        }
                    } else if (!item.is(Items.ARROW)) {
                        return false;
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack item = input.getItem(1, 1);
        if (!item.is(Items.LINGERING_POTION)) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemStack = new ItemStack(Items.TIPPED_ARROW, 8);
            itemStack.set(DataComponents.POTION_CONTENTS, item.get(DataComponents.POTION_CONTENTS));
            return itemStack;
        }
    }

    @Override
    public RecipeSerializer<TippedArrowRecipe> getSerializer() {
        return RecipeSerializer.TIPPED_ARROW;
    }
}
