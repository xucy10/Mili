package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

public class ArmorDyeRecipe extends CustomRecipe {
    public ArmorDyeRecipe(CraftingBookCategory category) {
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
                    if (item.is(ItemTags.DYEABLE)) {
                        if (flag) {
                            return false;
                        }

                        flag = true;
                    } else {
                        if (!(item.getItem() instanceof DyeItem)) {
                            return false;
                        }

                        flag1 = true;
                    }
                }
            }

            return flag1 && flag;
        }
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        List<DyeItem> list = new ArrayList<>();
        ItemStack itemStack = ItemStack.EMPTY;

        for (int i = 0; i < input.size(); i++) {
            ItemStack item = input.getItem(i);
            if (!item.isEmpty()) {
                if (item.is(ItemTags.DYEABLE)) {
                    if (!itemStack.isEmpty()) {
                        return ItemStack.EMPTY;
                    }

                    itemStack = item.copy();
                } else {
                    if (!(item.getItem() instanceof DyeItem dyeItem)) {
                        return ItemStack.EMPTY;
                    }

                    list.add(dyeItem);
                }
            }
        }

        return !itemStack.isEmpty() && !list.isEmpty() ? DyedItemColor.applyDyes(itemStack, list) : ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<ArmorDyeRecipe> getSerializer() {
        return RecipeSerializer.ARMOR_DYE;
    }
}
