package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public class BannerDuplicateRecipe extends CustomRecipe {
    public BannerDuplicateRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != 2) {
            return false;
        } else {
            DyeColor dyeColor = null;
            boolean flag = false;
            boolean flag1 = false;

            for (int i = 0; i < input.size(); i++) {
                ItemStack item = input.getItem(i);
                if (!item.isEmpty()) {
                    if (!(item.getItem() instanceof BannerItem bannerItem)) {
                        return false;
                    }

                    if (dyeColor == null) {
                        dyeColor = bannerItem.getColor();
                    } else if (dyeColor != bannerItem.getColor()) {
                        return false;
                    }

                    int size = item.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().size();
                    if (size > 6) {
                        return false;
                    }

                    if (size > 0) {
                        if (flag1) {
                            return false;
                        }

                        flag1 = true;
                    } else {
                        if (flag) {
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
        for (int i = 0; i < input.size(); i++) {
            ItemStack item = input.getItem(i);
            if (!item.isEmpty()) {
                int size = item.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().size();
                if (size > 0 && size <= 6) {
                    return item.copyWithCount(1);
                }
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> list = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        for (int i = 0; i < list.size(); i++) {
            ItemStack item = input.getItem(i);
            if (!item.isEmpty()) {
                ItemStack craftingRemainder = item.getItem().getCraftingRemainder();
                if (!craftingRemainder.isEmpty()) {
                    list.set(i, craftingRemainder);
                } else if (!item.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().isEmpty()) {
                    list.set(i, item.copyWithCount(1));
                }
            }
        }

        return list;
    }

    @Override
    public RecipeSerializer<BannerDuplicateRecipe> getSerializer() {
        return RecipeSerializer.BANNER_DUPLICATE;
    }
}
