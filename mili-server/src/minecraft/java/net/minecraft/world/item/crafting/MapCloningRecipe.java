package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class MapCloningRecipe extends CustomRecipe {
    public MapCloningRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() < 2) {
            return false;
        } else {
            boolean flag = false;
            boolean flag1 = false;

            for (int i = 0; i < input.size(); i++) {
                ItemStack item = input.getItem(i);
                if (!item.isEmpty()) {
                    if (item.has(DataComponents.MAP_ID)) {
                        if (flag1) {
                            return false;
                        }

                        flag1 = true;
                    } else {
                        if (!item.is(Items.MAP)) {
                            return false;
                        }

                        flag = true;
                    }
                }
            }

            return flag1 && flag;
        }
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        int i = 0;
        ItemStack itemStack = ItemStack.EMPTY;

        for (int i1 = 0; i1 < input.size(); i1++) {
            ItemStack item = input.getItem(i1);
            if (!item.isEmpty()) {
                if (item.has(DataComponents.MAP_ID)) {
                    if (!itemStack.isEmpty()) {
                        return ItemStack.EMPTY;
                    }

                    itemStack = item;
                } else {
                    if (!item.is(Items.MAP)) {
                        return ItemStack.EMPTY;
                    }

                    i++;
                }
            }
        }

        return !itemStack.isEmpty() && i >= 1 ? itemStack.copyWithCount(i + 1) : ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<MapCloningRecipe> getSerializer() {
        return RecipeSerializer.MAP_CLONING;
    }
}
