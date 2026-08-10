package net.minecraft.world.item.crafting;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;

public class FireworkStarFadeRecipe extends CustomRecipe {
    private static final Ingredient STAR_INGREDIENT = Ingredient.of(Items.FIREWORK_STAR);

    public FireworkStarFadeRecipe(CraftingBookCategory category) {
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
                    if (item.getItem() instanceof DyeItem) {
                        flag = true;
                    } else {
                        if (!STAR_INGREDIENT.test(item)) {
                            return false;
                        }

                        if (flag1) {
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
        IntList list = new IntArrayList();
        ItemStack itemStack = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack item = input.getItem(i);
            if (item.getItem() instanceof DyeItem dyeItem) {
                list.add(dyeItem.getDyeColor().getFireworkColor());
            } else if (STAR_INGREDIENT.test(item)) {
                itemStack = item.copyWithCount(1);
            }
        }

        if (itemStack != null && !list.isEmpty()) {
            itemStack.update(DataComponents.FIREWORK_EXPLOSION, FireworkExplosion.DEFAULT, list, FireworkExplosion::withFadeColors);
            return itemStack;
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public RecipeSerializer<FireworkStarFadeRecipe> getSerializer() {
        return RecipeSerializer.FIREWORK_STAR_FADE;
    }
}
